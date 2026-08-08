/**
 * Nelsen LMS roadmap — shared contract for Android + website.
 *
 * API home (no new service): /home/tommlyjumah/web101/nisisi-africa-webhook
 *   Deployed as PM2 `nisisi-africa` on server-remote → /home/server/WebHooks/Nisisi-Africa/
 *   Auth today: Bearer Firebase ID token (`middleware/auth.js` → admin.auth().verifyIdToken)
 *   Android already: Google Sign-In → FirebaseAuth (GoogleAuthHelper.kt) → same ID token
 *   Website: same Firebase Google login; call API with Authorization: Bearer <idToken>
 *
 * Data: Postgres (system of record on server) + Firebase RTDB (realtime mirror / failover).
 * Media: object storage + managed HLS (not Firebase Storage for video bytes).
 */

/** @typedef {"Mentee" | "Mentor" | "Admin"} UserRole */

/** @typedef {"planned" | "in_progress" | "done"} MilestoneStatus */

/** @typedef {"locked" | "available" | "in_progress" | "submitted" | "passed" | "failed"} LessonStatus */

/** @typedef {"not_started" | "in_progress" | "completed" | "certified"} EnrollmentStatus */

// ─── API surface (extend nisisi-africa-webhook) ───────────────────────────────

export const API = {
  repo: "/home/tommlyjumah/web101/nisisi-africa-webhook",
  remotePath: "/home/server/WebHooks/Nisisi-Africa/",
  pm2: "nisisi-africa",
  health: "/health",
  existingMounts: ["/notifications", "/chat", "/didit"],
  /** New LMS mounts under the same Express app */
  lmsMount: "/lms",
  auth: {
    scheme: "Bearer <Firebase ID token>",
    middleware: "src/middleware/auth.js → authenticateUser",
    serviceAccount: "firebase-service-account.json (already on API + remote)",
    web: "Firebase JS SDK Google provider → getIdToken() → Authorization header",
    android: "GoogleAuthHelper → FirebaseAuth → getIdToken() → same header",
  },
};

/**
 * Endpoints Android + website both consume (all behind authenticateUser unless noted).
 * Implement as routes under src/routes/lms.js + controllers/services in the webhook repo.
 */
export const LMS_ENDPOINTS = [
  { method: "GET", path: "/lms/me", does: "uid, role, capabilities (UserData-shaped)" },
  { method: "GET", path: "/lms/tracks", does: "TrackCardDto[] — CourseItem-compatible + LMS extras" },
  { method: "GET", path: "/lms/tracks/:trackId", does: "track + modules[]" },
  { method: "GET", path: "/lms/modules/:moduleId", does: "module + lessons[]" },
  { method: "GET", path: "/lms/lessons/:lessonId", does: "lesson + signed playbackUrl" },
  { method: "POST", path: "/lms/enrollments", does: "enroll { trackId }" },
  { method: "GET", path: "/lms/enrollments/me", does: "my enrollments + %" },
  { method: "DELETE", path: "/lms/enrollments/:trackId", does: "unenroll" },
  { method: "PATCH", path: "/lms/progress/:lessonId", does: "upsert progress → lesson/module/track %" },
  { method: "GET", path: "/lms/progress/me", does: "resume map byLessonId / byTrackId" },
  { method: "POST", path: "/lms/submissions", does: "submit assignment" },
  { method: "GET", path: "/lms/submissions/me", does: "my submissions" },
  { method: "GET", path: "/lms/submissions/queue", does: "mentor pending marks" },
  { method: "PATCH", path: "/lms/submissions/:id/mark", does: "mentor score + feedback" },
  { method: "GET", path: "/lms/certificates/me", does: "certs" },
  { method: "POST", path: "/lms/tracks/:trackId/like", does: "isLiked toggle (CoursesAdapter)" },
  { method: "POST", path: "/lms/media/upload-url", does: "presigned upload" },
  { method: "POST", path: "/lms/media/webhook", does: "provider callback (no user auth)" },
  { method: "GET", path: "/lms/media/:mediaId", does: "transcode status" },
  { method: "POST", path: "/lms/admin/tracks", does: "create track" },
  { method: "PUT", path: "/lms/admin/tracks/:trackId", does: "update track" },
  { method: "POST", path: "/lms/admin/modules", does: "create module" },
  { method: "POST", path: "/lms/admin/lessons", does: "create lesson" },
  { method: "PATCH", path: "/lms/admin/users/:uid/role", does: "set Mentee|Mentor|Admin" },
  { method: "GET", path: "/lms/admin/stats", does: "org stats" },
  { method: "GET", path: "/lms/admin/mentees/:mentorId/progress", does: "mentor mentee %" },
];

