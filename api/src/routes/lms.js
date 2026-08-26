import express from "express";
import multer from "multer";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";
import { authenticateUser } from "../middleware/auth.js";
import { optionalAuthenticate } from "../middleware/optionalAuth.js";
import { requireRoles } from "../middleware/lmsRoles.js";
import * as lmsController from "../controllers/lmsController.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const tmpDir = path.join(
  process.env.UPLOAD_DIR || path.join(ROOT, "uploads"),
  "_tmp",
);
fs.mkdirSync(tmpDir, { recursive: true });

const upload = multer({
  storage: multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, tmpDir),
    filename: (_req, file, cb) => {
      const safe = `${Date.now()}-${file.originalname.replace(/[^\w.\-]+/g, "_")}`;
      cb(null, safe);
    },
  }),
  limits: { fileSize: 500 * 1024 * 1024 },
});

const router = express.Router();

router.get("/me", authenticateUser, lmsController.getLmsMe);
router.patch(
  "/me/active-school",
  authenticateUser,
  lmsController.patchActiveSchool,
);
router.get("/health", lmsController.getLmsHealth);

router.get("/tracks", authenticateUser, lmsController.listTracks);
router.get("/tracks/:trackId", authenticateUser, lmsController.getTrack);
router.post(
  "/tracks/:trackId/like",
  authenticateUser,
  lmsController.likeTrack,
);
router.get("/modules/:moduleId", authenticateUser, lmsController.getModule);
router.get("/lessons/:lessonId", authenticateUser, lmsController.getLesson);
router.post(
  "/lessons/:lessonId/quiz",
  authenticateUser,
  lmsController.submitQuiz,
);

router.post("/enrollments", authenticateUser, lmsController.postEnrollment);
router.get("/enrollments/me", authenticateUser, lmsController.getMyEnrollments);
router.delete(
  "/enrollments/:trackId",
  authenticateUser,
  lmsController.deleteEnrollment,
);

router.patch(
  "/progress/:lessonId",
  authenticateUser,
  lmsController.patchProgress,
);
router.get("/progress/me", authenticateUser, lmsController.getProgressMe);

router.post("/checkout", authenticateUser, lmsController.postCheckout);
router.get("/purchases/me", authenticateUser, lmsController.getMyPurchases);

router.post(
  "/media/upload",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  upload.single("file"),
  lmsController.uploadMedia,
);
router.post(
  "/media/upload-url",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.postMediaUploadUrl,
);
router.get("/media/blob", lmsController.mediaBlob);
router.put("/media/blob", lmsController.mediaBlob);
router.get("/media/:mediaId", authenticateUser, lmsController.getMedia);
router.get(
  "/media/:mediaId/url",
  authenticateUser,
  lmsController.getMediaPlaybackUrl,
);
router.post(
  "/media/:mediaId/finalize",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.postMediaFinalize,
);
router.get("/media/:mediaId/play", lmsController.playMedia);

router.post("/submissions", authenticateUser, lmsController.postSubmission);
router.get("/submissions/me", authenticateUser, lmsController.getMySubmissions);
router.get(
  "/submissions/queue",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.getSubmissionQueue,
);
router.patch(
  "/submissions/:id/mark",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.markSubmission,
);

router.get(
  "/certificates/me",
  authenticateUser,
  lmsController.getCertificatesMe,
);

router.post(
  "/assignments",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.postAssignment,
);
router.get("/assignments/me", authenticateUser, lmsController.getMyAssignments);
router.get(
  "/assignments/assigned",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.getAssignedOutbox,
);

