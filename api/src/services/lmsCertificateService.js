/**
 * Certificates — issued when every configured course assessment is graded
 * and the average of the latest quiz and assignment scores reaches 80%.
 */

import crypto from "node:crypto";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { dualWrite, mirrorCertificate } from "./lmsMirror.js";
import { PASS_THRESHOLD } from "./lmsProgressMath.js";
import { publicBaseUrl } from "./lmsMediaService.js";
import { makeCertificatePdf } from "./certificateDesign.js";

function certificateNumber(uid, trackId, issuedAt) {
  const digest = crypto
    .createHash("sha256")
    .update(`${uid}:${trackId}:${issuedAt}`)
    .digest("hex")
    .slice(0, 12)
    .toUpperCase();
  return `NS-${digest}`;
}

async function courseAssessmentResult(uid, trackId) {
  const lessons = await dbAll(
    `SELECT lesson_id, has_quiz, has_assignment
     FROM lessons WHERE track_id = ?`,
    [trackId],
  );
  const assignments = await dbAll(
    `SELECT assignment_id, lesson_id FROM assignments
     WHERE track_id = ? AND lesson_id IS NOT NULL
       AND (assignee_uid IS NULL OR assignee_uid = ?)`,
    [trackId, uid],
  );
  const assignmentsByLesson = new Map();
  for (const assignment of assignments) {
    const list = assignmentsByLesson.get(assignment.lesson_id) || [];
    list.push(assignment);
    assignmentsByLesson.set(assignment.lesson_id, list);
  }

  const scores = [];
  for (const lesson of lessons) {
    if (Boolean(lesson.has_quiz)) {
      const attempt = await dbGet(
        `SELECT score FROM (
           SELECT score, submitted_at FROM quiz_attempts
           WHERE uid = ? AND lesson_id = ?
           UNION ALL
           SELECT score, submitted_at FROM cohort_quiz_attempts
           WHERE uid = ? AND lesson_id = ?
         ) attempts
         ORDER BY submitted_at DESC LIMIT 1`,
        [uid, lesson.lesson_id, uid, lesson.lesson_id],
      );
      if (!attempt) return { score: null, completed: scores.length };
      scores.push(Math.max(0, Math.min(100, Number(attempt.score) || 0)));
    }

    // Older lesson-configured assignments may have no assignments row.
    // Their submission is still included as one assessed course component.
    if (Boolean(lesson.has_assignment) && !assignmentsByLesson.has(lesson.lesson_id)) {
      const submission = await dbGet(
        `SELECT score FROM submissions
         WHERE uid = ? AND lesson_id = ?
           AND status IN ('passed', 'failed') AND score IS NOT NULL
         ORDER BY marked_at DESC LIMIT 1`,
        [uid, lesson.lesson_id],
      );
      if (!submission) return { score: null, completed: scores.length };
      scores.push(Math.max(0, Math.min(100, Number(submission.score) || 0)));
    }
  }

  // Include mentor-created course assignments even when the lesson itself
  // was not authored with has_assignment=true.
  for (const assignment of assignments) {
    const submission = await dbGet(
      `SELECT score FROM submissions
       WHERE uid = ? AND lesson_id = ? AND assignment_id = ?
         AND status IN ('passed', 'failed') AND score IS NOT NULL
       ORDER BY marked_at DESC LIMIT 1`,
      [uid, assignment.lesson_id, assignment.assignment_id],
    );
    if (!submission) return { score: null, completed: scores.length };
    scores.push(Math.max(0, Math.min(100, Number(submission.score) || 0)));
  }

  if (!scores.length) return { score: null, completed: 0 };
  return {
    score: Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length),
    completed: scores.length,
  };
}