/** Full request/response shapes for Android wiring: see ./lms-api-contract.js */

// ─── Dual store: Postgres + RTDB ──────────────────────────────────────────────

/**
 * Write path: API writes Postgres first, then mirrors metadata to RTDB.
 * Read path: prefer Postgres; if down, fall back to RTDB (and vice versa for live progress fan-out).
 * Video bytes never live in RTDB — only metadata (providerId, hlsUrl, duration, status).
 */
export const DATA_STORES = {
  primary: {
    engine: "postgres", // psql available on server-remote
    owns: [
      "users_mirror",
      "roles",
      "tracks",
      "modules",
      "lessons",
      "enrollments",
      "progress",
      "submissions",
      "certificates",
      "media_assets",
    ],
  },
  realtime: {
    engine: "firebase_rtdb",
    paths: {
      roles: "roles/{uid}",
      tracks: "lms/tracks/{trackId}",
      modules: "lms/modules/{moduleId}",
      lessons: "lms/lessons/{lessonId}",
      enrollments: "lms/enrollments/{uid}/{trackId}",
      progress: "lms/progress/{uid}/{lessonId}",
      submissions: "lms/submissions/{submissionId}",
      certificates: "lms/certificates/{uid}/{trackId}",
      legacyCourses: "courses/{courseId}",
    },
    role: "live sync for Android listeners + failover mirror of metadata",
  },
  failover: {
    rule: "API GET tries primary → on failure read RTDB mirror; flag response.source",
    dualWrite: "every mutating LMS endpoint: tx Postgres, then RTDB update (best-effort + retry queue)",
  },
};

/** @deprecated use DATA_STORES.realtime.paths — kept for older imports */
export const FIREBASE_PATHS = DATA_STORES.realtime.paths;

// ─── Video processing (research pick) ─────────────────────────────────────────

/**
 * Best pattern for this stack (small team, VOD LMS, existing Express API):
 *
 * 1. Client requests POST /lms/media/upload-url (auth)
 * 2. Client uploads source directly to object storage / Stream (never through Express)
 * 3. Managed transcoder builds HLS ABR ladder (240p–1080p)
 * 4. Webhook marks media_assets.status = ready; store playbackId + duration in Postgres + RTDB
 * 5. Lesson GET returns short-lived signed playback URL (enrolled users only)
 *
 * Recommendation: Cloudflare Stream (or Bunny Stream if egress cost dominates).
 * Avoid: DIY FFmpeg on the API box; avoid Firebase Storage as the video CDN.
 * Protocol: HLS first. Player: web HLS.js / Android ExoPlayer.
 */
export const VIDEO_STRATEGY = {
  pick: "cloudflare_stream",
  alternatives: ["bunny_stream", "mux"],
  avoid: ["ffmpeg_on_api_vps", "firebase_storage_as_cdn", "raw_mp4_from_origin"],
  flow: [
    "presigned / direct upload",
    "async HLS transcode",
    "CDN delivery + signed URL/token",
    "metadata only in Postgres + RTDB",
  ],
  player: { web: "hls.js", android: "ExoPlayer" },
};

