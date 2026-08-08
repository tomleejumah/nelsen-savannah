#!/usr/bin/env node
/**
 * Full LMS endpoint smoke against a live base URL.
 * Usage: LMS_API_BASE=https://api.tommlyjumah.dev/nisisi-africa node scripts/lms-smoke.mjs
 */
import "dotenv/config";
import admin from "../src/config/firebase.js";

const BASE = (process.env.LMS_API_BASE || "https://api.tommlyjumah.dev/nisisi-africa").replace(
  /\/$/,
  "",
);
const apiKey = "AIzaSyDaFYL-FE94ASTC01s-C7ZlwumPJmhzQD0";

async function token(uid) {
  const custom = await admin.auth().createCustomToken(uid);
  const { idToken } = await (
    await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: custom, returnSecureToken: true }),
      },
    )
  ).json();
  return idToken;
}

async function req(method, path, token, body, isForm = false) {
  const headers = { Authorization: `Bearer ${token}` };
  let b = body;
  if (body && !isForm) {
    headers["Content-Type"] = "application/json";
    b = JSON.stringify(body);
  }
  const res = await fetch(`${BASE}${path}`, { method, headers, body: b });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 200) };
  }
  return { status: res.status, json };
}

function pass(name, ok, detail = "") {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
  return ok;
}

const results = [];

