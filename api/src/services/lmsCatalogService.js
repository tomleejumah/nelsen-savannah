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
import { parseLab } from "./lmsLabService.js";
import { createHash } from "node:crypto";
import { redisGetJson, redisSetJson, redisBumpVersion, redisGetVersion, redisConfigured } from "./lmsRedisCache.js";

function parseAudience(json) {
  try {
    const a = JSON.parse(json || "[]");
    return Array.isArray(a) ? a : ["Mentee"];
  } catch {
    return ["Mentee"];
  }
}

/** Org placeholders are not real tutors — treat as blank. */
function isOrgTutorName(name) {
  const n = String(name || "")
    .trim()
    .toLowerCase();
  return (
    !n ||
    n === "nelsen savannah" ||
    n === "nelsen savannah innovation hub"
  );
}

/**
 * Blank when no mentors; otherwise first linked name, or "Name + n more".
 */
export function formatTutorLabel(mentors, fallbackName = "") {
  const list = Array.isArray(mentors) ? mentors : [];
  if (list.length > 0) {
    const first =
      String(list[0].displayName || list[0].display_name || "").trim() ||
      "Mentor";
    if (list.length === 1) return first;
    return `${first} + ${list.length - 1} more`;
  }
  if (isOrgTutorName(fallbackName)) return "";
  return String(fallbackName || "").trim();
}

function mapTrackCard(
  row,
  {
    enrolled = false,
    trackPercent = 0,
    isLiked = false,
    lessonCount,
    moduleCount,
    estimatedMinutes,
    price,
    mentors,
  } = {},
) {
  const trackId = row.track_id || row.trackId;
  const lessonsN =
    lessonCount != null
      ? Number(lessonCount)
      : row.lessons != null
        ? Number(row.lessons)
        : 0;
  const minutes =
    estimatedMinutes != null
      ? Number(estimatedMinutes)
      : Number(row.estimated_minutes ?? row.estimatedMinutes ?? 0);
  // Hours derived from lesson minutes — never a stale tracks.duration column.
  const hours = minutes > 0 ? Math.max(1, Math.round(minutes / 60)) : 0;
  const mentorList = Array.isArray(mentors)
    ? mentors
    : Array.isArray(row.mentors)
      ? row.mentors
      : [];
  const rawTutorName = row.tutor_name || row.tutorName || "";
  const tutorName = formatTutorLabel(mentorList, rawTutorName);
  const hasMentors = mentorList.length > 0;
  return {
    courseId: trackId,
    tutorId: hasMentors || tutorName ? row.tutor_id || row.tutorId || "" : "",
    courseImageUrl: row.course_image_url || row.courseImageUrl || "",
    tutorAvatarUrl:
      hasMentors || tutorName
        ? row.tutor_avatar_url || row.tutorAvatarUrl || ""
        : "",
    tutorName,
    mentors: mentorList,
    courseTitle: row.title || row.courseTitle || "",
    duration: String(hours),
    estimatedMinutes: minutes,
    lessons: String(lessonsN),
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
    schoolId: row.school_id || row.schoolId || "",
    price: price || { isPaid: false, amountMinor: 0, currency: "USD" },
    ideEnabled: Boolean(Number(row.ide_enabled ?? row.ideEnabled ?? 0)),
  };
}

export async function mentorsForTrack(trackId) {
  if (!trackId) return [];
  try {
    const rows = await dbAll(
      `SELECT uid, display_name, avatar_url, linked_at
       FROM track_mentors WHERE track_id = ? ORDER BY linked_at ASC`,
      [trackId],
    );
    return (rows || []).map((r) => ({
      uid: r.uid,
      displayName: r.display_name || "Mentor",
      avatarUrl: r.avatar_url || "",
      linkedAt: Number(r.linked_at) || 0,
    }));
  } catch {
    return [];
  }
}

function mapModule(row, { modulePercent = 0, status = "available", lessonCount } = {}) {
  const releaseAtRaw = row.release_at ?? row.releaseAt;
  const dueAtRaw = row.due_at ?? row.dueAt;
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
    releaseAt: releaseAtRaw != null ? Number(releaseAtRaw) : null,
    dueAt: dueAtRaw != null ? Number(dueAtRaw) : null,
  };
}

