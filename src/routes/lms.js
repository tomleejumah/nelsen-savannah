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

export default router;
