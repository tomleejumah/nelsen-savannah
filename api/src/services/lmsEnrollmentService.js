/**
 * Enrollments + progress — primary DB first, RTDB mirror.
 */

import {
  checkPrimaryHealth,
  dbAll,
  dbGet,
  dbRun,
  getPrimaryEngine,
} from "../db/lmsDb.js";
import {
  dualWrite,
  mirrorEnrollment,
  mirrorProgress,
} from "./lmsMirror.js";
import { upsertUserFromToken } from "./lmsMeService.js";
import {
  aggregatePercent,
  lessonPercentComplete,
  lessonStatusFromPercent,
  PASS_THRESHOLD,
} from "./lmsProgressMath.js";
import admin from "../config/firebase.js";

function mapEnrollment(row, extras = {}) {
  return {
    uid: row.uid,
    trackId: row.track_id || row.trackId,
    status: row.status,
    trackPercent: Number(row.track_percent ?? row.trackPercent ?? 0),
    modulesCompleted: Number(row.modules_completed ?? row.modulesCompleted ?? 0),
    modulesTotal: Number(row.modules_total ?? row.modulesTotal ?? 0),
    lessonsCompleted: Number(row.lessons_completed ?? row.lessonsCompleted ?? 0),
    lessonsTotal: Number(row.lessons_total ?? row.lessonsTotal ?? 0),
    mentorId: row.mentor_id ?? row.mentorId ?? null,
    enrolledAt: Number(row.enrolled_at ?? row.enrolledAt ?? 0),
    lastActiveAt: Number(row.last_active_at ?? row.lastActiveAt ?? 0),
    platform: row.platform || "both",
    role: row.role || null,
    ...extras,
  };
}

function mapProgress(row, extras = {}) {
  return {
    lessonId: row.lesson_id || row.lessonId,
    moduleId: row.module_id || row.moduleId,
    trackId: row.track_id || row.trackId,
    opened: Boolean(row.opened),
    contentPct: Number(row.content_pct ?? row.contentPct ?? 0),
    quizPct: Number(row.quiz_pct ?? row.quizPct ?? 0),
    assignmentPct: Number(row.assignment_pct ?? row.assignmentPct ?? 0),
    lessonPercent: Number(row.lesson_percent ?? row.lessonPercent ?? 0),
    status: row.status || "available",
    lastPlatform: row.last_platform || row.lastPlatform || null,
    updatedAt: Number(row.updated_at ?? row.updatedAt ?? 0),
    ...extras,
  };
}

async function trackTotals(trackId) {
  const modulesTotal = Number(
    (await dbGet("SELECT COUNT(*) AS c FROM modules WHERE track_id = ?", [trackId]))
      ?.c ?? 0,
  );
  const lessonsTotal = Number(
    (await dbGet("SELECT COUNT(*) AS c FROM lessons WHERE track_id = ?", [trackId]))
      ?.c ?? 0,
  );
  return { modulesTotal, lessonsTotal };
}

