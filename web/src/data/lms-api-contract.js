/**
 * Nelsen LMS API contract — wire Android + web against this.
 * Base: nisisi-africa-webhook (same host as today). Auth: Authorization: Bearer <Firebase ID token>
 *
 * Android today (CoursesAdapter / CourseItem) needs on a catalog card:
 *   courseId, courseImageUrl, tutorId, tutorAvatarUrl, tutorName,
 *   courseTitle, duration (hours string), lessons (count string), courseLink, isLiked
 *
 * Track list responses include those aliases so you can map → CourseItem without UI rewrite,
 * plus LMS fields (trackPercent, enrolled, does, …).
 */

export const API_BASE_NOTE =
  "Replace host with your deployed nisisi-africa URL. All /lms/* except media webhook require Bearer token.";

/** Envelope every JSON response uses */
export const envelope = {
  ok: true,
  source: "postgres", // | "rtdb" when failover
  data: {},
  error: null,
};

// ─── DTOs (Android Kotlin / Java models to add) ───────────────────────────────

/**
 * Maps 1:1 onto existing CourseItem for list/grid adapters.
 * @typedef {{
 *   courseId: string,
 *   tutorId: string,
 *   courseImageUrl: string,
 *   tutorAvatarUrl: string,
 *   tutorName: string,
 *   courseTitle: string,
 *   duration: string,
 *   lessons: string,
 *   courseLink: string,
 *   isLiked: boolean,
 *   trackId: string,
 *   programSlug: string,
 *   does: string,
 *   trackPercent: number,
 *   enrolled: boolean,
 *   audience: string[],
 * }} TrackCardDto
 */

/**
 * @typedef {{
 *   moduleId: string,
 *   trackId: string,
 *   title: string,
 *   does: string,
 *   estimatedMinutes: number,
 *   lessonCount: number,
 *   modulePercent: number,
 *   status: "locked"|"available"|"in_progress"|"completed",
 * }} ModuleDto
 */

/**
 * @typedef {{
 *   lessonId: string,
 *   moduleId: string,
 *   trackId: string,
 *   title: string,
 *   does: string,
 *   type: "read"|"video"|"quiz"|"assignment",
 *   estimatedMinutes: number,
 *   hasQuiz: boolean,
 *   hasAssignment: boolean,
 *   lessonPercent: number,
 *   status: string,
 *   contentUrl?: string,
 *   playbackUrl?: string,
 *   playbackExpiresAt?: number,
 * }} LessonDto
 */

/**
 * Mirrors UserData fields the app already caches + LMS extras.
 * @typedef {{
 *   uid: string,
 *   email: string,
 *   displayName: string,
 *   firstName: string,
 *   lastName: string,
 *   photoUrl: string,
 *   userRole: "Mentee"|"Mentor"|"Admin",
 *   capabilities: Record<string, boolean>,
 * }} MeDto
 */

// ─── Endpoints ────────────────────────────────────────────────────────────────

