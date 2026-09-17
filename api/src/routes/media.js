import express from "express";
import multer from "multer";
import path from "path";
import fs from "fs";
import crypto from "crypto";
import { fileURLToPath } from "url";
import { authenticateUser } from "../middleware/auth.js";
import { publicBaseUrl, uploadDir } from "../services/lmsMediaService.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");

const ALLOWED_FOLDERS = new Set([
  "stories",
  "community_posts",
  "community_icons",
  "chat_images",
  "chat_files",
  "banners",
  "misc",
]);

const tmpDir = path.join(uploadDir(), "_tmp");
fs.mkdirSync(tmpDir, { recursive: true });
fs.mkdirSync(path.join(uploadDir(), "app"), { recursive: true });

const upload = multer({
  storage: multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, tmpDir),
    filename: (_req, file, cb) => {
      const safe = `${Date.now()}-${file.originalname.replace(/[^\w.\-]+/g, "_")}`;
      cb(null, safe);
    },
  }),
  limits: { fileSize: 40 * 1024 * 1024 },
});

const router = express.Router();

/**
 * Authenticated app media upload (stories, groups, chat, banners).
 * Files land under uploads/app/{folder}/ and are served at /uploads/app/...
 */
router.post("/upload", authenticateUser, upload.single("file"), (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ success: false, error: "Missing file" });
    }

    let folder = String(req.body?.folder || "misc").trim().toLowerCase();
    folder = folder.replace(/[^a-z0-9_\-]/g, "");
    if (!ALLOWED_FOLDERS.has(folder)) folder = "misc";

    const ext =
      path.extname(req.file.originalname || "").toLowerCase() ||
      (req.file.mimetype === "image/png"
        ? ".png"
        : req.file.mimetype === "image/webp"
          ? ".webp"
          : req.file.mimetype === "image/gif"
            ? ".gif"
            : ".jpg");

    const name = `${crypto.randomBytes(12).toString("hex")}${ext}`;
    const destDir = path.join(uploadDir(), "app", folder);
    fs.mkdirSync(destDir, { recursive: true });
    const destPath = path.join(destDir, name);
    fs.renameSync(req.file.path, destPath);

    const url = `${publicBaseUrl()}/uploads/app/${folder}/${name}`;
    return res.status(201).json({
      success: true,
      url,
      folder,
      path: `app/${folder}/${name}`,
    });
  } catch (err) {
    console.error("[POST /media/upload]", err);
    if (req.file?.path) {
      try {
        fs.unlinkSync(req.file.path);
      } catch {
        /* ignore */
      }
    }
    return res.status(500).json({ success: false, error: "Upload failed" });
  }
});

export default router;
