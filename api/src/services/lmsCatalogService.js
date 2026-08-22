/**
 * Catalog reads — primary DB with RTDB failover.
 * Shapes match nelsen-savanna lms-api-contract.js TrackCardDto / ModuleDto / LessonDto.
 */

import {
  checkPrimaryHealth,
  dbAll,
  dbGet,
  getPrimaryEngine,
} from "../db/lmsDb.js";
import admin from "../config/firebase.js";

function parseAudience(json) {
  try {
    const a = JSON.parse(json || "[]");
    return Array.isArray(a) ? a : ["Mentee"];
  } catch {
    return ["Mentee"];
  }
}

function mapTrackCard(row, { enrolled = false, trackPercent = 0, isLiked = false, lessonCount, moduleCount, price } = {}) {
  const trackId = row.track_id || row.trackId;
  const lessons =
    lessonCount != null
      ? String(lessonCount)
      : row.lessons != null
        ? String(row.lessons)
        : "0";
  return {
    courseId: trackId,
    tutorId: row.tutor_id || row.tutorId || "nelsen-org",
    courseImageUrl: row.course_image_url || row.courseImageUrl || "",
    tutorAvatarUrl: row.tutor_avatar_url || row.tutorAvatarUrl || "",
    tutorName: row.tutor_name || row.tutorName || "Nelsen Savannah",
    courseTitle: row.title || row.courseTitle || "",
    duration: String(row.duration != null && row.duration !== "" ? row.duration : "1"),
    lessons,
    courseLink: "",
    isLiked: Boolean(isLiked),
    trackId,
    programSlug: row.program_slug || row.programSlug || "",
    does: row.does || "",
    trackPercent: Number(trackPercent) || 0,
    enrolled: Boolean(enrolled),
    audience: Array.isArray(row.audience)
      ? row.audience
      : parseAudience(row.audience_json),
    moduleCount: Number(
      moduleCount ?? row.module_count ?? row.moduleCount ?? 0,
    ),
    schoolId: row.school_id || row.schoolId || "nelsen-digital",
    price: price || { isPaid: false, amountMinor: 0, currency: "USD" },
  };
}

function mapModule(row, { modulePercent = 0, status = "available", lessonCount } = {}) {
  return {
    moduleId: row.module_id || row.moduleId,
    trackId: row.track_id || row.trackId,
    title: row.title,
    does: row.does || "",
    estimatedMinutes: Number(row.estimated_minutes ?? row.estimatedMinutes ?? 0),
    lessonCount:
      lessonCount != null
        ? Number(lessonCount)
        : Number(row.lesson_count ?? row.lessonCount ?? 0),
    modulePercent: Number(modulePercent) || 0,
    status,
  };
}

function mapLesson(row, { lessonPercent = 0, status = "available" } = {}) {
  return {
    lessonId: row.lesson_id || row.lessonId,
    moduleId: row.module_id || row.moduleId,
    trackId: row.track_id || row.trackId,
    title: row.title,
    does: row.does || "",
    type: row.type || "read",
    estimatedMinutes: Number(row.estimated_minutes ?? row.estimatedMinutes ?? 0),
    hasQuiz: Boolean(row.has_quiz ?? row.hasQuiz),
    hasAssignment: Boolean(row.has_assignment ?? row.hasAssignment),
    lessonPercent: Number(lessonPercent) || 0,
    status,
    contentUrl: row.content_url || row.contentUrl || null,
    mediaId: row.media_id || row.mediaId || null,
    playbackUrl: row.playbackUrl || null,
    playbackExpiresAt: row.playbackExpiresAt || null,
  };
}

async function likesFor(uid) {
  if (!uid) return new Set();
  const rows = await dbAll(
    "SELECT track_id FROM track_likes WHERE uid = ? AND liked = 1",
    [uid],
  );
  return new Set(rows.map((r) => r.track_id));
}

