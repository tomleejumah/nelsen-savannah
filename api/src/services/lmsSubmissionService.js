/**
 * M4 — assignment submissions + mentor marking.
 */

import crypto from "crypto";
import {
  dbAll,
  dbGet,
  dbRun,
  getPrimaryEngine,
} from "../db/lmsDb.js";
import { dualWrite, mirrorSubmission } from "./lmsMirror.js";
import {
  patchLessonProgress,
} from "./lmsEnrollmentService.js";
import { upsertUserFromToken } from "./lmsMeService.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { PASS_THRESHOLD } from "./lmsProgressMath.js";
import { maybeIssueCertificate } from "./lmsCertificateService.js";

function newSubmissionId() {
  return `sub_${crypto.randomBytes(8).toString("hex")}`;
}

function mapSubmission(row, extras = {}) {
  return {
    id: row.submission_id,
    lessonId: row.lesson_id,
    trackId: row.track_id,
    moduleId: row.module_id,
    uid: row.uid,
    status: row.status,
    text: row.body || "",
    fileUrl: null,
    linkUrl: null,
    score: row.score == null ? null : Number(row.score),
    feedback: row.feedback || null,
    mentorId: row.mentor_id || null,
    assignmentId: row.assignment_id || null,
    submittedAt: Number(row.submitted_at),
    markedAt: row.marked_at ? Number(row.marked_at) : null,
    ...extras,
  };
}

export async function createSubmission(profile, body = {}) {
  await upsertUserFromToken(profile);
  let lessonId = body.lessonId || null;
  let assignmentId = body.assignmentId || null;
  let assignment = null;

  if (assignmentId) {
    const { getAssignmentForSubmit } = await import("./lmsAssignmentService.js");
    assignment = await getAssignmentForSubmit(assignmentId, profile.uid);
    if (!assignment) {
      const err = new Error("Assignment not found or not assigned to you");
      err.status = 404;
      throw err;
    }
    lessonId = lessonId || assignment.lesson_id || null;
    if (!lessonId && assignment.track_id) {
      const first = await dbGet(
        `SELECT lesson_id FROM lessons WHERE track_id = ?
         ORDER BY module_id ASC, lesson_id ASC LIMIT 1`,
        [assignment.track_id],
      );
      lessonId = first?.lesson_id || null;
    }
  }

  if (!lessonId) {
    const err = new Error(
      "lessonId required — attach a lesson when assigning, or use a track that has lessons",
    );
    err.status = 400;
    throw err;
  }

  const lesson = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [
    lessonId,
  ]);
  if (!lesson) {
    const err = new Error("Lesson not found");
    err.status = 404;
    throw err;
  }

  // Mentor-created assignments can target any lesson; lesson.has_assignment is for catalog lessons.
  if (!assignmentId && !lesson.has_assignment) {
    const err = new Error("Lesson has no assignment");
    err.status = 400;
    throw err;
  }

  const enroll = await dbGet(
    "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
    [profile.uid, lesson.track_id],
  );
  if (!enroll) {
    if (assignmentId) {
      const { enrollUser } = await import("./lmsEnrollmentService.js");
      await enrollUser(profile, {
        trackId: lesson.track_id,
        platform: body.platform || "web",
      });
    } else {
      const err = new Error("Not enrolled");
      err.status = 403;
      throw err;
    }
  }

  const now = Date.now();
  const submissionId = newSubmissionId();
  const text = body.text || body.body || "";
  const mediaUrls = JSON.stringify(
    [body.fileUrl, body.linkUrl].filter(Boolean),
  );

  const row = await dualWrite({
    label: `submission:${submissionId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO submissions (
          submission_id, uid, lesson_id, track_id, module_id, body,
          media_urls_json, status, score, feedback, mentor_id,
          submitted_at, marked_at, assignment_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'submitted', NULL, NULL, NULL, ?, NULL, ?)`,
        [
          submissionId,
          profile.uid,
          lessonId,
          lesson.track_id,
          lesson.module_id,
          text,
          mediaUrls,
          now,
          assignmentId,
        ],
      );
      return dbGet("SELECT * FROM submissions WHERE submission_id = ?", [
        submissionId,
      ]);
    },
    mirrorFn: async (r) => {
      await mirrorSubmission(submissionId, mapSubmission(r));
    },
  });

  // mark lesson opened so assignment weight can apply later
  await patchLessonProgress(profile, lessonId, {
    opened: true,
    lastPlatform: body.platform || "web",
  });

  return {
    source: getPrimaryEngine(),
    data: { submission: mapSubmission(row) },
  };
}