// ─── Website login (reuse same Google / Firebase project) ─────────────────────

export const WEB_AUTH = {
  reuse: "Same Firebase project + Google provider as Android (service account already on API)",
  flow: [
    "Site login page → Firebase GoogleSignIn",
    "firebase.auth().currentUser.getIdToken()",
    "fetch(API + '/lms/...', { headers: { Authorization: 'Bearer ' + token } })",
    "API authenticateUser verifies token; load role from Postgres (fallback RTDB roles/{uid})",
  ],
  note: "No separate OAuth app for the LMS — one identity for Android + web.",
};

// ─── Roles (mirror Android) ─────────────────────────────────────────────────

export const ROLES = /** @type {const} */ ({
  Mentee: "Mentee",
  Mentor: "Mentor",
  Admin: "Admin",
});

/** What each role can do in the LMS (web + Android same rules). */
export const ROLE_CAPABILITIES = {
  Mentee: {
    browseCatalog: true,
    enroll: true,
    learn: true, // lessons on web + Android
    submitAssignments: true,
    viewOwnProgress: true,
    viewOwnCertificates: true,
    chatWithMentor: true,
    createCourses: false,
    markAssignments: false,
    manageUsers: false,
    publishTracks: false,
  },
  Mentor: {
    browseCatalog: true,
    enroll: true, // mentor cert tracks
    learn: true,
    submitAssignments: true,
    viewOwnProgress: true,
    viewOwnCertificates: true,
    chatWithMentor: false,
    createCourses: true,
    markAssignments: true, // rubric mark mentee work
    viewMenteeProgress: true,
    manageUsers: false,
    publishTracks: false,
  },
  Admin: {
    browseCatalog: true,
    enroll: true,
    learn: true,
    submitAssignments: true,
    viewOwnProgress: true,
    viewOwnCertificates: true,
    chatWithMentor: true,
    createCourses: true,
    markAssignments: true,
    viewMenteeProgress: true,
    manageUsers: true, // promote Mentor/Admin on roles/{uid}
    publishTracks: true,
    moderateContent: true,
    viewOrgDashboards: true,
  },
};

// ─── Progress math (single source of truth) ──────────────────────────────────

/**
 * Completion weights for a lesson. Percentages always sum to 100 per lesson.
 * Aggregate track % = weighted average of lesson % values (equal lesson weight unless overridden).
 */
export const LESSON_WEIGHTS = {
  open: 10, // opened / started
  content: 40, // read/watch progress (0–100 of this slice)
  quiz: 20, // auto-scored quiz if present
  assignment: 30, // mentor-marked assignment if present
};

/**
 * @param {{
 *   opened?: boolean,
 *   contentPct?: number, // 0–100
 *   quizPct?: number,    // 0–100
 *   assignmentPct?: number, // 0–100 (0 until mentor marks)
 *   hasQuiz?: boolean,
 *   hasAssignment?: boolean,
 * }} p
 * @returns {number} 0–100 integer
 */
export function lessonPercentComplete(p) {
  const w = { ...LESSON_WEIGHTS };
  if (!p.hasQuiz) {
    w.content += w.quiz;
    w.quiz = 0;
  }
  if (!p.hasAssignment) {
    w.content += w.assignment;
    w.assignment = 0;
  }
  const open = p.opened ? w.open : 0;
  const content = ((clamp(p.contentPct ?? 0) / 100) * w.content);
  const quiz = ((clamp(p.quizPct ?? 0) / 100) * w.quiz);
  const assignment = ((clamp(p.assignmentPct ?? 0) / 100) * w.assignment);
  return Math.round(open + content + quiz + assignment);
}

/**
 * Module % = mean of its lesson %.
 * Track % = mean of its module % (or weighted by module.weight).
 * @param {number[]} percents
 * @param {number[]} [weights]
 */
