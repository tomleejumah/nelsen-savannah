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
import { normalizeRole, isSuperAdmin, canMarkAssignments } from "../constants/lmsRoles.js";
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
  const tutorId = body.tutorId || actorUid;
  const tutorName =
    body.tutorName || (await actorDisplayName(actorUid));
  const actorRow = await dbGet(
    "SELECT photo_url FROM users_mirror WHERE uid = ?",
    [actorUid],
  );
  const tutorAvatarUrl =
    body.tutorAvatarUrl || actorRow?.photo_url || "";
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
          tutorId,
          tutorName,
          tutorAvatarUrl,
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
        tutorId,
        tutorName,
        tutorAvatarUrl,
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
        tutorId,
        tutorName,
        tutorAvatarUrl,
      },
    },
  };
}

async function actorDisplayName(uid) {
  const row = await dbGet(
    "SELECT display_name, email FROM users_mirror WHERE uid = ?",
    [uid],
  );
  if (row?.display_name && row.display_name !== uid) return row.display_name;
  if (row?.email) return String(row.email).split("@")[0];
  return "Mentor";
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
  const tutorName = await actorDisplayName(actorUid);

  await dualWrite({
    label: `admin-track-upd:${trackId}`,
    writeFn: async () => {
      await dbRun(
        `UPDATE tracks SET title = ?, does = ?, program_slug = ?,
         course_image_url = ?, audience_json = ?, published = ?,
         tutor_id = ?, tutor_name = ?, updated_at = ?
         WHERE track_id = ?`,
        [
          title,
          does,
          programSlug,
          imageUrl,
          audienceJson,
          published,
          actorUid,
          tutorName,
          now,
          trackId,
        ],
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
        tutorId: actorUid,
        tutorName,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: {
      track: {
        trackId,
        courseTitle: title,
        does,
        published: Boolean(published),
        tutorId: actorUid,
        tutorName,
      },
    },
  };
}

function parseRequiredChapterWindow(body) {
  const releaseAt = Number(body.releaseAt);
  const dueAt = Number(body.dueAt);
  if (!Number.isFinite(releaseAt) || releaseAt <= 0) {
    const err = new Error("releaseAt required");
    err.status = 400;
    throw err;
  }
  if (!Number.isFinite(dueAt) || dueAt <= 0) {
    const err = new Error("dueAt required");
    err.status = 400;
    throw err;
  }
  if (dueAt < releaseAt) {
    const err = new Error("dueAt must be on or after releaseAt");
    err.status = 400;
    throw err;
  }
  return { releaseAt, dueAt };
}

function normalizeLessonType(type) {
  const t = String(type || "text").toLowerCase();
  if (t === "read") return "text";
  if (
    t === "text" ||
    t === "video" ||
    t === "pdf" ||
    t === "quiz" ||
    t === "assignment"
  ) {
    return t;
  }
  return "text";
}

export async function adminCreateModule(actorUid, body = {}) {
  const { moduleId, trackId, title } = body;
  if (!moduleId || !trackId || !title) {
    const err = new Error("moduleId, trackId, title required");
    err.status = 400;
    throw err;
  }
  const { releaseAt, dueAt } = parseRequiredChapterWindow(body);
  await assertCanEditTrack(actorUid, trackId);
  const now = Date.now();
  const does = body.blurb || body.does || "";
  await dualWrite({
    label: `admin-mod:${moduleId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO modules (
          module_id, track_id, title, does, estimated_minutes,
          sort_order, release_at, due_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          moduleId,
          trackId,
          title,
          does,
          body.estimatedMinutes || 0,
          body.order || 0,
          releaseAt,
          dueAt,
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
        does,
        estimatedMinutes: body.estimatedMinutes || 0,
        releaseAt,
        dueAt,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: {
      module: {
        moduleId,
        trackId,
        title,
        does,
        releaseAt,
        dueAt,
      },
    },
  };
}

export async function adminUpdateModule(actorUid, moduleId, body = {}) {
  const mid = String(moduleId || "").trim();
  if (!mid) {
    const err = new Error("moduleId required");
    err.status = 400;
    throw err;
  }
  const row = await dbGet("SELECT * FROM modules WHERE module_id = ?", [mid]);
  if (!row) {
    const err = new Error("Module not found");
    err.status = 404;
    throw err;
  }
  await assertCanEditTrack(actorUid, row.track_id);
  const title = body.title != null ? String(body.title).trim() : row.title;
  if (!title) {
    const err = new Error("title required");
    err.status = 400;
    throw err;
  }
  const does =
    body.blurb != null || body.does != null
      ? String(body.blurb ?? body.does ?? "")
      : row.does || "";
  const estimatedMinutes =
    body.estimatedMinutes != null
      ? Number(body.estimatedMinutes) || 0
      : Number(row.estimated_minutes || 0);
  const sortOrder =
    body.order != null || body.sortOrder != null
      ? Number(body.order ?? body.sortOrder) || 0
      : Number(row.sort_order || 0);
  let releaseAt = row.release_at != null ? Number(row.release_at) : null;
  let dueAt = row.due_at != null ? Number(row.due_at) : null;
  if (body.releaseAt != null || body.dueAt != null) {
    const window = parseRequiredChapterWindow({
      releaseAt: body.releaseAt != null ? body.releaseAt : releaseAt,
      dueAt: body.dueAt != null ? body.dueAt : dueAt,
    });
    releaseAt = window.releaseAt;
    dueAt = window.dueAt;
  } else if (!releaseAt || !dueAt) {
    const err = new Error("releaseAt and dueAt required");
    err.status = 400;
    throw err;
  }
  const now = Date.now();
  await dualWrite({
    label: `admin-mod-upd:${mid}`,
    writeFn: async () => {
      await dbRun(
        `UPDATE modules SET
          title = ?, does = ?, estimated_minutes = ?, sort_order = ?,
          release_at = ?, due_at = ?, updated_at = ?
         WHERE module_id = ?`,
        [title, does, estimatedMinutes, sortOrder, releaseAt, dueAt, now, mid],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorModule(mid, {
        moduleId: mid,
        trackId: row.track_id,
        title,
        does,
        estimatedMinutes,
        releaseAt,
        dueAt,
        updatedAt: now,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: {
      module: {
        moduleId: mid,
        trackId: row.track_id,
        title,
        does,
        releaseAt,
        dueAt,
      },
    },
  };
}

export async function adminDeleteModule(actorUid, moduleId) {
  const mid = String(moduleId || "").trim();
  if (!mid) {
    const err = new Error("moduleId required");
    err.status = 400;
    throw err;
  }
  const row = await dbGet("SELECT * FROM modules WHERE module_id = ?", [mid]);
  if (!row) {
    const err = new Error("Module not found");
    err.status = 404;
    throw err;
  }
  await assertCanEditTrack(actorUid, row.track_id);
  const lessons = await dbAll(
    "SELECT lesson_id, media_id FROM lessons WHERE module_id = ?",
    [mid],
  );
  await dualWrite({
    label: `admin-mod-del:${mid}`,
    writeFn: async () => {
      for (const lesson of lessons) {
        await dbRun("DELETE FROM progress WHERE lesson_id = ?", [lesson.lesson_id]);
        await dbRun("DELETE FROM submissions WHERE lesson_id = ?", [
          lesson.lesson_id,
        ]);
        try {
          await dbRun("DELETE FROM quiz_attempts WHERE lesson_id = ?", [
            lesson.lesson_id,
          ]);
        } catch {
          /* table may not exist on older DBs */
        }
        try {
          await dbRun("DELETE FROM quizzes WHERE lesson_id = ?", [
            lesson.lesson_id,
          ]);
        } catch {
          /* optional */
        }
        if (lesson.media_id) {
          try {
            await dbRun(
              "UPDATE media_assets SET status = ? WHERE media_id = ?",
              ["detached", lesson.media_id],
            );
          } catch {
            /* optional */
          }
        }
        await dbRun("DELETE FROM lessons WHERE lesson_id = ?", [lesson.lesson_id]);
      }
      await dbRun("DELETE FROM modules WHERE module_id = ?", [mid]);
      return true;
    },
    mirrorFn: async () => {
      const { default: admin } = await import("../config/firebase.js");
      for (const lesson of lessons) {
        await admin.database().ref(`lms/lessons/${lesson.lesson_id}`).remove();
      }
      await admin.database().ref(`lms/modules/${mid}`).remove();
    },
  });
  return {
    source: getPrimaryEngine(),
    data: { deleted: true, moduleId: mid, lessonsDeleted: lessons.length },
  };
}

export async function adminCreateLesson(actorUid, body = {}) {
  const { lessonId, moduleId, trackId, title } = body;
  if (!lessonId || !moduleId || !trackId || !title) {
    const err = new Error("lessonId, moduleId, trackId, title required");
    err.status = 400;
    throw err;
  }
  await assertCanEditTrack(actorUid, trackId);
  const now = Date.now();
  const type = normalizeLessonType(body.type);
  const does = body.blurb || body.does || "";
  const quizFlag = body.hasQuiz != null ? (body.hasQuiz ? 1 : 0) : type === "quiz" ? 1 : 0;
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
          does,
          type,
          body.estimatedMinutes || 0,
          quizFlag,
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
        does,
        type,
        hasQuiz: Boolean(quizFlag),
        hasAssignment: Boolean(hasAssignment),
        mediaId: body.mediaId || null,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: { lesson: { lessonId, moduleId, trackId, title, type, does } },
  };
}

export async function adminUpdateLesson(actorUid, lessonId, body = {}) {
  const lid = String(lessonId || "").trim();
  if (!lid) {
    const err = new Error("lessonId required");
    err.status = 400;
    throw err;
  }
  const row = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [lid]);
  if (!row) {
    const err = new Error("Lesson not found");
    err.status = 404;
    throw err;
  }
  await assertCanEditTrack(actorUid, row.track_id);
  const title = body.title != null ? String(body.title).trim() : row.title;
  if (!title) {
    const err = new Error("title required");
    err.status = 400;
    throw err;
  }
  const does =
    body.blurb != null || body.does != null
      ? String(body.blurb ?? body.does ?? "")
      : row.does || "";
  const type =
    body.type != null ? normalizeLessonType(body.type) : normalizeLessonType(row.type);
  const quizFlag =
    body.hasQuiz != null ? (body.hasQuiz ? 1 : 0) : Number(row.has_quiz || 0);
  const hasAssignment =
    body.hasAssignment != null
      ? body.hasAssignment
        ? 1
        : 0
      : Number(row.has_assignment || 0);
  const mediaId =
    body.mediaId !== undefined ? body.mediaId || null : row.media_id || null;
  const contentUrl =
    body.contentUrl !== undefined
      ? body.contentUrl || null
      : row.content_url || null;
  const estimatedMinutes =
    body.estimatedMinutes != null
      ? Number(body.estimatedMinutes) || 0
      : Number(row.estimated_minutes || 0);
  const sortOrder =
    body.order != null || body.sortOrder != null
      ? Number(body.order ?? body.sortOrder) || 0
      : Number(row.sort_order || 0);
  const now = Date.now();
  await dualWrite({
    label: `admin-lesson-upd:${lid}`,
    writeFn: async () => {
      await dbRun(
        `UPDATE lessons SET
          title = ?, does = ?, type = ?, estimated_minutes = ?,
          has_quiz = ?, has_assignment = ?, content_url = ?, media_id = ?,
          sort_order = ?, updated_at = ?
         WHERE lesson_id = ?`,
        [
          title,
          does,
          type,
          estimatedMinutes,
          quizFlag,
          hasAssignment,
          contentUrl,
          mediaId,
          sortOrder,
          now,
          lid,
        ],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorLesson(lid, {
        lessonId: lid,
        moduleId: row.module_id,
        trackId: row.track_id,
        title,
        does,
        type,
        hasQuiz: Boolean(quizFlag),
        hasAssignment: Boolean(hasAssignment),
        mediaId,
        contentUrl,
        updatedAt: now,
      });
    },
  });
  return {
    source: getPrimaryEngine(),
    data: {
      lesson: {
        lessonId: lid,
        moduleId: row.module_id,
        trackId: row.track_id,
        title,
        type,
        does,
        hasQuiz: Boolean(quizFlag),
        mediaId,
      },
    },
  };
}

export async function adminDeleteLesson(actorUid, lessonId) {
  const lid = String(lessonId || "").trim();
  if (!lid) {
    const err = new Error("lessonId required");
    err.status = 400;
    throw err;
  }
  const row = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [lid]);
  if (!row) {
    const err = new Error("Lesson not found");
    err.status = 404;
    throw err;
  }
  await assertCanEditTrack(actorUid, row.track_id);
  await dualWrite({
    label: `admin-lesson-del:${lid}`,
    writeFn: async () => {
      await dbRun("DELETE FROM progress WHERE lesson_id = ?", [lid]);
      await dbRun("DELETE FROM submissions WHERE lesson_id = ?", [lid]);
      try {
        await dbRun("DELETE FROM quiz_attempts WHERE lesson_id = ?", [lid]);
      } catch {
        /* optional */
      }
      try {
        await dbRun("DELETE FROM quizzes WHERE lesson_id = ?", [lid]);
      } catch {
        /* optional */
      }
      if (row.media_id) {
        try {
          await dbRun(
            "UPDATE media_assets SET status = ? WHERE media_id = ?",
            ["detached", row.media_id],
          );
        } catch {
          /* optional */
        }
      }
      await dbRun("DELETE FROM lessons WHERE lesson_id = ?", [lid]);
      return true;
    },
    mirrorFn: async () => {
      const { default: admin } = await import("../config/firebase.js");
      await admin.database().ref(`lms/lessons/${lid}`).remove();
    },
  });
  return { source: getPrimaryEngine(), data: { deleted: true, lessonId: lid } };
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
              u.display_name, u.photo_url, u.email
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
              u.display_name, u.photo_url, u.email
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
      mentees: rows.map((r) => {
        const fromEmail = r.email ? String(r.email).split("@")[0] : "";
        const name =
          (r.display_name && r.display_name !== r.uid
            ? r.display_name
            : "") ||
          fromEmail ||
          "Learner";
        return {
          uid: r.uid,
          displayName: name,
          photoUrl: r.photo_url || "",
          trackId: r.track_id,
          trackPercent: Number(r.track_percent),
          lastActiveAt: Number(r.last_active_at),
        };
      }),
    },
  };
}

/** Mentor course cockpit: roster + assignment completion for one track. */
export async function adminTrackOverview(actorUid, trackId) {
  const role = await loadUserRole(actorUid);
  if (!canMarkAssignments(role) && !isSuperAdmin(role)) {
    const err = new Error("Mentor required");
    err.status = 403;
    throw err;
  }
  const tid = String(trackId || "").trim();
  if (!tid) {
    const err = new Error("trackId required");
    err.status = 400;
    throw err;
  }
  const track = await dbGet("SELECT track_id, title FROM tracks WHERE track_id = ?", [
    tid,
  ]);
  if (!track) {
    const err = new Error("Track not found");
    err.status = 404;
    throw err;
  }

  const students = await dbAll(
    `SELECT e.uid, e.track_percent, e.last_active_at, e.status,
            u.display_name, u.photo_url, u.email
     FROM enrollments e
     LEFT JOIN users_mirror u ON u.uid = e.uid
     WHERE e.track_id = ?
     ORDER BY e.track_percent DESC, u.display_name ASC`,
    [tid],
  );
  const assignments = await dbAll(
    `SELECT assignment_id, title, lesson_id, created_at, due_at
     FROM assignments WHERE track_id = ?
     ORDER BY created_at DESC`,
    [tid],
  );
  const submissions = await dbAll(
    `SELECT assignment_id, uid, status, score, submitted_at, marked_at
     FROM submissions
     WHERE track_id = ? AND assignment_id IS NOT NULL`,
    [tid],
  );
  const modules = await dbAll(
    `SELECT * FROM modules WHERE track_id = ? ORDER BY sort_order ASC, created_at ASC`,
    [tid],
  );
  const lessons = await dbAll(
    `SELECT lesson_id, module_id, title, type, does, media_id, has_quiz, sort_order
     FROM lessons WHERE track_id = ? ORDER BY sort_order ASC, created_at ASC`,
    [tid],
  );
  const lessonsByModule = new Map();
  for (const lesson of lessons) {
    const list = lessonsByModule.get(lesson.module_id) || [];
    list.push(lesson);
    lessonsByModule.set(lesson.module_id, list);
  }
  const subByKey = new Map();
  for (const s of submissions) {
    const key = `${s.assignment_id}:${s.uid}`;
    const prev = subByKey.get(key);
    if (!prev || Number(s.submitted_at) > Number(prev.submitted_at)) {
      subByKey.set(key, s);
    }
  }

  const avgProgress =
    students.length === 0
      ? 0
      : Math.round(
          students.reduce((a, s) => a + Number(s.track_percent || 0), 0) /
            students.length,
        );

  function learnerName(s) {
    const fromEmail = s.email ? String(s.email).split("@")[0] : "";
    return (
      (s.display_name && s.display_name !== s.uid ? s.display_name : "") ||
      fromEmail ||
      "Learner"
    );
  }

  return {
    source: getPrimaryEngine(),
    data: {
      trackId: tid,
      title: track.title,
      studentCount: students.length,
      avgProgress,
      chapters: modules.map((m) => {
        const chapterLessons = lessonsByModule.get(m.module_id) || [];
        return {
          moduleId: m.module_id,
          title: m.title,
          does: m.does || "",
          releaseAt: m.release_at != null ? Number(m.release_at) : null,
          dueAt: m.due_at != null ? Number(m.due_at) : null,
          lessonCount: chapterLessons.length,
          lessons: chapterLessons.map((l) => ({
            lessonId: l.lesson_id,
            title: l.title,
            type: l.type || "text",
            does: l.does || "",
            mediaId: l.media_id || null,
            hasQuiz: Boolean(l.has_quiz),
          })),
        };
      }),
      students: students.map((s) => ({
        uid: s.uid,
        displayName: learnerName(s),
        photoUrl: s.photo_url || "",
        trackPercent: Number(s.track_percent || 0),
        status: s.status || "in_progress",
        lastActiveAt: Number(s.last_active_at || 0),
      })),
      assignments: assignments.map((a) => {
        const rows = students.map((s) => {
          const sub = subByKey.get(`${a.assignment_id}:${s.uid}`);
          return {
            uid: s.uid,
            displayName: learnerName(s),
            status: sub ? String(sub.status) : "missing",
            score: sub?.score == null ? null : Number(sub.score),
            submittedAt: sub ? Number(sub.submitted_at) : null,
          };
        });
        const completed = rows.filter((r) =>
          ["submitted", "passed", "failed", "marked"].includes(r.status),
        ).length;
        return {
          id: a.assignment_id,
          title: a.title,
          lessonId: a.lesson_id || null,
          createdAt: Number(a.created_at),
          dueAt: a.due_at ? Number(a.due_at) : null,
          completedCount: completed,
          missingCount: Math.max(0, students.length - completed),
          students: rows,
        };
      }),
    },
  };
}

/** Per-student lesson progress for one track (Open → student click). */
export async function adminTrackStudentDetail(actorUid, trackId, studentUid) {
  const role = await loadUserRole(actorUid);
  if (!canMarkAssignments(role) && !isSuperAdmin(role)) {
    const err = new Error("Mentor required");
    err.status = 403;
    throw err;
  }
  const tid = String(trackId || "").trim();
  const uid = String(studentUid || "").trim();
  if (!tid || !uid) {
    const err = new Error("trackId and studentUid required");
    err.status = 400;
    throw err;
  }
  const enroll = await dbGet(
    `SELECT e.*, u.display_name, u.photo_url, u.email
     FROM enrollments e
     LEFT JOIN users_mirror u ON u.uid = e.uid
     WHERE e.uid = ? AND e.track_id = ?`,
    [uid, tid],
  );
  if (!enroll) {
    const err = new Error("Enrollment not found");
    err.status = 404;
    throw err;
  }
  const modules = await dbAll(
    `SELECT * FROM modules WHERE track_id = ? ORDER BY sort_order ASC`,
    [tid],
  );
  const lessons = await dbAll(
    `SELECT * FROM lessons WHERE track_id = ? ORDER BY sort_order ASC`,
    [tid],
  );
  const progressRows = await dbAll(
    `SELECT * FROM progress WHERE uid = ? AND track_id = ?`,
    [uid, tid],
  );
  const progressByLesson = new Map(
    progressRows.map((p) => [p.lesson_id, p]),
  );
  const submissions = await dbAll(
    `SELECT * FROM submissions WHERE uid = ? AND track_id = ?
     ORDER BY submitted_at DESC`,
    [uid, tid],
  );
  const latestSubByLesson = new Map();
  for (const s of submissions) {
    if (!latestSubByLesson.has(s.lesson_id)) {
      latestSubByLesson.set(s.lesson_id, s);
    }
  }
  const fromEmail = enroll.email ? String(enroll.email).split("@")[0] : "";
  const displayName =
    (enroll.display_name && enroll.display_name !== uid
      ? enroll.display_name
      : "") ||
    fromEmail ||
    "Learner";
  const lessonsByModule = new Map();
  for (const lesson of lessons) {
    const list = lessonsByModule.get(lesson.module_id) || [];
    list.push(lesson);
    lessonsByModule.set(lesson.module_id, list);
  }
  return {
    source: getPrimaryEngine(),
    data: {
      trackId: tid,
      student: {
        uid,
        displayName,
        photoUrl: enroll.photo_url || "",
        trackPercent: Number(enroll.track_percent || 0),
        status: enroll.status || "in_progress",
        lastActiveAt: Number(enroll.last_active_at || 0),
      },
      chapters: modules.map((m) => {
        const chapterLessons = lessonsByModule.get(m.module_id) || [];
        return {
          moduleId: m.module_id,
          title: m.title,
          does: m.does || "",
          releaseAt: m.release_at != null ? Number(m.release_at) : null,
          dueAt: m.due_at != null ? Number(m.due_at) : null,
          lessons: chapterLessons.map((l) => {
            const p = progressByLesson.get(l.lesson_id);
            const sub = latestSubByLesson.get(l.lesson_id);
            return {
              lessonId: l.lesson_id,
              title: l.title,
              type: l.type || "text",
              does: l.does || "",
              hasQuiz: Boolean(l.has_quiz),
              opened: Boolean(p?.opened),
              contentPct: Number(p?.content_pct || 0),
              watchSeconds: Number(p?.watch_seconds || 0),
              watchPct: Number(p?.watch_pct || 0),
              quizPct: Number(p?.quiz_pct || 0),
              assignmentPct: Number(p?.assignment_pct || 0),
              lessonPercent: Number(p?.lesson_percent || 0),
              status: p?.status || "available",
              submission: sub
                ? {
                    submissionId: sub.submission_id,
                    body: sub.body || "",
                    status: sub.status,
                    score: sub.score == null ? null : Number(sub.score),
                    submittedAt: Number(sub.submitted_at),
                  }
                : null,
            };
          }),
        };
      }),
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
  const learning = await import("./lmsLearningCommerceService.js");
  const authored = await learning.submitAuthoredQuiz(profile, lesson, body);
  if (authored) return authored;
  await learning.assertLessonMilestoneAvailable(profile.uid, lesson);
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