async function main() {
  console.log(`Smoke → ${BASE}\n`);
  const menteeTok = await token("lms-smoke-mentee");
  const mentorTok = await token("lms-smoke-mentor");
  const adminTok = await token("lms-smoke-admin");

  // Promote roles via RTDB+DB by calling admin after self-bootstrap: set via admin set role
  // First ensure admin exists as Admin using a one-shot: call me then patch role if needed
  // Use Firebase custom claims? No — use direct DB on local only. On live: set role through
  // first admin seed user by calling /lms/me then manually via service...
  // Bootstrap: import setUserRole after me creates user
  const { initLmsDb } = await import("../src/db/lmsDb.js");
  // Don't init local DB for live tests — use admin.database + live API only.
  // Promote via RTDB roles mirror that /lms/me syncs... set RTDB roles:
  await admin.database().ref("roles/lms-smoke-admin").set("Admin");
  await admin.database().ref("roles/lms-smoke-mentor").set("Mentor");
  await admin.database().ref("roles/lms-smoke-mentee").set("Mentee");

  let r = await req("GET", "/lms/me", adminTok);
  results.push(pass("GET /lms/me", r.json.ok && r.json.data?.uid, r.json.data?.userRole));

  r = await req("GET", "/lms/health", adminTok);
  results.push(pass("GET /lms/health", r.json.ok && r.json.data?.primary?.ok));

  r = await req("POST", "/lms/admin/seed", adminTok);
  // may 403 if role not synced yet — call me again after RTDB set
  await req("GET", "/lms/me", adminTok);
  r = await req("POST", "/lms/admin/seed", adminTok);
  results.push(
    pass(
      "POST /lms/admin/seed",
      r.status === 200 && r.json.ok,
      `tracks=${r.json.data?.trackCount}`,
    ),
  );

  r = await req("GET", "/lms/tracks", menteeTok);
  const tracks = r.json.data?.tracks || [];
  const demo = tracks.find((t) => t.trackId === "track-ui-demo") || tracks[0];
  results.push(
    pass(
      "GET /lms/tracks CourseItem fields",
      demo &&
        demo.courseId &&
        demo.courseTitle &&
        demo.tutorName &&
        typeof demo.isLiked === "boolean" &&
        demo.courseImageUrl,
      `n=${tracks.length} img=${Boolean(demo?.courseImageUrl)}`,
    ),
  );

  r = await req("GET", `/lms/tracks/${demo.trackId}`, menteeTok);
  results.push(
    pass(
      "GET /lms/tracks/:id",
      r.json.ok && r.json.data?.modules?.length > 0,
      `modules=${r.json.data?.modules?.length}`,
    ),
  );
  const moduleId = r.json.data.modules[0].moduleId;

  r = await req("GET", `/lms/modules/${moduleId}`, menteeTok);
  const lessons = r.json.data?.lessons || [];
  results.push(pass("GET /lms/modules/:id", r.json.ok && lessons.length > 0));
  const lessonId = lessons[0].lessonId;
  const quizLesson = lessons.find((l) => l.hasQuiz)?.lessonId;
  const assignLesson = lessons.find((l) => l.hasAssignment)?.lessonId;

  r = await req("POST", "/lms/enrollments", menteeTok, {
    trackId: demo.trackId,
    platform: "android",
  });
  results.push(pass("POST /lms/enrollments", r.json.ok, r.json.data?.enrollment?.status));

  r = await req("GET", "/lms/enrollments/me", menteeTok);
  results.push(pass("GET /lms/enrollments/me", r.json.ok && r.json.data?.enrollments?.length));

  r = await req("GET", `/lms/lessons/${lessonId}`, menteeTok);
  results.push(
    pass(
      "GET /lms/lessons/:id enrolled",
      r.json.ok && r.json.data?.lesson?.lessonId === lessonId,
    ),
  );

  r = await req("PATCH", `/lms/progress/${lessonId}`, menteeTok, {
    opened: true,
    contentPct: 100,
    lastPlatform: "web",
  });
  results.push(
    pass(
      "PATCH /lms/progress/:id",
      r.json.ok && r.json.data?.progress?.lessonPercent > 0,
      `lesson%=${r.json.data?.progress?.lessonPercent} track%=${r.json.data?.progress?.trackPercent}`,
    ),
  );

  r = await req("GET", `/lms/progress/me?trackId=${demo.trackId}`, menteeTok);
  results.push(
    pass(
      "GET /lms/progress/me",
      r.json.ok && r.json.data?.byTrackId?.[demo.trackId],
    ),
  );

  if (quizLesson) {
    r = await req("POST", `/lms/lessons/${quizLesson}/quiz`, menteeTok, {
      score: 100,
    });
    results.push(pass("POST /lms/lessons/:id/quiz", r.json.ok));
  } else {
    results.push(pass("POST /lms/lessons/:id/quiz", true, "skipped (no quiz)"));
  }

  if (assignLesson) {
    r = await req("POST", "/lms/submissions", menteeTok, {
      lessonId: assignLesson,
      text: "Smoke assignment body",
    });
    const subId = r.json.data?.submission?.id;
    results.push(pass("POST /lms/submissions", r.json.ok, subId));

    await req("GET", "/lms/me", mentorTok);
    r = await req("GET", "/lms/submissions/queue", mentorTok);
    results.push(pass("GET /lms/submissions/queue", r.json.ok));

    if (subId) {
      r = await req("PATCH", `/lms/submissions/${subId}/mark`, mentorTok, {
        score: 90,
        passed: true,
        feedback: "ok",
      });
      results.push(pass("PATCH /lms/submissions/:id/mark", r.json.ok));
    }
  }

  r = await req("POST", `/lms/tracks/${demo.trackId}/like`, menteeTok, {
    liked: true,
  });
  results.push(pass("POST /lms/tracks/:id/like", r.json.ok && r.json.data?.isLiked));

  r = await req("GET", "/lms/certificates/me", menteeTok);
  results.push(pass("GET /lms/certificates/me", r.json.ok));

  r = await req("GET", "/lms/admin/stats", adminTok);
  results.push(pass("GET /lms/admin/stats", r.json.ok));

  r = await req("GET", "/lms/events", menteeTok);
  results.push(pass("GET /lms/events (M6 TODO 501)", r.status === 501));

  // Media: upload as admin
  const sample = Buffer.from("smoke-mp4-bytes");
  const form = new FormData();
  form.append("file", new Blob([sample], { type: "video/mp4" }), "smoke.mp4");
  if (lessonId) form.append("lessonId", lessonId);
  r = await req("POST", "/lms/media/upload", adminTok, form, true);
  results.push(
    pass("POST /lms/media/upload", r.status === 201 && r.json.ok, r.json.data?.mediaId),
  );
  const mediaId = r.json.data?.mediaId;
  if (mediaId) {
    r = await req("GET", `/lms/media/${mediaId}`, adminTok);
    results.push(pass("GET /lms/media/:id", r.json.ok && r.json.data?.status === "ready"));

    r = await req("GET", `/lms/lessons/${lessonId}`, menteeTok);
    const playUrl = r.json.data?.lesson?.playbackUrl;
    results.push(pass("lesson playbackUrl when enrolled+media", Boolean(playUrl)));
    if (playUrl) {
      const play = await fetch(playUrl);
      const buf = Buffer.from(await play.arrayBuffer());
      results.push(pass("GET media play signed", play.status === 200 && buf.length > 0));
    }
  }

  const failed = results.filter((x) => !x).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