export function aggregatePercent(percents, weights) {
  if (!percents.length) return 0;
  if (!weights || weights.length !== percents.length) {
    return Math.round(percents.reduce((a, b) => a + b, 0) / percents.length);
  }
  const totalW = weights.reduce((a, b) => a + b, 0) || 1;
  const sum = percents.reduce((acc, pct, i) => acc + pct * weights[i], 0);
  return Math.round(sum / totalW);
}

/** Enrollment is complete when track % >= passThreshold (default 80) and required assignments passed. */
export const PASS_THRESHOLD = 80;

function clamp(n) {
  return Math.max(0, Math.min(100, n));
}

/**
 * Enrollment document shape
 * @typedef {{
 *   uid: string,
 *   trackId: string,
 *   role: UserRole,
 *   status: EnrollmentStatus,
 *   enrolledAt: number,
 *   lastActiveAt: number,
 *   trackPercent: number,
 *   modulesCompleted: number,
 *   modulesTotal: number,
 *   lessonsCompleted: number,
 *   lessonsTotal: number,
 *   mentorId?: string,
 *   platform: "android" | "web" | "both",
 * }} Enrollment
 */

/**
 * Progress document — updated from Android OR web via API; dual-written.
 * @typedef {{
 *   uid: string,
 *   lessonId: string,
 *   moduleId: string,
 *   trackId: string,
 *   opened: boolean,
 *   contentPct: number,
 *   quizPct: number,
 *   assignmentPct: number,
 *   lessonPercent: number,
 *   status: LessonStatus,
 *   lastPlatform: "android" | "web",
 *   updatedAt: number,
 * }} LessonProgress
 */

// ─── Delivery milestones ─────────────────────────────────────────────────────

/** @type {{ id: string, phase: string, title: string, percentOfLms: number, status: MilestoneStatus, ownerRoles: UserRole[], delivers: string[], acceptance: string[] }[]} */
export const LMS_MILESTONES = [
  {
    id: "m0-foundation",
    phase: "M0",
    title: "API ground + dual DB + web login",
    percentOfLms: 15,
    status: "planned",
    ownerRoles: ["Admin"],
    delivers: [
      "Extend nisisi-africa-webhook: /lms router, reuse authenticateUser + firebase-service-account.json",
      "Postgres schema on server-remote; dual-write helper (Postgres → RTDB mirror)",
      "GET /lms/me + role from DB with RTDB failover",
      "Website Google login (same Firebase project) → Bearer token to API",
      "Health checks for both stores",
    ],
    acceptance: [
      "Web and Android verify the same ID token against /lms/me",
      "Killing Postgres still serves catalog metadata from RTDB (flagged)",
      "Site can sign in with Google and call a protected /lms route",
    ],
  },
  {
    id: "m1-catalog",
    phase: "M1",
    title: "Catalog API: tracks → modules → lessons",
    percentOfLms: 15,
    status: "planned",
    ownerRoles: ["Admin", "Mentor"],
    delivers: [
      "CRUD + public list endpoints under /lms/tracks|modules|lessons",
      "Seed from LMS_TRACKS / LMS_MODULES in this file",
      "Website /learning + Android Learning tab consume API (not hardcoded only)",
      "Legacy courses/ kept as external resources where needed",
    ],
    acceptance: [
      "Identical catalog JSON on web and Android from /lms/tracks",
      "Each module exposes title + does + lesson count + minutes",
    ],
  },
  {
    id: "m2-enroll-progress",
    phase: "M2",
    title: "Enroll + cross-platform progress %",
    percentOfLms: 20,
    status: "planned",
    ownerRoles: ["Mentee", "Mentor", "Admin"],
    delivers: [
      "POST /lms/enrollments + GET /lms/progress/me",
      "PATCH progress with LESSON_WEIGHTS math server-side",
      "Resume Android ↔ web via shared enrollment uid",
      "Dual-write progress; clients may also listen RTDB for live %",
    ],
    acceptance: [
      "Start lesson on web, continue on Android with same %",
      "PASS_THRESHOLD (80%) gates Completed",
      "Failover read still returns last known %",
    ],
  },
  {
    id: "m3-video-media",
    phase: "M3",
    title: "Video upload → HLS (managed)",
    percentOfLms: 15,
    status: "planned",
    ownerRoles: ["Admin", "Mentor"],
    delivers: [
      "POST /lms/media/upload-url + provider webhook",
      "Cloudflare Stream (default) or Bunny — HLS ABR, signed playback",
      "media_assets metadata in Postgres + RTDB only",
      "Lesson player: web hls.js / Android ExoPlayer",
    ],
    acceptance: [
      "Upload never streams through Express",
      "Only enrolled users get a signed play URL",
      "Kenya low-bandwidth: 240p/360p renditions available",
    ],
  },
  {
    id: "m4-assignments-mentor",
    phase: "M4",
    title: "Assignments + mentor marking",
    percentOfLms: 15,
    status: "planned",
    ownerRoles: ["Mentee", "Mentor"],
    delivers: [
      "POST /lms/submissions + mentor queue + mark endpoint",
      "Rubric → assignmentPct → lesson % refresh (dual-write)",
      "Feedback thread on submission",
    ],
    acceptance: [
      "Mentor mark on Android updates mentee % on website",
      "Resubmit allowed; % uses latest mark",
    ],
  },
  {
    id: "m5-admin-certs",
    phase: "M5",
    title: "Admin CMS, quizzes, certificates",
    percentOfLms: 20,
    status: "planned",
    ownerRoles: ["Admin"],
    delivers: [
      "Admin web CMS via /lms/admin/*",
      "Role promote endpoint",
      "Org stats dashboard",
      "Quizzes + verifiable certificates",
      "WCAG / TalkBack pass",
    ],
    acceptance: [
      "Publish from admin appears on both clients without app store release",
      "Cert only if track % ≥ 80 and required assignments passed",
    ],
  },
];

