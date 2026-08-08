import express from "express";
import { authenticateUser } from "../middleware/auth.js";
import * as lmsController from "../controllers/lmsController.js";

const router = express.Router();

router.get("/me", authenticateUser, lmsController.getLmsMe);
router.get("/health", lmsController.getLmsHealth);

router.get("/tracks", authenticateUser, lmsController.listTracks);
router.get("/tracks/:trackId", authenticateUser, lmsController.getTrack);
router.get("/modules/:moduleId", authenticateUser, lmsController.getModule);
router.get("/lessons/:lessonId", authenticateUser, lmsController.getLesson);

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

export default router;