export async function listMySubmissions(uid, { trackId, status } = {}) {
  let sql = "SELECT * FROM submissions WHERE uid = ?";
  const params = [uid];
  if (trackId) {
    sql += " AND track_id = ?";
    params.push(trackId);
  }
  if (status) {
    sql += " AND status = ?";
    params.push(status);
  }
  sql += " ORDER BY submitted_at DESC";
  const rows = await dbAll(sql, params);
  return {
    source: getPrimaryEngine(),
    data: { submissions: rows.map((r) => mapSubmission(r)) },
  };
}

export async function listSubmissionQueue(mentorUid) {
  const role = await loadUserRole(mentorUid);
  if (role !== "Mentor" && role !== "SuperAdmin" && role !== "SchoolAdmin" && role !== "Admin") {
    const err = new Error("Mentor or Admin required");
    err.status = 403;
    throw err;
  }
  const rows = await dbAll(
    `SELECT s.*, u.display_name, u.photo_url, l.title AS lesson_title
     FROM submissions s
     JOIN users_mirror u ON u.uid = s.uid
     JOIN lessons l ON l.lesson_id = s.lesson_id
     WHERE s.status = 'submitted'
     ORDER BY s.submitted_at ASC`,
  );
  return {
    source: getPrimaryEngine(),
    data: {
      queue: rows.map((r) => ({
        id: r.submission_id,
        lessonId: r.lesson_id,
        lessonTitle: r.lesson_title,
        trackId: r.track_id,
        menteeId: r.uid,
        menteeName: r.display_name || "",
        menteeAvatar: r.photo_url || "",
        text: r.body || "",
        fileUrl: null,
        linkUrl: null,
        submittedAt: Number(r.submitted_at),
      })),
    },
  };
}

export async function markSubmission(mentorProfile, submissionId, body = {}) {
  const role = await loadUserRole(mentorProfile.uid);
  if (role !== "Mentor" && role !== "SuperAdmin" && role !== "SchoolAdmin" && role !== "Admin") {
    const err = new Error("Mentor or Admin required");
    err.status = 403;
    throw err;
  }
  const row = await dbGet("SELECT * FROM submissions WHERE submission_id = ?", [
    submissionId,
  ]);
  if (!row) {
    const err = new Error("Submission not found");
    err.status = 404;
    throw err;
  }

  const score = Math.max(0, Math.min(100, Number(body.score) || 0));
  const passed =
    body.passed !== undefined ? Boolean(body.passed) : score >= PASS_THRESHOLD;
  const status = passed ? "passed" : "failed";
  const feedback = body.feedback || "";
  const assignmentPct = passed ? 100 : Math.round(score);
  const now = Date.now();

  await dualWrite({
    label: `mark:${submissionId}`,
    writeFn: async () => {
      await dbRun(
        `UPDATE submissions SET
          status = ?, score = ?, feedback = ?, mentor_id = ?, marked_at = ?
         WHERE submission_id = ?`,
        [status, score, feedback, mentorProfile.uid, now, submissionId],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorSubmission(submissionId, {
        status,
        score,
        feedback,
        mentorId: mentorProfile.uid,
        markedAt: now,
        assignmentPct,
      });
    },
  });

  const progressResult = await patchLessonProgress(
    { uid: row.uid, email: "", displayName: "" },
    row.lesson_id,
    {
      opened: true,
      assignmentPct,
      lastPlatform: "android",
    },
  );

  await maybeIssueCertificate(row.uid, row.track_id);

  return {
    source: getPrimaryEngine(),
    data: {
      submission: {
        id: submissionId,
        status,
        score,
        feedback,
        assignmentPct,
        lessonPercent: progressResult.data?.progress?.lessonPercent ?? null,
        trackPercent: progressResult.data?.progress?.trackPercent ?? null,
      },
    },
  };
}