async function enrollmentsFor(uid) {
  if (!uid) return new Map();
  const rows = await dbAll(
    "SELECT track_id, track_percent FROM enrollments WHERE uid = ?",
    [uid],
  );
  return new Map(rows.map((r) => [r.track_id, r.track_percent || 0]));
}

async function lessonCountByTrack() {
  const rows = await dbAll(
    `SELECT track_id, COUNT(*) AS c FROM lessons GROUP BY track_id`,
  );
  return new Map(rows.map((r) => [r.track_id, Number(r.c)]));
}

async function lessonCountByModule() {
  const rows = await dbAll(
    `SELECT module_id, COUNT(*) AS c FROM lessons GROUP BY module_id`,
  );
  return new Map(rows.map((r) => [r.module_id, Number(r.c)]));
}

async function moduleCountByTrack() {
  const rows = await dbAll(
    `SELECT track_id, COUNT(*) AS c FROM modules GROUP BY track_id`,
  );
  return new Map(rows.map((r) => [r.track_id, Number(r.c)]));
}

async function pricingByTrack() {
  const rows = await dbAll(
    "SELECT track_id, currency, amount_minor, active FROM track_pricing",
  );
  return new Map(
    rows.map((row) => [
      row.track_id,
      {
        isPaid: Boolean(row.active) && Number(row.amount_minor) > 0,
        amountMinor: row.active ? Number(row.amount_minor) : 0,
        currency: row.currency || "USD",
      },
    ]),
  );
}

async function listTracksFromPrimary(uid, { audience, enrolled, schoolId: filterSchool } = {}) {
  const user = await dbGet(
    "SELECT school_id, active_school_id FROM users_mirror WHERE uid = ?",
    [uid],
  );
  const schoolId =
    filterSchool || user?.active_school_id || user?.school_id || "nelsen-digital";

  // Active school catalog: that school's tracks. Unaffiliated marketplace still
  // sees nelsen-digital + unscoped until they enroll into a partner school.
  const rows = await dbAll(
    `SELECT * FROM tracks WHERE published = 1
     AND (
       school_id = ?
       OR school_id IS NULL OR school_id = ''
       OR (? = 'nelsen-digital' AND school_id = 'nelsen-digital')
     )
     ORDER BY sort_order ASC, track_id ASC`,
    [schoolId, schoolId],
  );
  const [likes, enrollMap, lessonCounts, moduleCounts, pricing] = await Promise.all([
    likesFor(uid),
    enrollmentsFor(uid),
    lessonCountByTrack(),
    moduleCountByTrack(),
    pricingByTrack(),
  ]);

  let tracks = rows.map((row) => {
    const trackId = row.track_id;
    const isEnrolled = enrollMap.has(trackId);
    return mapTrackCard(row, {
      enrolled: isEnrolled,
      trackPercent: enrollMap.get(trackId) || 0,
      isLiked: likes.has(trackId),
      lessonCount: lessonCounts.get(trackId) || 0,
      moduleCount: moduleCounts.get(trackId) || 0,
      price: pricing.get(trackId),
    });
  });

  // attach moduleCount properly
  tracks = tracks.map((t) => ({
    ...t,
    moduleCount: moduleCounts.get(t.trackId) || t.moduleCount || 0,
  }));

  if (audience) {
    tracks = tracks.filter((t) => t.audience.includes(audience));
  }
  if (enrolled === "true" || enrolled === true) {
    tracks = tracks.filter((t) => t.enrolled);
  } else if (enrolled === "false" || enrolled === false) {
    tracks = tracks.filter((t) => !t.enrolled);
  }

  return tracks;
}