function mapLesson(row, { lessonPercent = 0, status = "available" } = {}) {
  const rawType = row.type || "text";
  let type = rawType === "read" ? "text" : rawType;
  if (type === "lab" || type === "ide") type = "code";
  const lab = type === "code" ? parseLab(row) : null;
  return {
    lessonId: row.lesson_id || row.lessonId,
    moduleId: row.module_id || row.moduleId,
    trackId: row.track_id || row.trackId,
    title: row.title,
    does: row.does || "",
    type,
    estimatedMinutes: Number(row.estimated_minutes ?? row.estimatedMinutes ?? 0),
    hasQuiz: Boolean(row.has_quiz ?? row.hasQuiz),
    hasAssignment: Boolean(row.has_assignment ?? row.hasAssignment),
    lessonPercent: Number(lessonPercent) || 0,
    status,
    contentUrl: row.content_url || row.contentUrl || null,
    mediaId: row.media_id || row.mediaId || null,
    playbackUrl: row.playbackUrl || null,
    playbackExpiresAt: row.playbackExpiresAt || null,
    lab,
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

async function minutesByTrack() {
  const rows = await dbAll(
    `SELECT track_id, COALESCE(SUM(estimated_minutes), 0) AS m
     FROM lessons GROUP BY track_id`,
  );
  return new Map(rows.map((r) => [r.track_id, Number(r.m)]));
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

// Every school shares a course/catalog *base* snapshot, never a learner's
// enrollment, likes, progress or role filters. This is the critical hot path
// for authenticated Android users (anonymous response caching alone is not
// enough). All mentors are loaded with ONE query rather than N requests.
const baseCatalogCache = new Map();
async function baseCatalogForSchool(schoolId) {
  const variant = String(schoolId || "marketplace");
  const remoteVersion = await redisGetVersion(CATALOG_VERSION_KEY);
  const version = remoteVersion === null ? `local-${localRevision}` : remoteVersion;
  const cached = baseCatalogCache.get(variant);
  if (cached && cached.expiresAt > Date.now() && cached.version === version) {
    return cached.tracks;
  }
  const cacheKey = "nelsen:lms:school-catalog:v1:" +
    createHash("sha256").update(variant + ":" + version).digest("hex").slice(0, 24);
  const redisTracks = await redisGetJson(cacheKey);
  if (Array.isArray(redisTracks)) {
    baseCatalogCache.set(variant, { tracks: redisTracks, version,
      expiresAt: Date.now() + (redisConfigured() ? 5_000 : 30_000) });
    return redisTracks;
  }

  const rows = schoolId
    ? await dbAll(
        `SELECT * FROM tracks WHERE published = 1
         AND (school_id = ? OR school_id IS NULL OR school_id = '')
         ORDER BY sort_order ASC, track_id ASC`,
        [schoolId],
      )
    : await dbAll(
        `SELECT * FROM tracks WHERE published = 1
         AND (school_id IS NULL OR school_id = '' OR school_id = 'nelsen-digital')
         ORDER BY sort_order ASC, track_id ASC`,
      );

  const [lessonCounts, moduleCounts, minuteTotals, pricing, mentorRows] =
    await Promise.all([
      lessonCountByTrack(),
      moduleCountByTrack(),
      minutesByTrack(),
      pricingByTrack(),
      dbAll(`SELECT track_id, uid, display_name, avatar_url, linked_at
             FROM track_mentors ORDER BY track_id, linked_at ASC`).catch(() => []),
    ]);
  const mentorsByTrack = new Map();
  for (const row of mentorRows) {
    const mentors = mentorsByTrack.get(row.track_id) || [];
    mentors.push({
      uid: row.uid,
      displayName: row.display_name || "Mentor",
      avatarUrl: row.avatar_url || "",
      linkedAt: Number(row.linked_at) || 0,
    });
    mentorsByTrack.set(row.track_id, mentors);
  }
  const tracks = rows.map((row) => mapTrackCard(row, {
    lessonCount: lessonCounts.get(row.track_id) || 0,
    moduleCount: moduleCounts.get(row.track_id) || 0,
    estimatedMinutes: minuteTotals.get(row.track_id) || 0,
    price: pricing.get(row.track_id),
    mentors: mentorsByTrack.get(row.track_id) || [],
  }));
  if (baseCatalogCache.size >= 32) baseCatalogCache.clear();
  baseCatalogCache.set(variant, { tracks, version,
    expiresAt: Date.now() + (redisConfigured() ? 5_000 : 30_000) });
  await redisSetJson(cacheKey, tracks, 30);
  return tracks;
}

async function listTracksFromPrimary(uid, { audience, enrolled, schoolId: filterSchool } = {}) {
  const user = uid
    ? await dbGet("SELECT school_id, active_school_id FROM users_mirror WHERE uid = ?", [uid])
    : null;
  const schoolId =
    (filterSchool && String(filterSchool).trim()) ||
    user?.active_school_id ||
    user?.school_id ||
    null;
  const [baseTracks, likes, enrollMap] = await Promise.all([
    baseCatalogForSchool(schoolId),
    likesFor(uid),
    enrollmentsFor(uid),
  ]);
  let tracks = baseTracks.map((track) => ({
    ...track,
    enrolled: enrollMap.has(track.trackId),
    trackPercent: Number(enrollMap.get(track.trackId)) || 0,
    isLiked: likes.has(track.trackId),
  }));

  // Mentors see only their assigned courses in host-facing selectors.
  // Other users retain the school-filtered published catalog.
  if (uid) {
    const roleRow = await dbGet("SELECT role FROM roles WHERE uid = ?", [uid]);
    if (String(roleRow?.role || "") === "Mentor") {
      const assignedRows = await dbAll(
        "SELECT track_id FROM track_mentors WHERE uid = ?",
        [uid],
      );
      const assigned = new Set(assignedRows.map((row) => String(row.track_id)));
      tracks = tracks.filter((track) => assigned.has(String(track.trackId)));
    }
  }
  if (audience) tracks = tracks.filter((track) => track.audience.includes(audience));
  if (enrolled === "true" || enrolled === true) {
    tracks = tracks.filter((track) => track.enrolled);
  } else if (enrolled === "false" || enrolled === false) {
    tracks = tracks.filter((track) => !track.enrolled);
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
  // The RTDB fallback must honor the selected school too. Otherwise a
  // healthy catalog and a failover catalog show different course lists.
  if (filters.schoolId) {
    const schoolId = String(filters.schoolId).trim();
    tracks = tracks.filter((t) => t.schoolId === schoolId);
  } else {
    tracks = tracks.filter((t) =>
      !t.schoolId || t.schoolId === "nelsen-digital");
  }
  tracks.sort((a, b) => (a.courseTitle || "").localeCompare(b.courseTitle || ""));
  if (filters.audience) {
    tracks = tracks.filter((t) => t.audience.includes(filters.audience));
  }
  return tracks;
}

// Catalog output may embed enrollment, likes, roles and learner-specific progress.
// Cache ONLY truly anonymous public results. Never cache authenticated responses.
// Redis version keys invalidate all school/audience variants without KEYS or SCAN.
const publicTrackCache = new Map();
const CATALOG_VERSION_KEY = "nelsen:lms:public-catalog:version";
let localRevision = 0;

export async function invalidatePublicTrackCache() {
  localRevision += 1;
  publicTrackCache.clear();
  baseCatalogCache.clear();
  await redisBumpVersion(CATALOG_VERSION_KEY);
}

export async function getTracks(uid, query = {}) {
  const cacheable = !uid && !query.enrolled;
  const variant = JSON.stringify([query.schoolId || "", query.audience || ""]);
  let cacheKey = null;
  let version;
  if (cacheable) {
    const remoteVersion = await redisGetVersion(CATALOG_VERSION_KEY);
    version = remoteVersion === null ? `local-${localRevision}` : remoteVersion;
    const local = publicTrackCache.get(variant);
    if (local && local.expiresAt > Date.now() && local.version === version) {
      return local.result;
    }
    cacheKey = "nelsen:lms:public-catalog:v2:" +
      createHash("sha256").update(variant + ":" + version).digest("hex").slice(0, 24);
    const remote = await redisGetJson(cacheKey);
    if (remote && Array.isArray(remote.data?.tracks)) {
      publicTrackCache.set(variant, { result: remote, version,
        expiresAt: Date.now() + (redisConfigured() ? 5_000 : 30_000) });
      return remote;
    }
  }
  const health = await checkPrimaryHealth();
  if (health.ok) {
    try {
      const tracks = await listTracksFromPrimary(uid, query);
      const result = { source: getPrimaryEngine(), data: { tracks } };
      if (cacheable) {
        if (publicTrackCache.size >= 64) publicTrackCache.clear();
        publicTrackCache.set(variant, { result, version,
          expiresAt: Date.now() + (redisConfigured() ? 5_000 : 30_000) });
        await redisSetJson(cacheKey, result, 30);
      }
      return result;
    } catch (err) {
      console.error("[lms-tracks] primary failed:", err.message);
    }
  }
  // RTDB fallback remains uncached so recovering primary data is visible quickly.
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
      const [mods, likes, enrollMap, lessonCounts, moduleLessonCounts, minuteTotals, pricing] =
        await Promise.all([
          dbAll(
            "SELECT * FROM modules WHERE track_id = ? ORDER BY sort_order ASC",
            [trackId],
          ),
          likesFor(uid),
          enrollmentsFor(uid),
          lessonCountByTrack(),
          lessonCountByModule(),
          minutesByTrack(),
          pricingByTrack(),
        ]);
      const mentors = await mentorsForTrack(trackId);
      const track = mapTrackCard(row, {
        enrolled: enrollMap.has(trackId),
        trackPercent: enrollMap.get(trackId) || 0,
        isLiked: likes.has(trackId),
        lessonCount: lessonCounts.get(trackId) || 0,
        moduleCount: mods.length,
        estimatedMinutes: minuteTotals.get(trackId) || 0,
        price: pricing.get(trackId),
        mentors,
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
      const { assertLearningAccess } = await import("./lmsMembershipService.js");
      await assertLearningAccess(uid, row.track_id);
      const lessons = await dbAll(
        "SELECT * FROM lessons WHERE module_id = ? ORDER BY sort_order ASC",
        [moduleId],
      );
      const enrolled = uid
        ? await dbGet(
            "SELECT uid FROM enrollments WHERE uid = ? AND track_id = ?",
            [uid, row.track_id],
          )
        : null;
      const mapped = [];
      for (const l of lessons) {
        const lesson = mapLesson(l);
        if (
          enrolled &&
          lesson.mediaId &&
          (lesson.type === "pdf" || /\.pdf$/i.test(lesson.contentUrl || ""))
        ) {
          try {
            const { resolvePlaybackUrl } = await import("./lmsMediaService.js");
            const play = await resolvePlaybackUrl(lesson.mediaId, uid);
            lesson.playbackUrl = play.url;
            lesson.playbackExpiresAt = play.expiresAt;
          } catch {
            /* thumbnails degrade to the type label */
          }
        }
        mapped.push(lesson);
      }
      return {
        source: getPrimaryEngine(),
        data: {
          module: mapModule(row, { lessonCount: lessons.length }),
          lessons: mapped,
        },
      };
    } catch (err) {
      if (err?.status && err.status < 500) throw err;
      console.error("[lms-module] primary failed:", err.message);
    }
  }

  // Never use the legacy RTDB fallback to bypass the school/enrollment gate.
  if (uid) {
    const primaryModule = await dbGet("SELECT track_id FROM modules WHERE module_id = ?", [moduleId]);
    if (primaryModule?.track_id) {
      const { assertLearningAccess } = await import("./lmsMembershipService.js");
      await assertLearningAccess(uid, primaryModule.track_id);
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
      const { assertLearningAccess } = await import("./lmsMembershipService.js");
      await assertLearningAccess(uid, row.track_id);
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
        getChapterLockForLesson,
      } = await import("./lmsLearningCommerceService.js");
      const [authoredQuiz, cohortRun, chapterLock, lessonAssignments] = await Promise.all([
        hasQuiz ? getAuthoredQuiz(uid, lessonId, row.track_id) : null,
        getTrackMilestones(uid, row.track_id),
        getChapterLockForLesson(uid, row),
        hasAssignment
          ? dbAll(
              `SELECT assignment_id, title, prompt, due_at
               FROM assignments
               WHERE lesson_id = ? AND assignee_uid IS NULL
               ORDER BY created_at ASC`,
              [lessonId],
            )
          : [],
      ]);
      const cohortMilestone =
        cohortRun.milestones.find((item) => item.lessonId === lessonId) || null;
      const milestone = chapterLock
        ? {
            milestoneId: `chapter:${chapterLock.moduleId}`,
            lessonId,
            title: chapterLock.title,
            releaseAt: chapterLock.releaseAt,
            dueAt: chapterLock.dueAt,
            order: 0,
            requiresPreviousCompletion: false,
            released: chapterLock.released,
            previousComplete: true,
            available: chapterLock.available,
            completed: chapterLock.completed,
            overdue: chapterLock.overdue,
            lockedReason: chapterLock.lockedReason,
          }
        : cohortMilestone;

      const lessonType = lesson.type === "read" ? "text" : lesson.type;
      const isPdf = lessonType === "pdf";
      const progressRow = await dbGet(
        "SELECT content_pct, watch_seconds FROM progress WHERE uid = ? AND lesson_id = ?",
        [uid, lessonId],
      );

      return {
        source: getPrimaryEngine(),
        data: {
          lesson: {
            ...lesson,
            type: lessonType,
            contentUrl: enrolled ? lesson.contentUrl : null,
            playbackUrl,
            playbackExpiresAt,
            contentPct: Number(progressRow?.content_pct ?? 0),
            lastPage: Number(progressRow?.watch_seconds ?? 0),
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
            assignments: lessonAssignments.map((assignment) => ({
              id: assignment.assignment_id,
              title: assignment.title || "Assignment",
              prompt: assignment.prompt || "",
              dueAt: assignment.due_at ? Number(assignment.due_at) : null,
            })),
            assignmentPrompt:
              hasAssignment && lessonAssignments.length === 0
                ? lesson.does || "Write your response below and submit for mentor review."
                : null,
            hasAssignment,
            chapter: chapterLock
              ? {
                  moduleId: chapterLock.moduleId,
                  title: chapterLock.title,
                  releaseAt: chapterLock.releaseAt,
                  dueAt: chapterLock.dueAt,
                }
              : null,
            isPdf,
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
