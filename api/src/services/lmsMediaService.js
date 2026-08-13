import crypto from "crypto";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { dualWrite, mirrorLesson, mirrorMedia } from "./lmsMirror.js";
import { DEFAULT_SCHOOL_ID, normalizeRole } from "../constants/lmsRoles.js";
import {
  buildObjectKey,
  extensionFor,
  getStorageDriver,
  normalizeScope,
  playbackTtlSeconds,
  uploadTtlSeconds,
} from "./storage/index.js";

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
  const objectKey = filename;

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
  const sizeBytes = file.size || fs.statSync(storagePath).size;
  const lesson = lessonId
    ? await dbGet(
        "SELECT lesson_id, track_id FROM lessons WHERE lesson_id = ?",
        [lessonId],
      )
    : null;
  const schoolId = lesson
    ? await trackSchoolId(lesson.track_id)
    : await actorSchoolId(uid);

  await dualWrite({
    label: `media:${mediaId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO media_assets (
          media_id, uid, school_id, scope, scope_id, track_id, filename,
          mime_type, size_bytes, storage_driver, bucket, object_key,
          storage_path, public_url, checksum, status, duration_sec,
          finalized_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'local', NULL, ?, ?, NULL, NULL, 'ready', NULL, ?, ?, ?)`,
        [
          mediaId,
          uid,
          schoolId,
          lesson ? "lesson" : "misc",
          lesson ? lesson.lesson_id : null,
          lesson ? lesson.track_id : null,
          file.originalname || filename,
          file.mimetype || "application/octet-stream",
          sizeBytes,
          objectKey,
          storagePath,
          now,
          now,
          now,
        ],
      );
      if (lessonId) {
        await dbRun(
          `UPDATE lessons SET media_id = ?, content_url = NULL, updated_at = ?
           WHERE lesson_id = ?`,
          [mediaId, now, lessonId],
        );
      }
      return mediaId;
    },
    mirrorFn: async () => {
      await mirrorMedia(mediaId, {
        mediaId,
        uid,
        schoolId,
        filename: file.originalname || filename,
        mimeType: file.mimetype,
        sizeBytes,
        status: "ready",
        storageDriver: "local",
        lessonId: lessonId || null,
        createdAt: now,
      });
      if (lessonId) {
        await mirrorLesson(lessonId, {
          mediaId,
          contentUrl: null,
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
      objectKey,
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
    if (media?.uid === uid) return true;
    const role = await resolveRole(uid);
    return role === "SuperAdmin";
  }
  const enroll = await dbGet(
    "SELECT uid FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, lesson.track_id],
  );
  if (enroll) return true;
  const role = await resolveRole(uid);
  if (role === "SuperAdmin") return true;
  if (role !== "Mentor" && role !== "SchoolAdmin") return false;
  return sameSchool(await actorSchoolId(uid), await trackSchoolId(lesson.track_id));
}

async function actorSchoolId(uid) {
  try {
    const row = await dbGet("SELECT school_id FROM users_mirror WHERE uid = ?", [
      uid,
    ]);
    return row?.school_id || DEFAULT_SCHOOL_ID;
  } catch {
    return DEFAULT_SCHOOL_ID;
  }
}

async function trackSchoolId(trackId) {
  if (!trackId) return null;
  try {
    const row = await dbGet("SELECT school_id FROM tracks WHERE track_id = ?", [
      trackId,
    ]);
    return row?.school_id || DEFAULT_SCHOOL_ID;
  } catch {
    return DEFAULT_SCHOOL_ID;
  }
}

function sameSchool(a, b) {
  if (!b) return true;
  return (a || DEFAULT_SCHOOL_ID) === (b || DEFAULT_SCHOOL_ID);
}

function badRequest(message, status = 400) {
  const err = new Error(message);
  err.status = status;
  return err;
}

/**
 * Resolve which school a new asset belongs to and validate the scope target.
 * SuperAdmin may target any school; everyone else is pinned to their own.
 */
async function resolveUploadTarget({ uid, role, scope, scopeId, schoolId }) {
  const normalizedRole = normalizeRole(role);
  const ownSchool = await actorSchoolId(uid);
  const requested = String(schoolId || "").trim();
  let targetSchool = ownSchool;
  if (requested && requested !== ownSchool) {
    if (normalizedRole !== "SuperAdmin") {
      throw badRequest("Cannot upload into another school", 403);
    }
    targetSchool = requested;
  }

  const target = { schoolId: targetSchool, scope, scopeId, trackId: null, moduleId: null, lessonId: null };

  if (scope === "lesson") {
    if (!scopeId) throw badRequest("scopeId (lessonId) required for scope=lesson");
    const lesson = await dbGet(
      "SELECT lesson_id, module_id, track_id FROM lessons WHERE lesson_id = ?",
      [scopeId],
    );
    if (!lesson) throw badRequest("Lesson not found", 404);
    target.lessonId = lesson.lesson_id;
    target.moduleId = lesson.module_id;
    target.trackId = lesson.track_id;
    const owning = await trackSchoolId(lesson.track_id);
    if (normalizedRole !== "SuperAdmin" && !sameSchool(targetSchool, owning)) {
      throw badRequest("Lesson belongs to another school", 403);
    }
    target.schoolId = owning || targetSchool;
    return target;
  }

  if (scope === "module") {
    if (!scopeId) throw badRequest("scopeId (moduleId) required for scope=module");
    const mod = await dbGet(
      "SELECT module_id, track_id FROM modules WHERE module_id = ?",
      [scopeId],
    );
    if (!mod) throw badRequest("Module not found", 404);
    target.moduleId = mod.module_id;
    target.trackId = mod.track_id;
    const owning = await trackSchoolId(mod.track_id);
    if (normalizedRole !== "SuperAdmin" && !sameSchool(targetSchool, owning)) {
      throw badRequest("Module belongs to another school", 403);
    }
    target.schoolId = owning || targetSchool;
    return target;
  }

  if (scope === "track") {
    if (!scopeId) throw badRequest("scopeId (trackId) required for scope=track");
    const track = await dbGet(
      "SELECT track_id, school_id FROM tracks WHERE track_id = ?",
      [scopeId],
    );
    if (!track) throw badRequest("Track not found", 404);
    target.trackId = track.track_id;
    const owning = track.school_id || DEFAULT_SCHOOL_ID;
    if (normalizedRole !== "SuperAdmin" && !sameSchool(targetSchool, owning)) {
      throw badRequest("Track belongs to another school", 403);
    }
    target.schoolId = owning;
    return target;
  }

  if (scope === "branding") {
    target.scopeId = scopeId || target.schoolId;
  }
  return target;
}

const MAX_UPLOAD_BYTES = (() => {
  const raw = Number.parseInt(process.env.MEDIA_MAX_UPLOAD_BYTES ?? "", 10);
  return Number.isFinite(raw) && raw > 0 ? raw : 2 * 1024 * 1024 * 1024;
})();

/**
 * Step 1 of direct upload: authorize, reserve a `pending` row, hand back a
 * presigned PUT. Bytes never traverse this API when driver=r2.
 */
export async function createUploadTicket({
  uid,
  role,
  filename,
  contentType,
  sizeBytes,
  scope,
  scopeId,
  schoolId,
  durationSeconds,
}) {
  const effectiveRole = normalizeRole(role || (await resolveRole(uid)));
  if (!canUpload(effectiveRole)) {
    throw badRequest("Only Mentor, SchoolAdmin or SuperAdmin can upload media", 403);
  }
  if (sizeBytes != null && Number(sizeBytes) > MAX_UPLOAD_BYTES) {
    throw badRequest(
      `File exceeds limit of ${MAX_UPLOAD_BYTES} bytes`,
      413,
    );
  }

  const normalizedScope = normalizeScope(scope);
  const target = await resolveUploadTarget({
    uid,
    role: effectiveRole,
    scope: normalizedScope,
    scopeId,
    schoolId,
  });

  const driver = getStorageDriver();
  const mediaId = newMediaId();
  const mime = contentType || "application/octet-stream";
  const objectKey = buildObjectKey({
    schoolId: target.schoolId,
    scope: normalizedScope,
    trackId: target.trackId,
    moduleId: target.moduleId,
    lessonId: target.lessonId,
    mediaId,
    filename,
    contentType: mime,
  });

  const ttl = uploadTtlSeconds();
  const presigned = await driver.presignPut({
    objectKey,
    contentType: mime,
    ttlSeconds: ttl,
  });

  const now = Date.now();
  const storagePath =
    driver.name === "local"
      ? path.join(uploadDir(), objectKey)
      : null;

  await dbRun(
    `INSERT INTO media_assets (
      media_id, uid, school_id, scope, scope_id, track_id, filename, mime_type,
      size_bytes, storage_driver, bucket, object_key, storage_path, public_url,
      checksum, status, duration_sec, finalized_at, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, 'pending', ?, NULL, ?, ?)`,
    [
      mediaId,
      uid,
      target.schoolId,
      normalizedScope,
      target.scopeId || null,
      target.trackId,
      filename || `${mediaId}${extensionFor(filename, mime)}`,
      mime,
      sizeBytes != null ? Number(sizeBytes) : null,
      driver.name,
      driver.bucket,
      objectKey,
      storagePath,
      durationSeconds != null ? Number(durationSeconds) : null,
      now,
      now,
    ],
  );

  if (driver.name === "local" && storagePath) {
    fs.mkdirSync(path.dirname(storagePath), { recursive: true });
  }

  return {
    source: getPrimaryEngine(),
    data: {
      mediaId,
      status: "pending",
      driver: driver.name,
      bucket: driver.bucket,
      objectKey,
      schoolId: target.schoolId,
      scope: normalizedScope,
      scopeId: target.scopeId || null,
      uploadUrl: presigned.url,
      method: presigned.method,
      headers: presigned.headers,
      expiresAt: presigned.expiresAt,
      ttlSeconds: ttl,
    },
  };
}

/**
 * Step 2: confirm the object landed, promote the row to `ready`, and attach it
 * to its lesson. Size/content-type are taken from storage, not the client.
 */
export async function finalizeUpload({ uid, role, mediaId, durationSeconds }) {
  const row = await getMediaFileRow(mediaId);
  if (!row) throw badRequest("Media not found", 404);

  const effectiveRole = normalizeRole(role || (await resolveRole(uid)));
  const isOwner = row.uid === uid;
  if (!isOwner && effectiveRole !== "SuperAdmin") {
    if (effectiveRole !== "SchoolAdmin" && effectiveRole !== "Mentor") {
      throw badRequest("Not allowed to finalize this upload", 403);
    }
    if (!sameSchool(await actorSchoolId(uid), row.school_id)) {
      throw badRequest("Media belongs to another school", 403);
    }
  }

  const driver = getStorageDriver();
  const objectKey = row.object_key;
  if (!objectKey) throw badRequest("Media has no object key to finalize", 409);

  const head = await driver.head({ objectKey });
  const now = Date.now();
  if (!head) {
    await dbRun(
      "UPDATE media_assets SET status = 'failed', updated_at = ? WHERE media_id = ?",
      [now, mediaId],
    );
    throw badRequest("Object not found in storage — upload did not complete", 409);
  }

  const duration =
    durationSeconds != null ? Number(durationSeconds) : row.duration_sec ?? null;

  await dualWrite({
    label: `media:${mediaId}:finalize`,
    writeFn: async () => {
      await dbRun(
        `UPDATE media_assets SET status = 'ready', size_bytes = ?, mime_type = ?,
         checksum = ?, duration_sec = ?, finalized_at = ?, updated_at = ?
         WHERE media_id = ?`,
        [
          head.sizeBytes || row.size_bytes || 0,
          head.contentType || row.mime_type || "application/octet-stream",
          head.checksum || null,
          duration,
          now,
          now,
          mediaId,
        ],
      );
      if (row.scope === "lesson" && row.scope_id) {
        await dbRun(
          `UPDATE lessons SET media_id = ?, content_url = NULL, updated_at = ?
           WHERE lesson_id = ?`,
          [mediaId, now, row.scope_id],
        );
      }
      return mediaId;
    },
    mirrorFn: async () => {
      await mirrorMedia(mediaId, {
        mediaId,
        uid: row.uid,
        schoolId: row.school_id || null,
        scope: row.scope || null,
        scopeId: row.scope_id || null,
        filename: row.filename || null,
        mimeType: head.contentType || row.mime_type || null,
        sizeBytes: head.sizeBytes || row.size_bytes || 0,
        durationSec: duration,
        storageDriver: row.storage_driver || driver.name,
        status: "ready",
        updatedAt: now,
      });
      if (row.scope === "lesson" && row.scope_id) {
        await mirrorLesson(row.scope_id, {
          mediaId,
          contentUrl: null,
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
      driver: row.storage_driver || driver.name,
      sizeBytes: head.sizeBytes || row.size_bytes || 0,
      mimeType: head.contentType || row.mime_type || null,
      durationSec: duration,
      checksum: head.checksum || null,
      lessonId: row.scope === "lesson" ? row.scope_id : null,
    },
  };
}

/**
 * Short-lived playback URL. For driver=r2 this is a presigned GET straight to
 * the private bucket; for driver=local it is an HMAC-signed URL on this API.
 * Legacy rows without an object_key keep the old signed /play route.
 */
export async function resolvePlaybackUrl(mediaId, uid) {
  const row = await getMediaFileRow(mediaId);
  if (!row) throw badRequest("Media not found", 404);
  if (!(await userMayPlayMedia(uid, mediaId))) {
    throw badRequest("Not authorized for this media", 403);
  }
  if (row.status !== "ready") {
    throw badRequest(`Media is ${row.status}`, 409);
  }

  if (!row.object_key) {
    const legacy = playbackUrlFor(mediaId, uid);
    return {
      url: legacy.playbackUrl,
      expiresAt: legacy.playbackExpiresAt,
      driver: "legacy",
      ttlSeconds: 7200,
    };
  }

  const driver = getStorageDriver();
  const ttl = playbackTtlSeconds();
  const signed = await driver.presignGet({
    objectKey: row.object_key,
    ttlSeconds: ttl,
    filename: row.filename,
    contentType: row.mime_type,
  });
  return {
    url: signed.url,
    expiresAt: signed.expiresAt,
    driver: driver.name,
    ttlSeconds: ttl,
    mimeType: row.mime_type || null,
    durationSec: row.duration_sec ?? null,
  };
}