async function listTracksFromRtdb(uid, filters = {}) {
  const snap = await admin.database().ref("lms/tracks").once("value");
  const val = snap.val() || {};
  let tracks = Object.values(val).map((row) =>
    mapTrackCard(
      {
        track_id: row.trackId || row.courseId,
        ...row,
        audience_json: JSON.stringify(row.audience || ["Mentee"]),
      },
      {
        enrolled: false,
        trackPercent: 0,
        isLiked: false,
        lessonCount: row.lessons,
      },
    ),
  );
  tracks.sort((a, b) => (a.courseTitle || "").localeCompare(b.courseTitle || ""));
  if (filters.audience) {
    tracks = tracks.filter((t) => t.audience.includes(filters.audience));
  }
  return tracks;
}

export async function getTracks(uid, query = {}) {
  const health = await checkPrimaryHealth();
  if (health.ok) {
    try {
      const tracks = await listTracksFromPrimary(uid, query);
      return { source: getPrimaryEngine(), data: { tracks } };
    } catch (err) {
      console.error("[lms-tracks] primary failed:", err.message);
    }
  }
  const tracks = await listTracksFromRtdb(uid, query);
  return { source: "rtdb", data: { tracks } };
}

export async function getTrackById(uid, trackId) {
  const health = await checkPrimaryHealth();
  if (health.ok) {
    try {
      const row = await dbGet(
        "SELECT * FROM tracks WHERE track_id = ? AND published = 1",
        [trackId],
      );
      if (!row) {
        return { source: getPrimaryEngine(), data: null, notFound: true };
      }
      const [mods, likes, enrollMap, lessonCounts, moduleLessonCounts, pricing] =
        await Promise.all([
          dbAll(
            "SELECT * FROM modules WHERE track_id = ? ORDER BY sort_order ASC",
            [trackId],
          ),
          likesFor(uid),
          enrollmentsFor(uid),
          lessonCountByTrack(),
          lessonCountByModule(),
          pricingByTrack(),
        ]);
      const track = mapTrackCard(row, {
        enrolled: enrollMap.has(trackId),
        trackPercent: enrollMap.get(trackId) || 0,
        isLiked: likes.has(trackId),
        lessonCount: lessonCounts.get(trackId) || 0,
        price: pricing.get(trackId),
      });
      track.moduleCount = mods.length;
      const modules = mods.map((m) =>
        mapModule(m, {
          lessonCount: moduleLessonCounts.get(m.module_id) || 0,
          status: enrollMap.has(trackId) ? "available" : "available",
        }),
      );
      const enrollment = enrollMap.has(trackId)
        ? {
            trackId,
            trackPercent: enrollMap.get(trackId) || 0,
            status: "in_progress",
          }
        : null;
      const { getTrackMilestones } = await import(
        "./lmsLearningCommerceService.js"
      );
      const cohortRun = await getTrackMilestones(uid, trackId);
      return {
        source: getPrimaryEngine(),
        data: { track, modules, enrollment, cohortRun },
      };
    } catch (err) {
      console.error("[lms-track] primary failed:", err.message);
    }
  }

  const [tSnap, mSnap] = await Promise.all([
    admin.database().ref(`lms/tracks/${trackId}`).once("value"),
    admin.database().ref("lms/modules").once("value"),
  ]);
  const t = tSnap.val();
  if (!t) return { source: "rtdb", data: null, notFound: true };
  const allMods = mSnap.val() || {};
  const modules = Object.values(allMods)
    .filter((m) => m.trackId === trackId)
    .map((m) => mapModule(m));
  return {
    source: "rtdb",
    data: {
      track: mapTrackCard({ track_id: trackId, ...t }),
      modules,
      enrollment: null,
    },
  };
}

