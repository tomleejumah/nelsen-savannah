#!/usr/bin/env node
/**
 * Seed a private QA track for edenpulp@gmail.com on the live LMS API.
 *
 * - Creates school `school-qa-edenpulp` (only that email as SchoolAdmin)
 * - Track with 2 chapters, video + PDF + text lessons, quizzes, cohort milestones
 * - Uploads free sample media via /lms/media (R2 or local on the server)
 *
 * Usage:
 *   LMS_API_BASE=https://api.nelsen-savannah.co.ke node scripts/seed-edenpulp-qa-track.mjs
 */
import "dotenv/config";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import admin from "../src/config/firebase.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = (process.env.LMS_API_BASE || "https://api.nelsen-savannah.co.ke").replace(
  /\/$/,
  "",
);
const MEDIA_DIR = path.join(__dirname, "../tmp/qa-media");
const API_KEY = "AIzaSyDaFYL-FE94ASTC01s-C7ZlwumPJmhzQD0";

const SCHOOL_ID = "school-qa-edenpulp";
const TRACK_ID = "track-qa-media-lab";
const TARGET_EMAIL = "edenpulp@gmail.com";
const TARGET_UID = "2BrN3Gp3mRag51YgPQzCh8fU7x42";
const SUPER_UID = "lms-smoke-admin";

async function tokenFor(uid) {
  const custom = await admin.auth().createCustomToken(uid);
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: custom, returnSecureToken: true }),
    },
  );
  const json = await res.json();
  if (!json.idToken) throw new Error(`token failed for ${uid}: ${JSON.stringify(json)}`);
  return json.idToken;
}

async function api(method, p, tok, body, raw = false) {
  const headers = { Authorization: `Bearer ${tok}` };
  let b;
  if (body != null && !raw) {
    headers["Content-Type"] = "application/json";
    b = JSON.stringify(body);
  } else {
    b = body;
  }
  const res = await fetch(`${BASE}${p}`, { method, headers, body: b });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 300) };
  }
  return { status: res.status, json };
}

async function putFile(uploadUrl, headers, filePath, contentType) {
  const buf = fs.readFileSync(filePath);
  const res = await fetch(uploadUrl, {
    method: "PUT",
    headers: { ...(headers || {}), "Content-Type": contentType },
    body: buf,
  });
  if (!res.ok) {
    const t = await res.text();
    throw new Error(`PUT media failed ${res.status}: ${t.slice(0, 200)}`);
  }
}

async function uploadLessonFile(tok, lessonId, filePath, contentType, filename) {
  const ticket = await api("POST", "/lms/media/upload-url", tok, {
    filename,
    contentType,
    sizeBytes: fs.statSync(filePath).size,
    scope: "lesson",
    scopeId: lessonId,
    lessonId,
    schoolId: SCHOOL_ID,
  });
  if (!ticket.json.ok || !ticket.json.data) {
    throw new Error(`upload-url failed: ${JSON.stringify(ticket.json)}`);
  }
  const d = ticket.json.data;
  await putFile(d.uploadUrl, d.headers, filePath, contentType);
  const fin = await api("POST", `/lms/media/${d.mediaId}/finalize`, tok, {});
  if (!fin.json.ok) {
    throw new Error(`finalize failed: ${JSON.stringify(fin.json)}`);
  }
  console.log(`  uploaded ${filename} → ${d.mediaId} (${fin.json.data?.driver || "?"})`);
  return d.mediaId;
}

