/**
 * M5 — admin CMS, likes, quiz submit, stats.
 */

import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import {
  dualWrite,
  mirrorLesson,
  mirrorModule,
  mirrorTrack,
  mirrorRole,
} from "./lmsMirror.js";
import { setUserRole } from "./lmsMeService.js";
import { normalizeRole, isSuperAdmin } from "../constants/lmsRoles.js";
import { patchLessonProgress } from "./lmsEnrollmentService.js";
import { maybeIssueCertificate } from "./lmsCertificateService.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { getActorSchoolId } from "./lmsSchoolService.js";

async function assertCanEditTrack(actorUid, trackId) {
  const role = await loadUserRole(actorUid);
  if (isSuperAdmin(role)) return;
  const track = await dbGet("SELECT school_id FROM tracks WHERE track_id = ?", [
    trackId,
  ]);
  if (!track) {
    const err = new Error("Track not found");
    err.status = 404;
    throw err;
  }
  const schoolId = await getActorSchoolId(actorUid);
  if ((track.school_id || "nelsen-digital") !== schoolId) {
    const err = new Error("Cannot edit another school’s track");
    err.status = 403;
    throw err;
  }
}

export async function adminCreateTrack(actorUid, body = {}) {
  const trackId = body.trackId;
  if (!trackId || !body.title) {
    const err = new Error("trackId and title required");
    err.status = 400;
    throw err;
  }
  const role = await loadUserRole(actorUid);
  const actorSchool = await getActorSchoolId(actorUid);
  let schoolId = body.schoolId || actorSchool;
  if (!isSuperAdmin(role)) schoolId = actorSchool;
  const now = Date.now();
  const audienceJson = JSON.stringify(body.audience || ["Mentee"]);
  await dualWrite({
    label: `admin-track:${trackId}`,
    writeFn: async () => {
      const existing = await dbGet("SELECT track_id FROM tracks WHERE track_id = ?", [
        trackId,
      ]);
      if (existing) {
        const err = new Error("Track already exists");
        err.status = 409;
        throw err;
      }
      await dbRun(
        `INSERT INTO tracks (
          track_id, program_slug, title, does, course_image_url,
          tutor_id, tutor_name, tutor_avatar_url, duration, audience_json,
          sort_order, published, created_at, updated_at, school_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          trackId,
          body.programSlug || "",
          body.title,
          body.blurb || body.does || "",
          body.imageUrl || "",
          body.tutorId || "nelsen-org",
          body.tutorName || "Nelsen Savannah",
          body.tutorAvatarUrl || "",
          body.duration || "1",
          audienceJson,
          body.order || 0,
          body.published === false ? 0 : 1,
          now,
          now,
          schoolId,
        ],
      );
      return dbGet("SELECT * FROM tracks WHERE track_id = ?", [trackId]);
    },
    mirrorFn: async (row) => {
      await mirrorTrack(trackId, {
        trackId,
        title: row.title,
        does: row.does,
        programSlug: row.program_slug,
        published: Boolean(row.published),
        schoolId,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: {
      track: {
        trackId,
        courseId: trackId,
        courseTitle: body.title,
        does: body.blurb || "",
        programSlug: body.programSlug || "",
        audience: body.audience || ["Mentee"],
        schoolId,
      },
    },
  };
}

export async function adminUpdateTrack(actorUid, trackId, body = {}) {
  await assertCanEditTrack(actorUid, trackId);
  const row = await dbGet("SELECT * FROM tracks WHERE track_id = ?", [trackId]);
  if (!row) {
    const err = new Error("Track not found");
    err.status = 404;
    throw err;
  }
  const now = Date.now();
  const title = body.title ?? row.title;
  const does = body.blurb ?? body.does ?? row.does;
  const programSlug = body.programSlug ?? row.program_slug;
  const imageUrl = body.imageUrl ?? row.course_image_url;
  const published =
    body.published === undefined ? row.published : body.published ? 1 : 0;
  const audienceJson = body.audience
    ? JSON.stringify(body.audience)
    : row.audience_json;

  await dualWrite({
    label: `admin-track-upd:${trackId}`,
    writeFn: async () => {
      await dbRun(
        `UPDATE tracks SET title = ?, does = ?, program_slug = ?,
         course_image_url = ?, audience_json = ?, published = ?, updated_at = ?
         WHERE track_id = ?`,
        [title, does, programSlug, imageUrl, audienceJson, published, now, trackId],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorTrack(trackId, {
        title,
        does,
        programSlug,
        courseImageUrl: imageUrl,
        published: Boolean(published),
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: { track: { trackId, courseTitle: title, does, published: Boolean(published) } },
  };
}

export async function adminCreateModule(actorUid, body = {}) {
  const { moduleId, trackId, title } = body;
  if (!moduleId || !trackId || !title) {
    const err = new Error("moduleId, trackId, title required");
    err.status = 400;
    throw err;
  }
  await assertCanEditTrack(actorUid, trackId);
  const now = Date.now();
  await dualWrite({
    label: `admin-mod:${moduleId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO modules (
          module_id, track_id, title, does, estimated_minutes,
          sort_order, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          moduleId,
          trackId,
          title,
          body.does || "",
          body.estimatedMinutes || 0,
          body.order || 0,
          now,
          now,
        ],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorModule(moduleId, {
        moduleId,
        trackId,
        title,
        does: body.does || "",
        estimatedMinutes: body.estimatedMinutes || 0,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: { module: { moduleId, trackId, title, does: body.does || "" } },
  };
}

export async function adminCreateLesson(actorUid, body = {}) {
  const { lessonId, moduleId, trackId, title, type } = body;
  if (!lessonId || !moduleId || !trackId || !title) {
    const err = new Error("lessonId, moduleId, trackId, title required");
    err.status = 400;
    throw err;
  }
  await assertCanEditTrack(actorUid, trackId);
  const now = Date.now();
  const hasQuiz = body.hasQuiz || type === "quiz" ? 1 : 0;
  const hasAssignment = body.hasAssignment || type === "assignment" ? 1 : 0;
  await dualWrite({
    label: `admin-lesson:${lessonId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO lessons (
          lesson_id, module_id, track_id, title, does, type,
          estimated_minutes, has_quiz, has_assignment, content_url,
          media_id, sort_order, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          lessonId,
          moduleId,
          trackId,
          title,
          body.does || "",
          type || "read",
          body.estimatedMinutes || 0,
          hasQuiz,
          hasAssignment,
          body.contentUrl || null,
          body.mediaId || null,
          body.order || 0,
          now,
          now,
        ],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorLesson(lessonId, {
        lessonId,
        moduleId,
        trackId,
        title,
        type: type || "read",
        hasQuiz: Boolean(hasQuiz),
        hasAssignment: Boolean(hasAssignment),
        mediaId: body.mediaId || null,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: { lesson: { lessonId, moduleId, trackId, title, type: type || "read" } },
  };
}

export async function adminSetRole(actorUid, targetUid, userRole) {
  const role = normalizeRole(userRole);
  await setUserRole(targetUid, role);
  await mirrorRole(targetUid, role);
  return {
    source: getPrimaryEngine(),
    data: { uid: targetUid, userRole: role },
  };
}

/**
 * Resolve Firebase user by email, then set LMS role.
 * Target must already have signed in with Google at least once (Auth user exists).
 */
export async function adminSetRoleByEmail(actorUid, email, userRole) {
  const actorRole = await loadUserRole(actorUid);
  if (!isSuperAdmin(actorRole)) {
    const err = new Error("SuperAdmin required");
    err.status = 403;
    throw err;
  }
  const trimmed = String(email || "")
    .trim()
    .toLowerCase();
  if (!trimmed || !trimmed.includes("@")) {
    const err = new Error("Valid email required");
    err.status = 400;
    throw err;
  }
  const role = normalizeRole(userRole || "SuperAdmin");
  const { default: admin } = await import("../config/firebase.js");
  let user;
  try {
    user = await admin.auth().getUserByEmail(trimmed);
  } catch (err) {
    if (err?.code === "auth/user-not-found") {
      const missing = new Error(
        "No Firebase user with that email — they must sign in once first",
      );
      missing.status = 404;
      throw missing;
    }
    throw err;
  }
  await setUserRole(user.uid, role);
  await mirrorRole(user.uid, role);
  return {
    source: getPrimaryEngine(),
    data: {
      uid: user.uid,
      email: user.email || trimmed,
      userRole: role,
    },
  };
}

export async function adminStats(actorUid) {
  const role = await loadUserRole(actorUid);
  const schoolId = await getActorSchoolId(actorUid);
  const schoolFilter = isSuperAdmin(role)
    ? ""
    : " AND COALESCE(u.school_id, 'nelsen-digital') = ?";
  const params = isSuperAdmin(role) ? [] : [schoolId];

  const enrollmentsTotal = Number(
    (
      await dbGet(
        `SELECT COUNT(*) AS c FROM enrollments e
         JOIN users_mirror u ON u.uid = e.uid
         WHERE 1=1${schoolFilter}`,
        params,
      )
    )?.c || 0,
  );
  const avgRow = await dbGet(
    `SELECT AVG(e.track_percent) AS a FROM enrollments e
     JOIN users_mirror u ON u.uid = e.uid
     WHERE 1=1${schoolFilter}`,
    params,
  );
  const avgTrackPercent = Math.round(Number(avgRow?.a || 0));
  const since = Date.now() - 30 * 24 * 60 * 60 * 1000;
  const completions30d = Number(
    (
      await dbGet(
        `SELECT COUNT(*) AS c FROM enrollments e
         JOIN users_mirror u ON u.uid = e.uid
         WHERE e.track_percent >= 80 AND e.last_active_at >= ?${schoolFilter}`,
        [since, ...params],
      )
    )?.c || 0,
  );
  const byTrack = await dbAll(
    `SELECT e.track_id AS trackId, COUNT(*) AS enrolled,
            ROUND(AVG(e.track_percent)) AS avgPercent
     FROM enrollments e
     JOIN users_mirror u ON u.uid = e.uid
     WHERE 1=1${schoolFilter}
     GROUP BY e.track_id`,
    params,
  );
  return {
    source: getPrimaryEngine(),
    data: {
      enrollmentsTotal,
      avgTrackPercent,
      completions30d,
      byTrack,
      schoolId: isSuperAdmin(role) ? null : schoolId,
    },
  };
}

export async function adminMenteeProgress(mentorId, actorUid) {
  const viewer = actorUid || mentorId;
  const role = await loadUserRole(viewer);
  let rows;
  if (role === "Mentor") {
    rows = await dbAll(
      `SELECT e.uid, e.track_id, e.track_percent, e.last_active_at,
              u.display_name, u.photo_url
       FROM enrollments e
       JOIN users_mirror u ON u.uid = e.uid
       WHERE e.mentor_id = ? OR e.mentor_id IS NULL
       ORDER BY e.last_active_at DESC
       LIMIT 100`,
      [viewer],
    );
  } else {
    const schoolId = await getActorSchoolId(viewer);
    rows = await dbAll(
      `SELECT e.uid, e.track_id, e.track_percent, e.last_active_at,
              u.display_name, u.photo_url
       FROM enrollments e
       JOIN users_mirror u ON u.uid = e.uid
       WHERE COALESCE(u.school_id, 'nelsen-digital') = ?
       ORDER BY e.last_active_at DESC
       LIMIT 100`,
      [schoolId],
    );
  }
  return {
    source: getPrimaryEngine(),
    data: {
      mentees: rows.map((r) => ({
        uid: r.uid,
        displayName: r.display_name || "",
        photoUrl: r.photo_url || "",
        trackId: r.track_id,
        trackPercent: Number(r.track_percent),
        lastActiveAt: Number(r.last_active_at),
      })),
    },
  };
}

export async function toggleTrackLike(uid, trackId, liked) {
  const now = Date.now();
  const want = liked === undefined ? true : Boolean(liked);
  const existing = await dbGet(
    "SELECT * FROM track_likes WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  let isLiked = want;
  if (existing) {
    if (liked === undefined) {
      isLiked = !Boolean(existing.liked);
    }
    await dbRun(
      "UPDATE track_likes SET liked = ?, updated_at = ? WHERE uid = ? AND track_id = ?",
      [isLiked ? 1 : 0, now, uid, trackId],
    );
  } else {
    await dbRun(
      "INSERT INTO track_likes (uid, track_id, liked, updated_at) VALUES (?, ?, 1, ?)",
      [uid, trackId, now],
    );
    isLiked = true;
  }
  return {
    source: getPrimaryEngine(),
    data: { trackId, isLiked },
  };
}

/** Quiz submit → sets quizPct on progress (auto-score 0–100). */
export async function submitQuiz(profile, lessonId, body = {}) {
  const lesson = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [
    lessonId,
  ]);
  if (!lesson || !lesson.has_quiz) {
    const err = new Error("Quiz lesson not found");
    err.status = 404;
    throw err;
  }
  const quizPct =
    body.score !== undefined
      ? Math.max(0, Math.min(100, Number(body.score)))
      : body.passed
        ? 100
        : 0;
  const result = await patchLessonProgress(profile, lessonId, {
    opened: true,
    quizPct,
    lastPlatform: body.lastPlatform || "web",
  });
  await maybeIssueCertificate(profile.uid, lesson.track_id);
  return {
    source: getPrimaryEngine(),
    data: {
      lessonId,
      quizPct,
      lessonPercent: result.data?.progress?.lessonPercent,
      trackPercent: result.data?.progress?.trackPercent,
    },
  };
}
