/**
 * Disk driver — dev default and fallback for the current self-hosted deploy.
 * Mirrors the S3 contract: the client still does a raw PUT then a GET against a
 * time-limited URL, except the URL points at this API and is HMAC-signed
 * instead of SigV4-signed.
 */

import crypto from "crypto";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../../..");

function signingSecret() {
  return (
    process.env.MEDIA_SIGNING_SECRET ||
    process.env.DIDIT_WEBHOOK_SECRET ||
    "lms-dev-media-secret"
  );
}

function publicBase() {
  return (process.env.PUBLIC_BASE_URL || "http://localhost:5002").replace(
    /\/$/,
    "",
  );
}

export function localUploadRoot() {
  const raw = process.env.UPLOAD_DIR || path.join(ROOT, "uploads");
  return path.isAbsolute(raw) ? raw : path.resolve(ROOT, raw);
}

function sign(objectKey, mode, exp) {
  return crypto
    .createHmac("sha256", signingSecret())
    .update(`${objectKey}.${mode}.${exp}`)
    .digest("hex");
}

export function verifyLocalBlobSignature({ objectKey, mode, exp, sig }) {
  const expNum = Number(exp);
  if (!objectKey || !sig || !expNum) return false;
  if (expNum < Math.floor(Date.now() / 1000)) return false;
  const expected = sign(objectKey, mode, expNum);
  try {
    return crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected));
  } catch {
    return false;
  }
}

/** Resolve an object key to a path guaranteed to stay inside UPLOAD_DIR. */
export function resolveLocalPath(objectKey) {
  const root = localUploadRoot();
  const abs = path.resolve(root, objectKey);
  const rel = path.relative(root, abs);
  if (rel.startsWith("..") || path.isAbsolute(rel)) {
    const err = new Error("Invalid object key");
    err.status = 400;
    throw err;
  }
  return abs;
}

function blobUrl(objectKey, mode, ttlSeconds) {
  const exp = Math.floor(Date.now() / 1000) + ttlSeconds;
  const qs = new URLSearchParams({
    key: objectKey,
    mode,
    exp: String(exp),
    sig: sign(objectKey, mode, exp),
  });
  return { url: `${publicBase()}/lms/media/blob?${qs.toString()}`, expiresAt: exp };
}

export function createLocalDriver() {
  return {
    name: "local",
    bucket: null,
    root: localUploadRoot(),

    async presignPut({ objectKey, contentType, ttlSeconds }) {
      const { url, expiresAt } = blobUrl(objectKey, "put", ttlSeconds);
      return {
        url,
        method: "PUT",
        headers: { "Content-Type": contentType || "application/octet-stream" },
        expiresAt,
      };
    },

    async presignGet({ objectKey, ttlSeconds }) {
      return blobUrl(objectKey, "get", ttlSeconds);
    },

    async head({ objectKey }) {
      const abs = resolveLocalPath(objectKey);
      if (!fs.existsSync(abs)) return null;
      const stat = fs.statSync(abs);
      if (!stat.isFile()) return null;
      return { sizeBytes: stat.size, contentType: null, checksum: null };
    },

    async remove({ objectKey }) {
      const abs = resolveLocalPath(objectKey);
      await fs.promises.rm(abs, { force: true });
    },
  };
}