/** Sum of milestone percentOfLms should be 100. */
export const LMS_ROADMAP_TOTAL_PERCENT = LMS_MILESTONES.reduce(
  (a, m) => a + m.percentOfLms,
  0,
);

// ─── Module catalog (what each module does) ──────────────────────────────────
// Aligned to site programs: Sela, Trailblazers, Scripture Safari, Go for it Codelab

/** @type {{ id: string, trackId: string, programSlug: string, title: string, does: string, audience: UserRole[], estimatedMinutes: number, lessons: { id: string, title: string, type: "read" | "video" | "quiz" | "assignment", does: string, hasQuiz?: boolean, hasAssignment?: boolean, estimatedMinutes: number }[] }[]} */
export const LMS_MODULES = [
  // ── Sela programme ──
  {
    id: "sela-orient",
    trackId: "track-sela",
    programSlug: "sela-programme",
    title: "Sela orientation",
    does: "Sets expectations, maps your starting point, and pairs you with a mentor pathway.",
    audience: ["Mentee"],
    estimatedMinutes: 45,
    lessons: [
      {
        id: "sela-orient-1",
        title: "How Sela works",
        type: "video",
        does: "Walk through cohort rhythm, mentor sessions, and LMS checkpoints.",
        estimatedMinutes: 12,
      },
      {
        id: "sela-orient-2",
        title: "Baseline self-check",
        type: "quiz",
        does: "Short quiz on goals, constraints, and preferred learning style.",
        hasQuiz: true,
        estimatedMinutes: 15,
      },
      {
        id: "sela-orient-3",
        title: "Write your 90-day aim",
        type: "assignment",
        does: "Submit one clear outcome for mentor review.",
        hasAssignment: true,
        estimatedMinutes: 18,
      },
    ],
  },
  {
    id: "sela-map",
    trackId: "track-sela",
    programSlug: "sela-programme",
    title: "Path mapping",
    does: "Turns interests into a shortlist of realistic next steps with mentor input.",
    audience: ["Mentee"],
    estimatedMinutes: 90,
    lessons: [
      {
        id: "sela-map-1",
        title: "Strengths inventory",
        type: "read",
        does: "Guided worksheet: skills you have vs skills you need.",
        estimatedMinutes: 20,
      },
      {
        id: "sela-map-2",
        title: "Option shortlist",
        type: "assignment",
        does: "Pick 3 paths; mentor marks fit and stretch.",
        hasAssignment: true,
        estimatedMinutes: 40,
      },
      {
        id: "sela-map-3",
        title: "Checkpoint quiz",
        type: "quiz",
        does: "Confirm you can explain your shortlist in under a minute.",
        hasQuiz: true,
        estimatedMinutes: 15,
      },
    ],
  },
  {
    id: "sela-execute",
    trackId: "track-sela",
    programSlug: "sela-programme",
    title: "First moves",
    does: "Converts the map into weekly actions and accountability.",
    audience: ["Mentee"],
    estimatedMinutes: 60,
    lessons: [
      {
        id: "sela-execute-1",
        title: "Weekly action plan",
        type: "assignment",
        does: "Submit a 4-week plan; mentor approves or returns.",
        hasAssignment: true,
        estimatedMinutes: 30,
      },
      {
        id: "sela-execute-2",
        title: "Progress log",
        type: "read",
        does: "How to log wins/blockers so % stays honest across app and web.",
        estimatedMinutes: 15,
      },
    ],
  },

  // ── Trailblazers ──
  {
    id: "tb-lead",
    trackId: "track-trailblazers",
    programSlug: "trailblazers",
    title: "Leadership stance",
    does: "Builds peer leadership habits: speak up, own a room, support others.",
    audience: ["Mentee"],
    estimatedMinutes: 75,
    lessons: [
      {
        id: "tb-lead-1",
        title: "Presence basics",
        type: "video",
        does: "Body language, openings, and holding attention.",
        estimatedMinutes: 20,
      },
      {
        id: "tb-lead-2",
        title: "Lead a 5-minute segment",
        type: "assignment",
        does: "Record or run a short peer segment; mentor marks clarity.",
        hasAssignment: true,
        estimatedMinutes: 40,
      },
    ],
  },
  {
    id: "tb-network",
    trackId: "track-trailblazers",
    programSlug: "trailblazers",
    title: "Network with intent",
    does: "Teaches outreach that is specific, respectful, and follow-through ready.",
    audience: ["Mentee"],
    estimatedMinutes: 50,
    lessons: [
      {
        id: "tb-network-1",
        title: "Outreach templates",
        type: "read",
        does: "Copy patterns for mentors, peers, and hiring contacts.",
        estimatedMinutes: 15,
      },
      {
        id: "tb-network-2",
        title: "Send three asks",
        type: "assignment",
        does: "Evidence of three outreach messages; mentor marks quality.",
        hasAssignment: true,
        estimatedMinutes: 25,
      },
    ],
  },

  // ── Scripture Safari ──
  {
    id: "ss-ground",
    trackId: "track-scripture-safari",
    programSlug: "scripture-safari",
    title: "Grounded foundations",
    does: "Faith-rooted framing for purpose, pressure, and community support.",
    audience: ["Mentee"],
    estimatedMinutes: 70,
    lessons: [
      {
        id: "ss-ground-1",
        title: "Purpose & pressure",
        type: "video",
        does: "Session framing identity and stress without performance.",
        estimatedMinutes: 25,
      },
      {
        id: "ss-ground-2",
        title: "Reflection journal",
        type: "assignment",
        does: "Written reflection; mentor responds with guiding questions.",
        hasAssignment: true,
        estimatedMinutes: 30,
      },
    ],
  },
  {
    id: "ss-community",
    trackId: "track-scripture-safari",
    programSlug: "scripture-safari",
    title: "Walk with others",
    does: "Practices peer support rhythms inside the Safari cohort.",
    audience: ["Mentee"],
    estimatedMinutes: 40,
    lessons: [
      {
        id: "ss-community-1",
        title: "Buddy check-in ritual",
        type: "read",
        does: "How to run a 15-minute peer check-in.",
        estimatedMinutes: 12,
      },
      {
        id: "ss-community-2",
        title: "Host one check-in",
        type: "assignment",
        does: "Log a hosted check-in; mentor confirms.",
        hasAssignment: true,
        estimatedMinutes: 20,
      },
    ],
  },

  // ── Go for it Codelab ──
  {
    id: "code-setup",
    trackId: "track-codelab",
    programSlug: "go-for-it-codelab",
    title: "Dev environment & first commit",
    does: "Gets mentees shipping — tools installed, repo ready, first PR mindset.",
    audience: ["Mentee"],
    estimatedMinutes: 90,
    lessons: [
      {
        id: "code-setup-1",
        title: "Tooling setup",
        type: "video",
        does: "Install editor, git, and run a hello project.",
        estimatedMinutes: 30,
      },
      {
        id: "code-setup-2",
        title: "First commit proof",
        type: "assignment",
        does: "Submit repo link; mentor marks completeness.",
        hasAssignment: true,
        estimatedMinutes: 40,
      },
      {
        id: "code-setup-3",
        title: "Git basics quiz",
        type: "quiz",
        does: "Check status / add / commit / push understanding.",
        hasQuiz: true,
        estimatedMinutes: 15,
      },
    ],
  },
  {
    id: "code-build",
    trackId: "track-codelab",
    programSlug: "go-for-it-codelab",
    title: "Build a small project",
    does: "Ships a portfolio-ready mini project with mentor review.",
    audience: ["Mentee"],
    estimatedMinutes: 180,
    lessons: [
      {
        id: "code-build-1",
        title: "Project brief",
        type: "read",
        does: "Scope a project that fits 1–2 weeks.",
        estimatedMinutes: 20,
      },
      {
        id: "code-build-2",
        title: "Milestone demo",
        type: "assignment",
        does: "Mid-build demo; mentor marks progress against brief.",
        hasAssignment: true,
        estimatedMinutes: 60,
      },
      {
        id: "code-build-3",
        title: "Final ship",
        type: "assignment",
        does: "Final README + demo link; mentor final mark.",
        hasAssignment: true,
        estimatedMinutes: 90,
      },
    ],
  },

  // ── Mentor academy (Mentor + Admin learners) ──
  {
    id: "mentor-safeguard",
    trackId: "track-mentor-academy",
    programSlug: "mentor-academy",
    title: "Safeguarding & listening",
    does: "Certifies mentors on safe practice before they mark mentee work.",
    audience: ["Mentor", "Admin"],
    estimatedMinutes: 80,
    lessons: [
      {
        id: "mentor-safeguard-1",
        title: "Safeguarding essentials",
        type: "read",
        does: "Boundaries, escalation, and documentation rules.",
        estimatedMinutes: 25,
      },
      {
        id: "mentor-safeguard-2",
        title: "Listening frameworks",
        type: "video",
        does: "How to coach without taking over.",
        estimatedMinutes: 20,
      },
      {
        id: "mentor-safeguard-3",
        title: "Scenario quiz",
        type: "quiz",
        does: "Pass scenarios before unlocking mentee marking.",
        hasQuiz: true,
        estimatedMinutes: 20,
      },
      {
        id: "mentor-safeguard-4",
        title: "Practice mark",
        type: "assignment",
        does: "Mark a sample assignment; Admin reviews quality.",
        hasAssignment: true,
        estimatedMinutes: 15,
      },
    ],
  },
];