export async function recomputeTrackProgress(uid, trackId) {
  const lessons = await dbAll(
    "SELECT lesson_id, module_id FROM lessons WHERE track_id = ? ORDER BY sort_order ASC",
    [trackId],
  );
  const progressRows = await dbAll(
    "SELECT * FROM progress WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  const byLesson = new Map(progressRows.map((p) => [p.lesson_id, p]));

  const modules = await dbAll(
    "SELECT module_id FROM modules WHERE track_id = ? ORDER BY sort_order ASC",
    [trackId],
  );

  const modulePercents = [];
  let lessonsCompleted = 0;

  for (const mod of modules) {
    const modLessons = lessons.filter((l) => l.module_id === mod.module_id);
    const percents = modLessons.map((l) => {
      const p = byLesson.get(l.lesson_id);
      const pct = Number(p?.lesson_percent ?? 0);
      if (pct >= PASS_THRESHOLD) lessonsCompleted += 1;
      return pct;
    });
    modulePercents.push(aggregatePercent(percents));
  }

  const trackPercent = aggregatePercent(modulePercents);
  const modulesCompleted = modulePercents.filter((p) => p >= PASS_THRESHOLD).length;
  const { modulesTotal, lessonsTotal } = await trackTotals(trackId);
  const status =
    trackPercent >= PASS_THRESHOLD ? "completed" : "in_progress";
  const now = Date.now();

  await dbRun(
    `UPDATE enrollments SET
      track_percent = ?, modules_completed = ?, modules_total = ?,
      lessons_completed = ?, lessons_total = ?, status = ?,
      last_active_at = ?
     WHERE uid = ? AND track_id = ?`,
    [
      trackPercent,
      modulesCompleted,
      modulesTotal,
      lessonsCompleted,
      lessonsTotal,
      status,
      now,
      uid,
      trackId,
    ],
  );

  const enrollment = await dbGet(
    "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );

  await mirrorEnrollment(uid, trackId, {
    ...mapEnrollment(enrollment),
    updatedAt: now,
  });

  return {
    trackPercent,
    modulePercents,
    enrollment: mapEnrollment(enrollment),
  };
}

async function nextLessonId(uid, trackId) {
  const lessons = await dbAll(
    `SELECT l.lesson_id FROM lessons l
     JOIN modules m ON m.module_id = l.module_id
     WHERE l.track_id = ?
     ORDER BY m.sort_order ASC, l.sort_order ASC`,
    [trackId],
  );
  const progressRows = await dbAll(
    "SELECT lesson_id, lesson_percent FROM progress WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  const done = new Set(
    progressRows
      .filter((p) => Number(p.lesson_percent) >= PASS_THRESHOLD)
      .map((p) => p.lesson_id),
  );
  for (const l of lessons) {
    if (!done.has(l.lesson_id)) return l.lesson_id;
  }
  return lessons[0]?.lesson_id || null;
}

export async function enrollUser(profile, { trackId, platform = "web" }) {
  if (!trackId) {
    const err = new Error("trackId required");
    err.status = 400;
    throw err;
  }

  await upsertUserFromToken(profile);
  const track = await dbGet(
    "SELECT * FROM tracks WHERE track_id = ? AND published = 1",
    [trackId],
  );
  if (!track) {
    const err = new Error("Track not found");
    err.status = 404;
    throw err;
  }

  const existing = await dbGet(
    "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
    [profile.uid, trackId],
  );
  if (existing) {
    return {
      source: getPrimaryEngine(),
      data: { enrollment: mapEnrollment(existing) },
    };
  }

  const schoolId =
    track.school_id ||
    track.schoolId ||
    "nelsen-digital";

  try {
    const { attachOnEnroll } = await import("./lmsMembershipService.js");
    await attachOnEnroll(profile.uid, profile.email || "", schoolId);
  } catch (err) {
    console.warn("[enroll] membership attach:", err.message);
  }

  const now = Date.now();
  const { modulesTotal, lessonsTotal } = await trackTotals(trackId);

  const enrollment = await dualWrite({
    label: `enroll:${profile.uid}:${trackId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO enrollments (
          uid, track_id, role, status, enrolled_at, last_active_at,
          track_percent, modules_completed, modules_total,
          lessons_completed, lessons_total, mentor_id, platform, school_id
        ) VALUES (?, ?, ?, 'in_progress', ?, ?, 0, 0, ?, 0, ?, NULL, ?, ?)`,
        [
          profile.uid,
          trackId,
          null,
          now,
          now,
          modulesTotal,
          lessonsTotal,
          platform === "android" || platform === "web" ? platform : "web",
          schoolId,
        ],
      );
      return dbGet(
        "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
        [profile.uid, trackId],
      );
    },
    mirrorFn: async (row) => {
      await mirrorEnrollment(profile.uid, trackId, mapEnrollment(row));
    },
  });

  return {
    source: getPrimaryEngine(),
    data: { enrollment: mapEnrollment(enrollment) },
  };
}

export async function listMyEnrollments(uid) {
  const health = await checkPrimaryHealth();
  if (health.ok) {
    try {
      const rows = await dbAll(
        `SELECT e.*, t.title AS course_title, t.course_image_url
         FROM enrollments e
         JOIN tracks t ON t.track_id = e.track_id
         WHERE e.uid = ?
         ORDER BY e.last_active_at DESC`,
        [uid],
      );
      const enrollments = [];
      for (const row of rows) {
        const next = await nextLessonId(uid, row.track_id);
        enrollments.push(
          mapEnrollment(row, {
            courseTitle: row.course_title,
            courseImageUrl: row.course_image_url || "",
            nextLessonId: next,
          }),
        );
      }
      return { source: getPrimaryEngine(), data: { enrollments } };
    } catch (err) {
      console.error("[lms-enrollments] primary failed:", err.message);
    }
  }

  const snap = await admin
    .database()
    .ref(`lms/enrollments/${uid}`)
    .once("value");
  const val = snap.val() || {};
  const enrollments = Object.entries(val).map(([trackId, row]) =>
    mapEnrollment({ track_id: trackId, uid, ...row }),
  );
  return { source: "rtdb", data: { enrollments } };
}