export async function getModuleById(uid, moduleId) {
  const health = await checkPrimaryHealth();
  if (health.ok) {
    try {
      const row = await dbGet("SELECT * FROM modules WHERE module_id = ?", [
        moduleId,
      ]);
      if (!row) {
        return { source: getPrimaryEngine(), data: null, notFound: true };
      }
      const lessons = await dbAll(
        "SELECT * FROM lessons WHERE module_id = ? ORDER BY sort_order ASC",
        [moduleId],
      );
      return {
        source: getPrimaryEngine(),
        data: {
          module: mapModule(row, { lessonCount: lessons.length }),
          lessons: lessons.map((l) => mapLesson(l)),
        },
      };
    } catch (err) {
      console.error("[lms-module] primary failed:", err.message);
    }
  }

  const [mSnap, lSnap] = await Promise.all([
    admin.database().ref(`lms/modules/${moduleId}`).once("value"),
    admin.database().ref("lms/lessons").once("value"),
  ]);
  const m = mSnap.val();
  if (!m) return { source: "rtdb", data: null, notFound: true };
  const lessons = Object.values(lSnap.val() || {})
    .filter((l) => l.moduleId === moduleId)
    .map((l) => mapLesson(l));
  return {
    source: "rtdb",
    data: {
      module: mapModule({ module_id: moduleId, ...m }, { lessonCount: lessons.length }),
      lessons,
    },
  };
}

export async function getLessonById(uid, lessonId) {
  const health = await checkPrimaryHealth();
  if (health.ok) {
    try {
      const row = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [
        lessonId,
      ]);
      if (!row) {
        return { source: getPrimaryEngine(), data: null, notFound: true };
      }
      const lesson = mapLesson(row);
      let playbackUrl = null;
      let playbackExpiresAt = null;

      const enrolled = await dbGet(
        "SELECT uid FROM enrollments WHERE uid = ? AND track_id = ?",
        [uid, row.track_id],
      );
      if (row.media_id) {
        const { resolvePlaybackUrl } = await import("./lmsMediaService.js");
        try {
          const play = await resolvePlaybackUrl(row.media_id, uid);
          playbackUrl = play.url;
          playbackExpiresAt = play.expiresAt;
        } catch (err) {
          if (!err.status || err.status >= 500) {
            console.error("[lms-lesson] playback sign failed:", err.message);
          }
        }
      } else if (enrolled && row.content_url) {
        // legacy/external URL only when enrolled
        playbackUrl = row.content_url;
      }

      const hasQuiz = Boolean(lesson.hasQuiz);
      const hasAssignment = Boolean(lesson.hasAssignment);
      const {
        getAuthoredQuiz,
        getTrackMilestones,
      } = await import("./lmsLearningCommerceService.js");
      const [authoredQuiz, cohortRun] = await Promise.all([
        hasQuiz ? getAuthoredQuiz(uid, lessonId, row.track_id) : null,
        getTrackMilestones(uid, row.track_id),
      ]);
      const milestone =
        cohortRun.milestones.find((item) => item.lessonId === lessonId) || null;

      return {
        source: getPrimaryEngine(),
        data: {
          lesson: {
            ...lesson,
            contentUrl: enrolled ? lesson.contentUrl : null,
            playbackUrl,
            playbackExpiresAt,
            bodyHtml: null,
            quiz: hasQuiz
              ? authoredQuiz || {
                  mode: "self_score",
                  prompt:
                    lesson.does ||
                    "Answer the lesson questions, then record your score (0–100).",
                }
              : null,
            milestone,
            assignmentPrompt: hasAssignment
              ? lesson.does ||
                "Write your response below and submit for mentor review."
              : null,
          },
        },
      };
    } catch (err) {
      console.error("[lms-lesson] primary failed:", err.message);
    }
  }

  const snap = await admin
    .database()
    .ref(`lms/lessons/${lessonId}`)
    .once("value");
  const l = snap.val();
  if (!l) return { source: "rtdb", data: null, notFound: true };
  return {
    source: "rtdb",
    data: {
      lesson: {
        ...mapLesson({ lesson_id: lessonId, ...l }),
        playbackUrl: null,
        playbackExpiresAt: null,
        bodyHtml: null,
        quiz: null,
        assignmentPrompt: null,
      },
    },
  };
}
