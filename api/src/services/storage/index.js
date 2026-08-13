/**
 * Object storage driver selection + tenant-scoped key layout.
 *
 * MEDIA_STORAGE_DRIVER=r2   → S3-compatible presigned PUT/GET (R2, S3, B2, MinIO)
 * MEDIA_STORAGE_DRIVER=local → disk under UPLOAD_DIR, presigned through this API
 */

import { createLocalDriver } from "./localDriver.js";
import { createS3Driver } from "./r2Driver.js";

export const MEDIA_SCOPES = Object.freeze([
  "track",
  "module",
  "lesson",
  "branding",
  "misc",
]);

const DEFAULT_UPLOAD_TTL = 3600;
const DEFAULT_PLAYBACK_TTL = 3600;
const MAX_TTL = 7 * 24 * 3600;

let cached = null;

function intEnv(name, fallback) {
  const raw = Number.parseInt(process.env[name] ?? "", 10);
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
}

export function uploadTtlSeconds() {
  return Math.min(intEnv("MEDIA_UPLOAD_TTL_SECONDS", DEFAULT_UPLOAD_TTL), MAX_TTL);
}

export function playbackTtlSeconds() {
  return Math.min(
    intEnv("MEDIA_PLAYBACK_TTL_SECONDS", DEFAULT_PLAYBACK_TTL),
    MAX_TTL,
  );
}

export function configuredDriverName() {
  const raw = (process.env.MEDIA_STORAGE_DRIVER || "").trim().toLowerCase();
  if (raw === "r2" || raw === "s3") return "r2";
  if (raw === "local" || raw === "") return "local";
  return "local";
}

/**
 * Resolve the active driver. Falls back to local when r2 is selected but
 * credentials are incomplete, so a misconfigured deploy still serves media.
 */
export function getStorageDriver() {
  if (cached) return cached;
  if (configuredDriverName() === "r2") {
    try {
      cached = createS3Driver();
      console.log(
        `[media-storage] driver=r2 bucket=${cached.bucket} endpoint=${cached.endpoint}`,
      );
      return cached;
    } catch (err) {
      console.warn(
        `[media-storage] r2 selected but unusable (${err.message}); falling back to local disk`,
      );
    }
  }
  cached = createLocalDriver();
  console.log(`[media-storage] driver=local root=${cached.root}`);
  return cached;
}

export function resetStorageDriver() {
  cached = null;
}

export function normalizeScope(scope) {
  const s = String(scope || "").trim().toLowerCase();
  return MEDIA_SCOPES.includes(s) ? s : "misc";
}

function safeSegment(value, fallback) {
  const cleaned = String(value ?? "")
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 96);
  return cleaned || fallback;
}

export function extensionFor(filename, contentType) {
  const fromName = String(filename || "").match(/\.([A-Za-z0-9]{1,8})$/);
  if (fromName) return `.${fromName[1].toLowerCase()}`;
  const map = {
    "video/mp4": ".mp4",
    "video/webm": ".webm",
    "video/quicktime": ".mov",
    "audio/mpeg": ".mp3",
    "audio/mp4": ".m4a",
    "application/pdf": ".pdf",
    "image/png": ".png",
    "image/jpeg": ".jpg",
    "image/webp": ".webp",
    "application/vnd.apple.mpegurl": ".m3u8",
  };
  return map[String(contentType || "").toLowerCase()] || "";
}

/**
 * Tenant prefix comes first so a single key prefix can be granted, listed,
 * lifecycle-ruled or purged per school without touching another tenant.
 * The basename is the opaque mediaId — the learner's original filename never
 * reaches the bucket, so keys cannot be guessed from a course title.
 */
export function buildObjectKey({
  schoolId,
  scope,
  trackId,
  moduleId,
  lessonId,
  mediaId,
  filename,
  contentType,
}) {
  const school = safeSegment(schoolId, "unassigned");
  const ext = extensionFor(filename, contentType);
  const base = `${mediaId}${ext}`;
  const s = normalizeScope(scope);

  if (s === "lesson") {
    return `schools/${school}/tracks/${safeSegment(trackId, "unassigned")}/lessons/${safeSegment(lessonId, mediaId)}/${base}`;
  }
  if (s === "module") {
    return `schools/${school}/tracks/${safeSegment(trackId, "unassigned")}/modules/${safeSegment(moduleId, mediaId)}/${base}`;
  }
  if (s === "track") {
    return `schools/${school}/tracks/${safeSegment(trackId, mediaId)}/${base}`;
  }
  if (s === "branding") {
    return `schools/${school}/branding/${base}`;
  }
  return `schools/${school}/misc/${base}`;
}