/** @type {{ id: string, title: string, programSlug: string, blurb: string, audience: UserRole[], moduleIds: string[] }[]} */
export const LMS_TRACKS = [
  {
    id: "track-sela",
    title: "Sela programme track",
    programSlug: "sela-programme",
    blurb: "Orientation → path map → first moves, with mentor-marked checkpoints.",
    audience: ["Mentee"],
    moduleIds: ["sela-orient", "sela-map", "sela-execute"],
  },
  {
    id: "track-trailblazers",
    title: "Trailblazers track",
    programSlug: "trailblazers",
    blurb: "Leadership presence and intentional networking.",
    audience: ["Mentee"],
    moduleIds: ["tb-lead", "tb-network"],
  },
  {
    id: "track-scripture-safari",
    title: "Scripture Safari track",
    programSlug: "scripture-safari",
    blurb: "Faith-rooted purpose work and peer walk rhythms.",
    audience: ["Mentee"],
    moduleIds: ["ss-ground", "ss-community"],
  },
  {
    id: "track-codelab",
    title: "Go for it Codelab track",
    programSlug: "go-for-it-codelab",
    blurb: "From tooling to a shipped mini project with mentor review.",
    audience: ["Mentee"],
    moduleIds: ["code-setup", "code-build"],
  },
  {
    id: "track-mentor-academy",
    title: "Mentor Academy track",
    programSlug: "mentor-academy",
    blurb: "Required before mentors mark live mentee assignments.",
    audience: ["Mentor", "Admin"],
    moduleIds: ["mentor-safeguard"],
  },
];