async function main() {
  for (const f of ["video1.mp4", "video2.mp4", "sample.pdf"]) {
    const p = path.join(MEDIA_DIR, f);
    if (!fs.existsSync(p)) throw new Error(`Missing media file ${p} — download first`);
  }

  console.log(`Seeding → ${BASE}`);
  await admin.database().ref(`roles/${SUPER_UID}`).set("SuperAdmin");
  await admin.database().ref(`roles/${TARGET_UID}`).set("SchoolAdmin");

  const superTok = await tokenFor(SUPER_UID);
  const edenTok = await tokenFor(TARGET_UID);

  // Ensure /lms/me rows exist
  await api("GET", "/lms/me", superTok);
  await api("GET", "/lms/me", edenTok);

  let school = await api("POST", "/lms/schools", superTok, {
    schoolId: SCHOOL_ID,
    name: "Edenpulp QA Lab",
    adminUid: TARGET_UID,
    adminEmail: TARGET_EMAIL,
    adminDisplayName: "Eden Pulp",
  });
  if (school.status === 409 || /already exists/i.test(school.json?.error || "")) {
    console.log("School already exists — continuing");
  } else if (!school.json.ok && school.status !== 201) {
    // Some handlers wrap as { ok, data } or raw
    if (!school.json.data && school.status >= 400) {
      throw new Error(`create school failed: ${JSON.stringify(school.json)}`);
    }
  }
  console.log("School ready:", SCHOOL_ID);

  // Force edenpulp onto this school via SuperAdmin invite/roster if needed
  await api("POST", `/lms/schools/${SCHOOL_ID}/roster`, superTok, {
    email: TARGET_EMAIL,
    role: "SchoolAdmin",
    uid: TARGET_UID,
  }).catch(() => null);

  // Refresh me so active school sticks
  const me = await api("GET", "/lms/me", edenTok);
  console.log("edenpulp me:", {
    role: me.json.data?.userRole,
    schoolId: me.json.data?.schoolId,
    activeSchoolId: me.json.data?.activeSchoolId,
  });

  const now = Date.now();
  const week = 7 * 24 * 3600 * 1000;

  // Create track (idempotent: delete not available — fail soft on 409)
  let track = await api("POST", "/lms/admin/tracks", edenTok, {
    trackId: TRACK_ID,
    title: "QA Media Lab (edenpulp only)",
    blurb: "Private test course: video, PDF, quizzes, milestones.",
    schoolId: SCHOOL_ID,
    published: true,
    audience: ["Mentee", "Mentor"],
    duration: "1",
  });
  if (track.status === 409) {
    console.log("Track exists — updating publish flag");
    await api("PATCH", `/lms/admin/tracks/${TRACK_ID}`, edenTok, {
      published: true,
      title: "QA Media Lab (edenpulp only)",
      blurb: "Private test course: video, PDF, quizzes, milestones.",
    });
  } else if (!track.json.ok && track.status >= 400) {
    // try superadmin
    track = await api("POST", "/lms/admin/tracks", superTok, {
      trackId: TRACK_ID,
      title: "QA Media Lab (edenpulp only)",
      blurb: "Private test course: video, PDF, quizzes, milestones.",
      schoolId: SCHOOL_ID,
      published: true,
      audience: ["Mentee", "Mentor"],
    });
    if (!track.json.ok && track.status !== 409 && track.status >= 400) {
      throw new Error(`create track: ${JSON.stringify(track.json)}`);
    }
  }
  console.log("Track ready:", TRACK_ID);

  const chapters = [
    {
      moduleId: "mod-qa-ch1-start",
      title: "Chapter 1 · Watch & check",
      does: "Short video + quiz",
      lessons: [
        {
          lessonId: "vid-qa-blazes",
          title: "Sample video A",
          type: "video",
          file: "video1.mp4",
          contentType: "video/mp4",
          quiz: {
            questions: [
              {
                id: "q1",
                prompt: "Did the video play?",
                type: "true_false",
                correctOptionId: "a",
              },
              {
                id: "q2",
                prompt: "What is 2 + 2?",
                type: "single",
                options: [
                  { id: "a", text: "3" },
                  { id: "b", text: "4" },
                  { id: "c", text: "5" },
                ],
                correctOptionId: "b",
              },
            ],
          },
        },
        {
          lessonId: "pdf-qa-dummy",
          title: "Sample PDF",
          type: "pdf",
          file: "sample.pdf",
          contentType: "application/pdf",
          quiz: {
            questions: [
              {
                id: "q1",
                prompt: "Select all that apply: this lesson includes…",
                type: "multi_select",
                options: [
                  { id: "a", text: "A PDF" },
                  { id: "b", text: "A live goat" },
                  { id: "c", text: "A quiz" },
                ],
                correctOptionIds: ["a", "c"],
              },
              {
                id: "q2",
                prompt: "Type the word: nelsen",
                type: "short_text",
                acceptedAnswers: ["nelsen", "Nelsen"],
              },
            ],
          },
        },
      ],
    },
    {
      moduleId: "mod-qa-ch2-deeper",
      title: "Chapter 2 · Second video + notes",
      does: "Another video and a text checkpoint",
      lessons: [
        {
          lessonId: "vid-qa-escapes",
          title: "Sample video B",
          type: "video",
          file: "video2.mp4",
          contentType: "video/mp4",
          quiz: {
            questions: [
              {
                id: "q1",
                prompt: "Pick the correct statement",
                type: "single",
                options: [
                  { id: "a", text: "This is a QA lesson" },
                  { id: "b", text: "This is production content" },
                ],
                correctOptionId: "a",
              },
            ],
          },
        },
        {
          lessonId: "txt-qa-reflect",
          title: "Reflection note",
          type: "text",
          does: "Write a short reflection (assignment).",
        },
      ],
    },
  ];

  const lessonIds = [];
  for (let ci = 0; ci < chapters.length; ci++) {
    const ch = chapters[ci];
    const releaseAt = now - hourMs(1);
    const dueAt = now + week * (ci + 1);
    let mod = await api("POST", "/lms/admin/modules", edenTok, {
      moduleId: ch.moduleId,
      trackId: TRACK_ID,
      title: ch.title,
      does: ch.does,
      releaseAt,
      dueAt,
      order: ci,
    });
    if (mod.status === 409) {
      await api("PATCH", `/lms/admin/modules/${ch.moduleId}`, edenTok, {
        title: ch.title,
        does: ch.does,
        releaseAt,
        dueAt,
      });
    } else if (!mod.json.ok && mod.status >= 400 && mod.status !== 409) {
      mod = await api("POST", "/lms/admin/modules", superTok, {
        moduleId: ch.moduleId,
        trackId: TRACK_ID,
        title: ch.title,
        does: ch.does,
        releaseAt,
        dueAt,
        order: ci,
      });
    }
    console.log("Chapter:", ch.title);

    for (let li = 0; li < ch.lessons.length; li++) {
      const les = ch.lessons[li];
      lessonIds.push(les.lessonId);
      let created = await api("POST", "/lms/admin/lessons", edenTok, {
        lessonId: les.lessonId,
        moduleId: ch.moduleId,
        trackId: TRACK_ID,
        title: les.title,
        type: les.type,
        does: les.does || "",
        hasQuiz: Boolean(les.quiz),
        hasAssignment: les.type === "text",
        order: li,
        estimatedMinutes: les.type === "video" ? 3 : 5,
      });
      if (created.status === 409) {
        await api("PATCH", `/lms/admin/lessons/${les.lessonId}`, edenTok, {
          title: les.title,
          hasQuiz: Boolean(les.quiz),
          hasAssignment: les.type === "text",
        });
      }
      if (les.file) {
        await uploadLessonFile(
          edenTok,
          les.lessonId,
          path.join(MEDIA_DIR, les.file),
          les.contentType,
          les.file,
        );
      }
      if (les.quiz) {
        // Prefer multi-type payload; server may reject unknown types on older deploy —
        // fall back to single-choice only.
        let q = await api(
          "PUT",
          `/lms/schools/${SCHOOL_ID}/lessons/${les.lessonId}/quiz`,
          edenTok,
          {
            prompt: `Quiz · ${les.title}`,
            questions: les.quiz.questions,
          },
        );
        if (!q.json.ok && q.status >= 400) {
          const legacy = les.quiz.questions
            .filter((x) => x.type === "single" || x.type === "true_false" || !x.type)
            .map((x, i) => ({
              id: x.id || `q${i + 1}`,
              prompt: x.prompt,
              options:
                x.type === "true_false"
                  ? [
                      { id: "a", text: "True" },
                      { id: "b", text: "False" },
                    ]
                  : x.options,
              correctOptionId: x.correctOptionId || "a",
            }));
          if (legacy.length) {
            q = await api(
              "PUT",
              `/lms/schools/${SCHOOL_ID}/lessons/${les.lessonId}/quiz`,
              edenTok,
              { prompt: `Quiz · ${les.title}`, questions: legacy },
            );
          }
        }
        console.log(`  quiz ${les.lessonId}:`, q.json.ok ? "ok" : q.json.error || q.status);
      }
    }
  }

  // Cohort + milestones
  const cohortId = "coh-qa-edenpulp";
  const runId = "run-qa-edenpulp";
  let cohort = await api("POST", `/lms/schools/${SCHOOL_ID}/cohorts`, edenTok, {
    cohortId,
    name: "Edenpulp QA cohort",
    status: "active",
    startsAt: now - hourMs(1),
    endsAt: now + week * 4,
  });
  if (cohort.status === 409 || cohort.json.ok || cohort.status < 400) {
    console.log("Cohort ready");
  }
  let run = await api(
    "POST",
    `/lms/schools/${SCHOOL_ID}/cohorts/${cohortId}/runs`,
    edenTok,
    { runId, trackId: TRACK_ID, startsAt: now - hourMs(1), endsAt: now + week * 4 },
  );
  if (run.status === 409 || run.json.ok || run.status < 400) {
    console.log("Run ready");
  }
  for (let i = 0; i < lessonIds.length; i++) {
    const lessonId = lessonIds[i];
    const mil = await api(
      "POST",
      `/lms/schools/${SCHOOL_ID}/cohort-runs/${runId}/milestones`,
      edenTok,
      {
        milestoneId: `mil-qa-${i + 1}`,
        lessonId,
        title: `Milestone ${i + 1}`,
        releaseAt: now - hourMs(1),
        dueAt: now + week * (i + 1),
        order: i,
        requiresPreviousCompletion: i > 0,
      },
    );
    console.log(`  milestone ${i + 1}:`, mil.json.ok ? "ok" : mil.json.error || mil.status);
  }

  // Enroll target user
  const enroll = await api("POST", "/lms/enrollments", edenTok, {
    trackId: TRACK_ID,
    platform: "web",
  });
  console.log("Enroll:", enroll.json.ok ? "ok" : enroll.json.error || enroll.status);

  const tracks = await api(
    "GET",
    `/lms/tracks?schoolId=${encodeURIComponent(SCHOOL_ID)}`,
    edenTok,
  );
  const found = (tracks.json.data?.tracks || tracks.json.data || []).find?.(
    (t) => t.trackId === TRACK_ID,
  );
  console.log("\nDone.");
  console.log({
    schoolId: SCHOOL_ID,
    trackId: TRACK_ID,
    email: TARGET_EMAIL,
    visibleToEdenpulp: Boolean(found) || enroll.json.ok,
    openWeb: `https://nelsen-savannah.co.ke/learning/${TRACK_ID}`,
  });
}

function hourMs(h) {
  return h * 3600 * 1000;
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
