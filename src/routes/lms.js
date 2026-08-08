import express from "express";
import multer from "multer";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";
import { authenticateUser } from "../middleware/auth.js";
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
  limits: { fileSize: 500 * 1024 * 1024 }, // 500MB
});

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

router.post(
  "/media/upload",
  authenticateUser,
  upload.single("file"),
  lmsController.uploadMedia,
);
router.get("/media/:mediaId", authenticateUser, lmsController.getMedia);
router.get("/media/:mediaId/play", lmsController.playMedia);

export default router;