export const LMS_API = [
  // ── Auth / me ──
  {
    method: "GET",
    path: "/lms/me",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    body: null,
    query: null,
    response: {
      uid: "firebaseUid",
      email: "a@b.com",
      displayName: "Name",
      firstName: "A",
      lastName: "B",
      photoUrl: "https://…",
      userRole: "Mentee",
      capabilities: {
        browseCatalog: true,
        enroll: true,
        learn: true,
        submitAssignments: true,
        markAssignments: false,
        manageUsers: false,
        publishTracks: false,
      },
    },
    androidUi: "Splash / home — set Util userRole, isMentor; gate FABs like MainActivity.isMentorOrAdmin()",
  },

  // ── Catalog (maps to CourseItem list) ──
  {
    method: "GET",
    path: "/lms/tracks",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    query: { audience: "Mentee|Mentor|Admin (optional)", enrolled: "true|false (optional)" },
    body: null,
    response: {
      tracks: [
        {
          // —— CourseItem-compatible ——
          courseId: "track-sela",
          tutorId: "mentorUidOrOrg",
          courseImageUrl: "https://…/cover.jpg",
          tutorAvatarUrl: "https://…/avatar.jpg",
          tutorName: "Nelsen Savannah",
          courseTitle: "Sela programme track",
          duration: "4",
          lessons: "8",
          courseLink: "",
          isLiked: false,
          // —— LMS extras ——
          trackId: "track-sela",
          programSlug: "sela-programme",
          does: "Orientation → path map → first moves",
          trackPercent: 0,
          enrolled: false,
          audience: ["Mentee"],
          moduleCount: 3,
        },
      ],
    },
    androidUi:
      "HomeFragment Top Courses / ViewAllActivity — bind CoursesAdapter; courseLink empty → open in-app track detail",
  },
  {
    method: "GET",
    path: "/lms/tracks/:trackId",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    response: {
      track: { "/* TrackCardDto */": true },
      modules: [
        {
          moduleId: "sela-orient",
          trackId: "track-sela",
          title: "Sela orientation",
          does: "Sets expectations and mentor pathway",
          estimatedMinutes: 45,
          lessonCount: 3,
          modulePercent: 0,
          status: "available",
        },
      ],
      enrollment: null,
    },
    androidUi: "New TrackDetailActivity — module list (title + does + % + status)",
  },
  {
    method: "GET",
    path: "/lms/modules/:moduleId",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    response: {
      module: { "/* ModuleDto */": true },
      lessons: [
        {
          lessonId: "sela-orient-1",
          moduleId: "sela-orient",
          trackId: "track-sela",
          title: "How Sela works",
          does: "Cohort rhythm and checkpoints",
          type: "video",
          estimatedMinutes: 12,
          hasQuiz: false,
          hasAssignment: false,
          lessonPercent: 0,
          status: "available",
        },
      ],
    },
    androidUi: "ModuleDetail — lesson rows by type icon; tap → LessonPlayer",
  },
  {
    method: "GET",
    path: "/lms/lessons/:lessonId",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    response: {
      lesson: {
        lessonId: "sela-orient-1",
        moduleId: "sela-orient",
        trackId: "track-sela",
        title: "How Sela works",
        does: "…",
        type: "video",
        estimatedMinutes: 12,
        hasQuiz: false,
        hasAssignment: false,
        lessonPercent: 10,
        status: "in_progress",
        bodyHtml: null,
        contentUrl: null,
        playbackUrl: "https://…/manifest.m3u8?token=…",
        playbackExpiresAt: 1710000000,
        quiz: null,
        assignmentPrompt: null,
      },
    },
    androidUi:
      "LessonPlayer — ExoPlayer if playbackUrl; WebView/text if bodyHtml; quiz fragment; assignment form",
  },

  // ── Enrollments ──
  {
    method: "POST",
    path: "/lms/enrollments",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    body: { trackId: "track-sela" },
    response: {
      enrollment: {
        uid: "…",
        trackId: "track-sela",
        status: "in_progress",
        trackPercent: 0,
        modulesCompleted: 0,
        modulesTotal: 3,
        lessonsCompleted: 0,
        lessonsTotal: 8,
        mentorId: null,
        enrolledAt: 1710000000,
        platform: "android",
      },
    },
    androidUi: "Enroll CTA on TrackDetail; refresh card enrolled=true",
  },
  {
    method: "GET",
    path: "/lms/enrollments/me",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    response: {
      enrollments: [
        {
          uid: "…",
          trackId: "track-sela",
          status: "in_progress",
          trackPercent: 35,
          modulesCompleted: 1,
          modulesTotal: 3,
          lessonsCompleted: 2,
          lessonsTotal: 8,
          mentorId: "mentorUid",
          courseTitle: "Sela programme track",
          courseImageUrl: "https://…",
          nextLessonId: "sela-orient-2",
        },
      ],
    },
    androidUi: "My Learning tab / profile rv_enrolled_courses — progress bars",
  },
  {
    method: "DELETE",
    path: "/lms/enrollments/:trackId",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    body: null,
    response: { unenrolled: true, trackId: "track-sela" },
    androidUi: "Optional leave-track action",
  },

  // ── Progress (cross-device) ──
  {
    method: "PATCH",
    path: "/lms/progress/:lessonId",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    body: {
      opened: true,
      contentPct: 80,
      quizPct: 100,
      lastPlatform: "android",
    },
    response: {
      progress: {
        lessonId: "sela-orient-1",
        moduleId: "sela-orient",
        trackId: "track-sela",
        opened: true,
        contentPct: 80,
        quizPct: 100,
        assignmentPct: 0,
        lessonPercent: 70,
        status: "in_progress",
        trackPercent: 12,
        modulePercent: 40,
        lastPlatform: "android",
        updatedAt: 1710000000,
      },
    },
    androidUi: "Call on open, on ExoPlayer % ticks, on quiz submit",
  },
  {
    method: "GET",
    path: "/lms/progress/me",
    auth: true,
    query: { trackId: "optional" },
    response: {
      byLessonId: {
        "sela-orient-1": {
          lessonPercent: 70,
          status: "in_progress",
          contentPct: 80,
          updatedAt: 1710000000,
        },
      },
      byTrackId: {
        "track-sela": { trackPercent: 12, status: "in_progress" },
      },
    },
    androidUi: "Cold start / resume — paint % before opening player",
  },

  // ── Assignments ──
  {
    method: "POST",
    path: "/lms/submissions",
    auth: true,
    roles: ["Mentee", "Mentor", "Admin"],
    body: {
      lessonId: "sela-orient-3",
      text: "My 90-day aim…",
      fileUrl: null,
      linkUrl: null,
    },
    response: {
      submission: {
        id: "sub_123",
        lessonId: "sela-orient-3",
        trackId: "track-sela",
        uid: "…",
        status: "submitted",
        text: "…",
        fileUrl: null,
        linkUrl: null,
        score: null,
        feedback: null,
        submittedAt: 1710000000,
      },
    },
    androidUi: "Assignment form → submit; status chip Submitted",
  },
  {
    method: "GET",
    path: "/lms/submissions/me",
    auth: true,
    query: { trackId: "optional", status: "optional" },
    response: { submissions: [] },
    androidUi: "Mentee submission history",
  },
  {
    method: "GET",
    path: "/lms/submissions/queue",
    auth: true,
    roles: ["Mentor", "Admin"],
    response: {
      queue: [
        {
          id: "sub_123",
          lessonId: "sela-orient-3",
          lessonTitle: "Write your 90-day aim",
          trackId: "track-sela",
          menteeId: "…",
          menteeName: "Amina W.",
          menteeAvatar: "https://…",
          text: "…",
          fileUrl: null,
          linkUrl: null,
          submittedAt: 1710000000,
        },
      ],
    },
    androidUi: "Mentor home / FAB queue — mark screen",
  },
  {
    method: "PATCH",
    path: "/lms/submissions/:id/mark",
    auth: true,
    roles: ["Mentor", "Admin"],
    body: {
      score: 85,
      passed: true,
      feedback: "Clear aim. Tighten week-2 action.",
    },
    response: {
      submission: {
        id: "sub_123",
        status: "passed",
        score: 85,
        feedback: "…",
        assignmentPct: 100,
        lessonPercent: 100,
        trackPercent: 28,
      },
    },
    androidUi: "Mentor mark UI; mentee % updates on next progress fetch",
  },

  // ── Certificates ──
  {
    method: "GET",
    path: "/lms/certificates/me",
    auth: true,
    response: {
      certificates: [
        {
          trackId: "track-sela",
          courseTitle: "Sela programme track",
          issuedAt: 1710000000,
          verifyUrl: "https://…/cert/…",
          pdfUrl: "https://…/cert.pdf",
        },
      ],
    },
    androidUi: "Profile achievements / certificate list",
  },

  // ── Likes (compat with existing CoursesAdapter likeBtn) ──
  {
    method: "POST",
    path: "/lms/tracks/:trackId/like",
    auth: true,
    body: { liked: true },
    response: { trackId: "track-sela", isLiked: true },
    androidUi: "likeBtn on course card — keep current UX",
  },

  // ── Media ──
  {
    method: "POST",
    path: "/lms/media/upload-url",
    auth: true,
    roles: ["Mentor", "Admin"],
    body: {
      filename: "intro.mp4",
      contentType: "video/mp4",
      lessonId: "optional-attach",
    },
    response: {
      uploadUrl: "https://upload…",
      mediaId: "med_123",
      provider: "cloudflare_stream",
    },
    androidUi: "Admin/Mentor upload — PUT file to uploadUrl",
  },
  {
    method: "POST",
    path: "/lms/media/webhook",
    auth: false,
    note: "Signed provider webhook — not for app clients",
    body: { "/* provider payload */": true },
    response: { received: true },
    androidUi: "n/a",
  },
  {
    method: "GET",
    path: "/lms/media/:mediaId",
    auth: true,
    roles: ["Mentor", "Admin"],
    response: {
      mediaId: "med_123",
      status: "ready",
      durationSec: 720,
      playbackId: "…",
    },
    androidUi: "Poll until ready before attaching to lesson",
  },

  // ── Admin ──
  {
    method: "POST",
    path: "/lms/admin/tracks",
    auth: true,
    roles: ["Admin"],
    body: {
      trackId: "track-sela",
      title: "Sela programme track",
      programSlug: "sela-programme",
      blurb: "…",
      audience: ["Mentee"],
      imageUrl: "https://…",
      published: true,
    },
    response: { track: { "/* TrackCardDto */": true } },
    androidUi: "Optional admin app later; primarily web CMS",
  },
  {
    method: "PUT",
    path: "/lms/admin/tracks/:trackId",
    auth: true,
    roles: ["Admin"],
    body: { "/* partial track + modules[] */": true },
    response: { track: {} },
    androidUi: "Web CMS",
  },
  {
    method: "POST",
    path: "/lms/admin/modules",
    auth: true,
    roles: ["Admin"],
    body: {
      moduleId: "sela-orient",
      trackId: "track-sela",
      title: "Sela orientation",
      does: "…",
      estimatedMinutes: 45,
      order: 1,
    },
    response: { module: {} },
    androidUi: "Web CMS",
  },
  {
    method: "POST",
    path: "/lms/admin/lessons",
    auth: true,
    roles: ["Admin"],
    body: {
      lessonId: "sela-orient-1",
      moduleId: "sela-orient",
      trackId: "track-sela",
      title: "How Sela works",
      type: "video",
      mediaId: "med_123",
      order: 1,
    },
    response: { lesson: {} },
    androidUi: "Web CMS",
  },
  {
    method: "PATCH",
    path: "/lms/admin/users/:uid/role",
    auth: true,
    roles: ["Admin"],
    body: { userRole: "Mentor" },
    response: { uid: "…", userRole: "Mentor" },
    androidUi: "Web admin; mirrors RTDB roles/{uid}",
  },
  {
    method: "GET",
    path: "/lms/admin/stats",
    auth: true,
    roles: ["Admin"],
    response: {
      enrollmentsTotal: 420,
      avgTrackPercent: 47,
      completions30d: 38,
      byTrack: [{ trackId: "track-sela", enrolled: 120, avgPercent: 52 }],
    },
    androidUi: "Optional mentor/admin dashboard fragment",
  },
  {
    method: "GET",
    path: "/lms/admin/mentees/:mentorId/progress",
    auth: true,
    roles: ["Mentor", "Admin"],
    response: {
      mentees: [
        {
          uid: "…",
          displayName: "Amina W.",
          photoUrl: "https://…",
          trackId: "track-sela",
          trackPercent: 35,
          lastActiveAt: 1710000000,
        },
      ],
    },
    androidUi: "Mentor: mentee progress list (MentorItem-adjacent)",
  },
];

/** Flat list for quick Android Retrofit interface generation */
export const LMS_ROUTE_TABLE = LMS_API.map((e) => `${e.method.padEnd(6)} ${e.path}`);

export default { API_BASE_NOTE, envelope, LMS_API, LMS_ROUTE_TABLE };
