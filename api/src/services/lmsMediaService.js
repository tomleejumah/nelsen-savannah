import crypto from "crypto";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { dualWrite, mirrorLesson, mirrorMedia } from "./lmsMirror.js";
import { normalizeRole } from "../constants/lmsRoles.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");

export function uploadDir() {
  const raw = process.env.UPLOAD_DIR || path.join(ROOT, "uploads");
  return path.isAbsolute(raw) ? raw : path.resolve(ROOT, raw);
}

export function publicBaseUrl() {
  return (process.env.PUBLIC_BASE_URL || "http://localhost:5002").replace(
    /\/$/,
    "",
  );
}

function signingSecret() {
  return (
    process.env.MEDIA_SIGNING_SECRET ||
    process.env.DIDIT_WEBHOOK_SECRET ||
    "lms-dev-media-secret"
  );
}

export function newMediaId() {
  return `med_${crypto.randomBytes(8).toString("hex")}`;
}

export function signMediaPlayToken(mediaId, uid, ttlSec = 7200) {
  const exp = Math.floor(Date.now() / 1000) + ttlSec;
  const payload = `${mediaId}.${uid}.${exp}`;
  const sig = crypto
    .createHmac("sha256", signingSecret())
    .update(payload)
    .digest("hex");
  return { token: `${exp}.${sig}`, expiresAt: exp };
}

export function verifyMediaPlayToken(mediaId, uid, token) {
  if (!token || typeof token !== "string") return false;
  const [expStr, sig] = token.split(".");
  const exp = Number(expStr);
  if (!exp || !sig || exp < Math.floor(Date.now() / 1000)) return false;
  const payload = `${mediaId}.${uid}.${exp}`;
  const expected = crypto
    .createHmac("sha256", signingSecret())
    .update(payload)
    .digest("hex");
  try {
    return crypto.timingSafeEqual(
      Buffer.from(sig),
      Buffer.from(expected),
    );
  } catch {
    return false;
  }
}

function canUpload(role) {
  const r = normalizeRole(role);
  return r === "Mentor" || r === "SuperAdmin" || r === "SchoolAdmin" || r === "Admin";
}

async function resolveRole(uid) {
  const row = await dbGet("SELECT role FROM roles WHERE uid = ?", [uid]);
  if (row?.role) return normalizeRole(row.role);
  return "Mentee";
}

/**
 * Persist multipart file to disk + media_assets + RTDB.
 * Optional lessonId attaches media to that lesson.
 */
export async function saveUploadedMedia({
  uid,
  role,
  file,
  lessonId,
}) {
  if (!canUpload(role || (await resolveRole(uid)))) {
    const err = new Error("Only Mentor or Admin can upload media");
    err.status = 403;
    throw err;
  }
  if (!file) {
    const err = new Error("file required");
    err.status = 400;
    throw err;
  }

  const mediaId = newMediaId();
  const ext = path.extname(file.originalname || "") || guessExt(file.mimetype);
  const filename = `${mediaId}${ext}`;
  const destDir = uploadDir();
  fs.mkdirSync(destDir, { recursive: true });
  const storagePath = path.join(destDir, filename);

  // multer memory or disk — support both
  if (file.buffer) {
    fs.writeFileSync(storagePath, file.buffer);
  } else if (file.path) {
    fs.renameSync(file.path, storagePath);
  } else {
    const err = new Error("Invalid upload");
    err.status = 400;
    throw err;
  }

  const now = Date.now();
  const publicUrl = `${publicBaseUrl()}/uploads/${filename}`;
  const sizeBytes = file.size || fs.statSync(storagePath).size;

  await dualWrite({
    label: `media:${mediaId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO media_assets (
          media_id, uid, filename, mime_type, size_bytes, storage_path,
          public_url, status, duration_sec, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ready', NULL, ?, ?)`,
        [
          mediaId,
          uid,
          file.originalname || filename,
          file.mimetype || "application/octet-stream",
          sizeBytes,
          storagePath,
          publicUrl,
          now,
          now,
        ],
      );
      if (lessonId) {
        await dbRun(
          `UPDATE lessons SET media_id = ?, content_url = ?, updated_at = ?
           WHERE lesson_id = ?`,
          [mediaId, publicUrl, now, lessonId],
        );
      }
      return mediaId;
    },
    mirrorFn: async () => {
      await mirrorMedia(mediaId, {
        mediaId,
        uid,
        filename: file.originalname || filename,
        mimeType: file.mimetype,
        sizeBytes,
        status: "ready",
        storagePath: filename,
        publicUrl,
        lessonId: lessonId || null,
        createdAt: now,
      });
      if (lessonId) {
        await mirrorLesson(lessonId, {
          mediaId,
          contentUrl: publicUrl,
          updatedAt: now,
        });
      }
    },
  });

  return {
    source: getPrimaryEngine(),
    data: {
      mediaId,
      status: "ready",
      filename: file.originalname || filename,
      mimeType: file.mimetype,
      sizeBytes,
      // raw public path is NOT the enrolled playback URL
      storageKey: filename,
    },
  };
}

function guessExt(mime) {
  if (mime === "video/mp4") return ".mp4";
  if (mime === "video/webm") return ".webm";
  if (mime === "audio/mpeg") return ".mp3";
  if (mime === "application/pdf") return ".pdf";
  return "";
}

export async function getMediaStatus(mediaId) {
  const row = await dbGet("SELECT * FROM media_assets WHERE media_id = ?", [
    mediaId,
  ]);
  if (!row) {
    return { source: getPrimaryEngine(), notFound: true, data: null };
  }
  return {
    source: getPrimaryEngine(),
    data: {
      mediaId: row.media_id,
      status: row.status,
      durationSec: row.duration_sec,
      filename: row.filename,
      mimeType: row.mime_type,
      sizeBytes: row.size_bytes,
    },
  };
}

export async function getMediaFileRow(mediaId) {
  return dbGet("SELECT * FROM media_assets WHERE media_id = ?", [mediaId]);
}

/**
 * Build playback URL for an enrolled learner (signed, time-limited).
 */
export function playbackUrlFor(mediaId, uid) {
  const { token, expiresAt } = signMediaPlayToken(mediaId, uid);
  return {
    playbackUrl: `${publicBaseUrl()}/lms/media/${mediaId}/play?uid=${encodeURIComponent(uid)}&token=${token}`,
    playbackExpiresAt: expiresAt,
  };
}

export async function userMayPlayMedia(uid, mediaId) {
  const lesson = await dbGet(
    "SELECT lesson_id, track_id FROM lessons WHERE media_id = ? LIMIT 1",
    [mediaId],
  );
  if (!lesson) {
    // orphan media: uploader or admin only
    const media = await getMediaFileRow(mediaId);
    return media?.uid === uid;
  }
  const enroll = await dbGet(
    "SELECT uid FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, lesson.track_id],
  );
  if (enroll) return true;
  const role = await resolveRole(uid);
  return role === "SuperAdmin" || role === "Admin" || role === "Mentor" || role === "SchoolAdmin";
}