export async function unenrollUser(uid, trackId) {
  const existing = await dbGet(
    "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  if (!existing) {
    const err = new Error("Enrollment not found");
    err.status = 404;
    throw err;
  }

  await dualWrite({
    label: `unenroll:${uid}:${trackId}`,
    writeFn: async () => {
      await dbRun("DELETE FROM enrollments WHERE uid = ? AND track_id = ?", [
        uid,
        trackId,
      ]);
      // keep progress rows for resume if they re-enroll
      return true;
    },
    mirrorFn: async () => {
      await admin.database().ref(`lms/enrollments/${uid}/${trackId}`).remove();
    },
  });

  return {
    source: getPrimaryEngine(),
    data: { unenrolled: true, trackId },
  };
}

export async function patchLessonProgress(profile, lessonId, body = {}) {
  await upsertUserFromToken(profile);
  const lesson = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [
    lessonId,
  ]);
  if (!lesson) {
    const err = new Error("Lesson not found");
    err.status = 404;
    throw err;
  }

  const enroll = await dbGet(
    "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
    [profile.uid, lesson.track_id],
  );
  if (!enroll) {
    const err = new Error("Not enrolled in this track");
    err.status = 403;
    throw err;
  }

  const prev = await dbGet(
    "SELECT * FROM progress WHERE uid = ? AND lesson_id = ?",
    [profile.uid, lessonId],
  );

  const opened =
    body.opened !== undefined ? Boolean(body.opened) : Boolean(prev?.opened);
  const contentPct =
    body.contentPct !== undefined
      ? clampPct(body.contentPct)
      : Number(prev?.content_pct ?? 0);
  const quizPct =
    body.quizPct !== undefined
      ? clampPct(body.quizPct)
      : Number(prev?.quiz_pct ?? 0);
  const assignmentPct =
    body.assignmentPct !== undefined
      ? clampPct(body.assignmentPct)
      : Number(prev?.assignment_pct ?? 0);
  const lastPlatform =
    body.lastPlatform === "android" || body.lastPlatform === "web"
      ? body.lastPlatform
      : prev?.last_platform || "web";

  const hasQuiz = Boolean(lesson.has_quiz);
  const hasAssignment = Boolean(lesson.has_assignment);
  const lessonPercent = lessonPercentComplete({
    opened,
    contentPct,
    quizPct,
    assignmentPct,
    hasQuiz,
    hasAssignment,
  });
  const status = lessonStatusFromPercent(lessonPercent, { opened });
  const now = Date.now();

  await dualWrite({
    label: `progress:${profile.uid}:${lessonId}`,
    writeFn: async () => {
      if (prev) {
        await dbRun(
          `UPDATE progress SET
            opened = ?, content_pct = ?, quiz_pct = ?, assignment_pct = ?,
            lesson_percent = ?, status = ?, last_platform = ?, updated_at = ?
           WHERE uid = ? AND lesson_id = ?`,
          [
            opened ? 1 : 0,
            contentPct,
            quizPct,
            assignmentPct,
            lessonPercent,
            status,
            lastPlatform,
            now,
            profile.uid,
            lessonId,
          ],
        );
      } else {
        await dbRun(
          `INSERT INTO progress (
            uid, lesson_id, module_id, track_id, opened, content_pct,
            quiz_pct, assignment_pct, lesson_percent, status, last_platform, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            profile.uid,
            lessonId,
            lesson.module_id,
            lesson.track_id,
            opened ? 1 : 0,
            contentPct,
            quizPct,
            assignmentPct,
            lessonPercent,
            status,
            lastPlatform,
            now,
          ],
        );
      }
      return true;
    },
    mirrorFn: async () => {
      await mirrorProgress(profile.uid, lessonId, {
        lessonId,
        moduleId: lesson.module_id,
        trackId: lesson.track_id,
        opened,
        contentPct,
        quizPct,
        assignmentPct,
        lessonPercent,
        status,
        lastPlatform,
        updatedAt: now,
      });
    },
  });

  // Merge platform on enrollment
  const platform =
    enroll.platform === "both"
      ? "both"
      : enroll.platform && enroll.platform !== lastPlatform
        ? "both"
        : lastPlatform;
  if (platform !== enroll.platform) {
    await dbRun(
      "UPDATE enrollments SET platform = ? WHERE uid = ? AND track_id = ?",
      [platform, profile.uid, lesson.track_id],
    );
  }

  const { trackPercent, modulePercents, enrollment } =
    await recomputeTrackProgress(profile.uid, lesson.track_id);

  const moduleIndex = (
    await dbAll(
      "SELECT module_id FROM modules WHERE track_id = ? ORDER BY sort_order ASC",
      [lesson.track_id],
    )
  ).findIndex((m) => m.module_id === lesson.module_id);
  const modulePercent =
    moduleIndex >= 0 ? modulePercents[moduleIndex] ?? 0 : 0;

  return {
    source: getPrimaryEngine(),
    data: {
      progress: mapProgress(
        {
          lesson_id: lessonId,
          module_id: lesson.module_id,
          track_id: lesson.track_id,
          opened: opened ? 1 : 0,
          content_pct: contentPct,
          quiz_pct: quizPct,
          assignment_pct: assignmentPct,
          lesson_percent: lessonPercent,
          status,
          last_platform: lastPlatform,
          updated_at: now,
        },
        { trackPercent, modulePercent },
      ),
      enrollment,
    },
  };
}

function clampPct(n) {
  return Math.max(0, Math.min(100, Number(n) || 0));
}

export async function getMyProgress(uid, { trackId } = {}) {
  const health = await checkPrimaryHealth();
  if (health.ok) {
    try {
      const progressSql = trackId
        ? "SELECT * FROM progress WHERE uid = ? AND track_id = ?"
        : "SELECT * FROM progress WHERE uid = ?";
      const progressParams = trackId ? [uid, trackId] : [uid];
      const progressRows = await dbAll(progressSql, progressParams);

      const enrollSql = trackId
        ? "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?"
        : "SELECT * FROM enrollments WHERE uid = ?";
      const enrollParams = trackId ? [uid, trackId] : [uid];
      const enrollRows = await dbAll(enrollSql, enrollParams);

      const byLessonId = {};
      for (const row of progressRows) {
        byLessonId[row.lesson_id] = {
          lessonPercent: Number(row.lesson_percent),
          status: row.status,
          contentPct: Number(row.content_pct),
          quizPct: Number(row.quiz_pct),
          assignmentPct: Number(row.assignment_pct),
          opened: Boolean(row.opened),
          moduleId: row.module_id,
          trackId: row.track_id,
          lastPlatform: row.last_platform,
          updatedAt: Number(row.updated_at),
        };
      }

      const byTrackId = {};
      for (const row of enrollRows) {
        byTrackId[row.track_id] = {
          trackPercent: Number(row.track_percent),
          status: row.status,
          lessonsCompleted: Number(row.lessons_completed),
          lessonsTotal: Number(row.lessons_total),
          nextLessonId: await nextLessonId(uid, row.track_id),
          platform: row.platform,
          lastActiveAt: Number(row.last_active_at),
        };
      }

      return {
        source: getPrimaryEngine(),
        data: { byLessonId, byTrackId },
      };
    } catch (err) {
      console.error("[lms-progress] primary failed:", err.message);
    }
  }

  const [pSnap, eSnap] = await Promise.all([
    admin.database().ref(`lms/progress/${uid}`).once("value"),
    admin.database().ref(`lms/enrollments/${uid}`).once("value"),
  ]);
  const byLessonId = {};
  const pVal = pSnap.val() || {};
  for (const [lessonId, row] of Object.entries(pVal)) {
    if (trackId && row.trackId !== trackId) continue;
    byLessonId[lessonId] = {
      lessonPercent: row.lessonPercent || 0,
      status: row.status,
      contentPct: row.contentPct || 0,
      updatedAt: row.updatedAt || 0,
    };
  }
  const byTrackId = {};
  const eVal = eSnap.val() || {};
  for (const [tid, row] of Object.entries(eVal)) {
    if (trackId && tid !== trackId) continue;
    byTrackId[tid] = {
      trackPercent: row.trackPercent || 0,
      status: row.status,
    };
  }
  return { source: "rtdb", data: { byLessonId, byTrackId } };
}