// ─── Platform parity checklist ───────────────────────────────────────────────

export const PLATFORM_PARITY = [
  { feature: "Google / Firebase login → API Bearer", android: "done (GoogleAuthHelper)", web: "M0" },
  { feature: "Browse tracks via /lms/*", android: "M1", web: "M1" },
  { feature: "Enroll + synced progress %", android: "M2", web: "M2" },
  { feature: "Resume cross-device", android: "M2", web: "M2" },
  { feature: "HLS video lessons", android: "M3 ExoPlayer", web: "M3 hls.js" },
  { feature: "Submit assignments", android: "M4", web: "M4" },
  { feature: "Mentor marking queue", android: "M4", web: "M4" },
  { feature: "Admin CMS + dashboards", android: "stats M5", web: "full CMS M5" },
  { feature: "Certificates + quizzes", android: "M5", web: "M5" },
  { feature: "Postgres ↔ RTDB failover", android: "via API", web: "via API" },
];

// ─── Helpers for UI / milestones ─────────────────────────────────────────────

export function modulesForTrack(trackId) {
  return LMS_MODULES.filter((m) => m.trackId === trackId);
}

export function milestoneProgressSummary() {
  const done = LMS_MILESTONES.filter((m) => m.status === "done");
  const inProgress = LMS_MILESTONES.filter((m) => m.status === "in_progress");
  const planned = LMS_MILESTONES.filter((m) => m.status === "planned");
  const deliveredPercent = done.reduce((a, m) => a + m.percentOfLms, 0);
  return {
    deliveredPercent,
    remainingPercent: 100 - deliveredPercent,
    counts: {
      done: done.length,
      in_progress: inProgress.length,
      planned: planned.length,
      total: LMS_MILESTONES.length,
    },
  };
}

export default {
  API,
  LMS_ENDPOINTS,
  DATA_STORES,
  VIDEO_STRATEGY,
  WEB_AUTH,
  ROLES,
  ROLE_CAPABILITIES,
  LESSON_WEIGHTS,
  PASS_THRESHOLD,
  lessonPercentComplete,
  aggregatePercent,
  FIREBASE_PATHS,
  LMS_MILESTONES,
  LMS_ROADMAP_TOTAL_PERCENT,
  LMS_TRACKS,
  LMS_MODULES,
  PLATFORM_PARITY,
  modulesForTrack,
  milestoneProgressSummary,
};