export async function maybeIssueCertificate(uid, trackId) {
  const enrollment = await dbGet(
    "SELECT uid FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  if (!enrollment) return null;

  const result = await courseAssessmentResult(uid, trackId);
  if (result.score == null || result.score < PASS_THRESHOLD) return null;

  const existing = await dbGet(
    "SELECT * FROM certificates WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  if (existing) {
    // Older certificates stored course completion progress in this legacy
    // column. Replace it with the verified assessment average before reuse.
    await dbRun(
      "UPDATE certificates SET track_percent = ? WHERE uid = ? AND track_id = ?",
      [result.score, uid, trackId],
    );
    return dbGet(
      "SELECT * FROM certificates WHERE uid = ? AND track_id = ?",
      [uid, trackId],
    );
  }

  const now = Date.now();
  const certUrl = `${publicBaseUrl()}/lms/certificates/verify/${encodeURIComponent(uid)}/${encodeURIComponent(trackId)}`;
  await dualWrite({
    label: `cert:${uid}:${trackId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO certificates (uid, track_id, issued_at, cert_url, track_percent)
         VALUES (?, ?, ?, ?, ?)`,
        [uid, trackId, now, certUrl, result.score],
      );
      await dbRun(
        "UPDATE enrollments SET status = 'certified' WHERE uid = ? AND track_id = ?",
        [uid, trackId],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorCertificate(uid, trackId, {
        issuedAt: now,
        certUrl,
        trackPercent: result.score,
        overallScore: result.score,
      });
    },
  });

  return dbGet("SELECT * FROM certificates WHERE uid = ? AND track_id = ?", [
    uid,
    trackId,
  ]);
}

async function certificateRecord(uid, trackId) {
  return dbGet(
    `SELECT c.*, t.title AS course_title, u.display_name, u.first_name, u.last_name
     FROM certificates c
     JOIN tracks t ON t.track_id = c.track_id
     JOIN users_mirror u ON u.uid = c.uid
     WHERE c.uid = ? AND c.track_id = ?`,
    [uid, trackId],
  );
}

export async function getMyCertificatePdf(uid, trackId) {
  const row = await certificateRecord(uid, trackId);
  const result = row ? await courseAssessmentResult(uid, trackId) : null;
  if (!row || result?.score == null || result.score < PASS_THRESHOLD) {
    const err = new Error("Certificate locked. Complete all course assessments and achieve at least 80% overall.");
    err.status = 403;
    throw err;
  }
  const learnerName =
    [row.first_name, row.last_name].filter(Boolean).join(" ").trim() ||
    row.display_name ||
    "Learner";
  const id = certificateNumber(uid, trackId, Number(row.issued_at));
  const pdf = await makeCertificatePdf({
    learnerName,
    courseTitle: row.course_title,
    score: result.score,
    date: new Date(Number(row.issued_at)).toLocaleDateString("en-GB", {
      day: "2-digit", month: "long", year: "numeric", timeZone: "UTC",
    }),
    certificateId: id,
    verifyUrl: row.cert_url || "",
  });
  return {
    pdf,
    filename: `nelsen-certificate-${id}.pdf`,
  };
}

export async function verifyCertificate(uid, trackId) {
  const row = await certificateRecord(uid, trackId);
  const result = row ? await courseAssessmentResult(uid, trackId) : null;
  if (!row || result?.score == null || result.score < PASS_THRESHOLD) {
    const err = new Error("Certificate not found or no longer eligible");
    err.status = 404;
    throw err;
  }
  return {
    valid: true,
    learnerName:
      [row.first_name, row.last_name].filter(Boolean).join(" ").trim() ||
      row.display_name ||
      "Learner",
    courseTitle: row.course_title,
    issuedAt: Number(row.issued_at),
    overallScore: result.score,
    certificateId: certificateNumber(uid, trackId, Number(row.issued_at)),
  };
}

export async function listMyCertificates(uid) {
  const rows = await dbAll(
    `SELECT c.*, t.title AS course_title, u.display_name, u.first_name, u.last_name
     FROM certificates c
     JOIN tracks t ON t.track_id = c.track_id
     JOIN users_mirror u ON u.uid = c.uid
     WHERE c.uid = ?
     ORDER BY c.issued_at DESC`,
    [uid],
  );
  const certificates = [];
  for (const row of rows) {
    const result = await courseAssessmentResult(uid, row.track_id);
    // Do not display legacy progress-based certificates as assessment scores.
    if (result.score == null || result.score < PASS_THRESHOLD) continue;
    if (Number(row.track_percent) !== result.score) {
      await dbRun(
        "UPDATE certificates SET track_percent = ? WHERE uid = ? AND track_id = ?",
        [result.score, uid, row.track_id],
      );
    }
    certificates.push({
      trackId: row.track_id,
      learnerName:
        [row.first_name, row.last_name].filter(Boolean).join(" ").trim() ||
        row.display_name ||
        "Learner",
      courseTitle: row.course_title,
      issuedAt: Number(row.issued_at),
      verifyUrl: row.cert_url,
      pdfUrl: null,
      certificateId: certificateNumber(uid, row.track_id, Number(row.issued_at)),
      trackPercent: result.score,
      overallScore: result.score,
    });
  }
  return {
    source: getPrimaryEngine(),
    data: { certificates },
  };
}
