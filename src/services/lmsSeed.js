/**
 * Seed LMS catalog from lmsSeed.json (from nelsen-savanna lms-roadmap.js).
 * Runs when tracks table is empty (idempotent skip otherwise).
 */

import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import { dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import {
  dualWrite,
  mirrorLesson,
  mirrorModule,
  mirrorTrack,
} from "./lmsMirror.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SEED_PATH = join(__dirname, "../data/lmsSeed.json");

const DEFAULT_TUTOR = {
  tutorId: "nelsen-org",
  tutorName: "Nelsen Savannah",
  tutorAvatarUrl: "",
  courseImageUrl: "",
};

function lessonFlags(lesson) {
  return {
    hasQuiz: Boolean(lesson.hasQuiz || lesson.type === "quiz"),
    hasAssignment: Boolean(
      lesson.hasAssignment || lesson.type === "assignment",
    ),
  };
}

export async function seedLmsCatalog({ force = false } = {}) {
  const raw = JSON.parse(readFileSync(SEED_PATH, "utf8"));
  const { tracks, modules } = raw;
  const existing = await dbGet("SELECT COUNT(*) AS c FROM tracks");
  const count = Number(existing?.c ?? 0);
  if (count > 0 && !force) {
    console.log(`[lms-seed] skip — ${count} tracks already present`);
    return { seeded: false, trackCount: count, engine: getPrimaryEngine() };
  }

  if (force && count > 0) {
    await dbRun("DELETE FROM lessons");
    await dbRun("DELETE FROM modules");
    await dbRun("DELETE FROM tracks");
  }

  const now = Date.now();
  let trackN = 0;
  let moduleN = 0;
  let lessonN = 0;

  for (let ti = 0; ti < tracks.length; ti++) {
    const t = tracks[ti];
    const audienceJson = JSON.stringify(t.audience || ["Mentee"]);
    const mods = modules.filter((m) => m.trackId === t.id);
    const minutes = mods.reduce((a, m) => a + (m.estimatedMinutes || 0), 0);
    const lessonCount = mods.reduce((a, m) => a + (m.lessons?.length || 0), 0);
    const durationHrs = String(Math.max(1, Math.round(minutes / 60) || 1));

    await dualWrite({
      label: `track:${t.id}`,
      writeFn: async () => {
        await dbRun(
          `INSERT INTO tracks (
            track_id, program_slug, title, does, course_image_url,
            tutor_id, tutor_name, tutor_avatar_url, duration, audience_json,
            sort_order, published, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)`,
          [
            t.id,
            t.programSlug || "",
            t.title,
            t.blurb || "",
            DEFAULT_TUTOR.courseImageUrl,
            DEFAULT_TUTOR.tutorId,
            DEFAULT_TUTOR.tutorName,
            DEFAULT_TUTOR.tutorAvatarUrl,
            durationHrs,
            audienceJson,
            ti,
            now,
            now,
          ],
        );
        trackN++;
        return { t, durationHrs, lessonCount, moduleCount: mods.length };
      },
      mirrorFn: async ({ t: track, durationHrs: dur, lessonCount: lc, moduleCount }) => {
        await mirrorTrack(track.id, {
          trackId: track.id,
          courseId: track.id,
          programSlug: track.programSlug,
          title: track.title,
          courseTitle: track.title,
          does: track.blurb || "",
          audience: track.audience || ["Mentee"],
          tutorId: DEFAULT_TUTOR.tutorId,
          tutorName: DEFAULT_TUTOR.tutorName,
          tutorAvatarUrl: DEFAULT_TUTOR.tutorAvatarUrl,
          courseImageUrl: DEFAULT_TUTOR.courseImageUrl,
          duration: dur,
          lessons: String(lc),
          moduleCount,
          published: true,
          sortOrder: ti,
        });
      },
    });
  }

  for (let mi = 0; mi < modules.length; mi++) {
    const m = modules[mi];
    await dualWrite({
      label: `module:${m.id}`,
      writeFn: async () => {
        await dbRun(
          `INSERT INTO modules (
            module_id, track_id, title, does, estimated_minutes,
            sort_order, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            m.id,
            m.trackId,
            m.title,
            m.does || "",
            m.estimatedMinutes || 0,
            mi,
            now,
            now,
          ],
        );
        moduleN++;
        return m;
      },
      mirrorFn: async () => {
        await mirrorModule(m.id, {
          moduleId: m.id,
          trackId: m.trackId,
          title: m.title,
          does: m.does || "",
          estimatedMinutes: m.estimatedMinutes || 0,
          sortOrder: mi,
          lessonCount: (m.lessons || []).length,
        });
      },
    });

    for (let li = 0; li < (m.lessons || []).length; li++) {
      const lesson = m.lessons[li];
      const { hasQuiz, hasAssignment } = lessonFlags(lesson);
      await dualWrite({
        label: `lesson:${lesson.id}`,
        writeFn: async () => {
          await dbRun(
            `INSERT INTO lessons (
              lesson_id, module_id, track_id, title, does, type,
              estimated_minutes, has_quiz, has_assignment, content_url,
              media_id, sort_order, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?)`,
            [
              lesson.id,
              m.id,
              m.trackId,
              lesson.title,
              lesson.does || "",
              lesson.type || "read",
              lesson.estimatedMinutes || 0,
              hasQuiz ? 1 : 0,
              hasAssignment ? 1 : 0,
              li,
              now,
              now,
            ],
          );
          lessonN++;
          return lesson;
        },
        mirrorFn: async () => {
          await mirrorLesson(lesson.id, {
            lessonId: lesson.id,
            moduleId: m.id,
            trackId: m.trackId,
            title: lesson.title,
            does: lesson.does || "",
            type: lesson.type || "read",
            estimatedMinutes: lesson.estimatedMinutes || 0,
            hasQuiz,
            hasAssignment,
            sortOrder: li,
          });
        },
      });
    }
  }

  console.log(
    `[lms-seed] inserted tracks=${trackN} modules=${moduleN} lessons=${lessonN} engine=${getPrimaryEngine()}`,
  );
  return {
    seeded: true,
    trackCount: trackN,
    moduleCount: moduleN,
    lessonCount: lessonN,
    engine: getPrimaryEngine(),
  };
}