router.get(
  "/schools",
  authenticateUser,
  requireRoles("Admin"),
  lmsController.listSchools,
);
router.post(
  "/schools",
  authenticateUser,
  requireRoles("Admin"),
  lmsController.createSchool,
);
router.patch(
  "/schools/:id/admins",
  authenticateUser,
  requireRoles("Admin"),
  lmsController.patchSchoolAdmins,
);
router.get(
  "/schools/:id/members",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.listSchoolMembers,
);
router.post(
  "/schools/:id/mentors",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.postSchoolMentor,
);
router.post(
  "/schools/:id/mentees",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.postSchoolMentee,
);
router.patch(
  "/schools/:id/members/:uid/role",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.patchSchoolMemberRole,
);
router.patch(
  "/schools/:id",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.patchSchoolBranding,
);
router.post(
  "/schools/:id/roster",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.postSchoolRoster,
);
router.get(
  "/schools/:id/dashboard",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.getSchoolDashboard,
);
router.get(
  "/schools/:id/money",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin"),
  lmsController.getSchoolMoney,
);
router.get(
  "/schools/:id/tutor-payouts",
  authenticateUser,
  requireRoles("SchoolAdmin", "Admin", "Mentor"),
  lmsController.getSchoolTutorPayouts,
);
router.get(
  "/schools/:id/cohorts",
  authenticateUser,
  requireRoles("Mentor", "SchoolAdmin", "Admin"),
  lmsController.listCohorts,
);
router.post(
  "/schools/:id/cohorts",
  authenticateUser,
  requireRoles("Mentor", "SchoolAdmin", "Admin"),
  lmsController.postCohort,
);
router.post(
  "/schools/:id/cohorts/:cohortId/members",
  authenticateUser,
  requireRoles("Mentor", "SchoolAdmin", "Admin"),
  lmsController.postCohortMember,
);
router.post(
  "/schools/:id/cohorts/:cohortId/runs",
  authenticateUser,
  requireRoles("Mentor", "SchoolAdmin", "Admin"),
  lmsController.postCohortRun,
);
router.post(
  "/schools/:id/cohort-runs/:runId/milestones",
  authenticateUser,
  requireRoles("Mentor", "SchoolAdmin", "Admin"),
  lmsController.postMilestone,
);
router.put(
  "/schools/:id/tracks/:trackId/pricing",
  authenticateUser,
  requireRoles("Mentor", "SchoolAdmin", "Admin"),
  lmsController.putTrackPricing,
);
router.put(
  "/schools/:id/lessons/:lessonId/quiz",
  authenticateUser,
  requireRoles("Mentor", "SchoolAdmin", "Admin"),
  lmsController.putLessonQuiz,
);
router.post(
  "/admin/tracks",
  authenticateUser,
  requireRoles("Admin", "SchoolAdmin"),
  lmsController.adminCreateTrack,
);
router.put(
  "/admin/tracks/:trackId",
  authenticateUser,
  requireRoles("Admin", "SchoolAdmin"),
  lmsController.adminUpdateTrack,
);
router.post(
  "/admin/modules",
  authenticateUser,
  requireRoles("Admin", "SchoolAdmin"),
  lmsController.adminCreateModule,
);
router.post(
  "/admin/lessons",
  authenticateUser,
  requireRoles("Admin", "SchoolAdmin"),
  lmsController.adminCreateLesson,
);
router.patch(
  "/admin/users/:uid/role",
  authenticateUser,
  requireRoles("Admin"),
  lmsController.adminSetRole,
);
router.post(
  "/admin/users/role-by-email",
  authenticateUser,
  requireRoles("Admin"),
  lmsController.adminSetRoleByEmail,
);
router.get(
  "/admin/stats",
  authenticateUser,
  requireRoles("Admin", "SchoolAdmin"),
  lmsController.adminStats,
);
router.get(
  "/admin/mentees/:mentorId/progress",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.adminMenteeProgress,
);
router.post(
  "/admin/seed",
  authenticateUser,
  requireRoles("Admin"),
  lmsController.adminForceSeed,
);
router.post(
  "/admin/media/reap",
  authenticateUser,
  requireRoles("Admin"),
  lmsController.postMediaReap,
);

// Hub events — public catalogue + reservations (web + Android)
router.get("/events/public", lmsController.getPublicHubEvents);
router.get("/events/public/:eventId", lmsController.getPublicHubEvent);
router.post(
  "/events",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.postHubEvent,
);
router.delete(
  "/events/:eventId",
  authenticateUser,
  requireRoles("Mentor", "Admin", "SchoolAdmin"),
  lmsController.deleteHubEvent,
);
router.get("/events/reservation-counts", lmsController.getEventReservationCounts);
router.post(
  "/events/:eventId/reserve",
  optionalAuthenticate,
  lmsController.postEventReserve,
);

// Legacy authenticated events index (501)
router.get("/events", authenticateUser, lmsController.eventsTodo);
router.get("/events/:eventId", authenticateUser, lmsController.eventsTodo);

export default router;
