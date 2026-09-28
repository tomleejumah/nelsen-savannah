import crypto from "crypto";
import fs from "fs";
import path from "path";
import { ROOT } from "../loadEnv.js";
import { publicBaseUrl } from "./lmsMediaService.js";

const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(ROOT, "uploads");
const APK_DIR = path.join(UPLOAD_DIR, "apk");
const APK_FILE = "nelsen-savannah-latest.apk";
const META_FILE = "latest.json";
const APK_TOKEN_SCOPE = "android-apk";
const DEFAULT_TTL_SEC = 15 * 60;

export function apkDir() {
  return APK_DIR;
}

export function apkPath() {
  return path.join(APK_DIR, APK_FILE);
}

export function metaPath() {
  return path.join(APK_DIR, META_FILE);
}

/** Ensure upload dir exists (deploy also creates it). */
export function ensureApkDir() {
  fs.mkdirSync(APK_DIR, { recursive: true });
}

function signingSecret() {
  return (
    process.env.MEDIA_SIGNING_SECRET ||
    process.env.DIDIT_WEBHOOK_SECRET ||
    "lms-dev-media-secret"
  );
}

/**
 * Short-lived HMAC so the browser can GET the APK without an Authorization
 * header — that enables the native download bar (size + %).
 */
export function signAndroidApkToken(uid, ttlSec = DEFAULT_TTL_SEC) {
  const exp = Math.floor(Date.now() / 1000) + ttlSec;
  const payload = `${APK_TOKEN_SCOPE}.${uid}.${exp}`;
  const sig = crypto
    .createHmac("sha256", signingSecret())
    .update(payload)
    .digest("hex");
  return { token: `${exp}.${sig}`, expiresAt: exp };
}

export function verifyAndroidApkToken(uid, token) {
  if (!token || typeof token !== "string" || !uid) return false;
  const [expStr, sig] = token.split(".");
  const exp = Number(expStr);
  if (!exp || !sig || exp < Math.floor(Date.now() / 1000)) return false;
  const payload = `${APK_TOKEN_SCOPE}.${uid}.${exp}`;
  const expected = crypto
    .createHmac("sha256", signingSecret())
    .update(payload)
    .digest("hex");
  try {
    return crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected));
  } catch {
    return false;
  }
}

export function androidApkDownloadUrlFor(uid, ttlSec = DEFAULT_TTL_SEC) {
  const { token, expiresAt } = signAndroidApkToken(uid, ttlSec);
  const url =
    `${publicBaseUrl()}/lms/app/android/file` +
    `?uid=${encodeURIComponent(uid)}&token=${encodeURIComponent(token)}`;
  return { url, expiresAt };
}

/**
 * @returns {{
 *   available: boolean,
 *   fileName?: string,
 *   versionName?: string,
 *   versionCode?: number,
 *   sizeBytes?: number,
 *   sha256?: string,
 *   updatedAt?: string,
 *   gitSha?: string,
 *   runId?: string,
 * }}
 */
export function getAndroidReleaseMeta() {
  ensureApkDir();
  const file = apkPath();
  const metaFile = metaPath();
  if (!fs.existsSync(file)) {
    return { available: false };
  }
  let meta = {};
  if (fs.existsSync(metaFile)) {
    try {
      meta = JSON.parse(fs.readFileSync(metaFile, "utf8"));
    } catch {
      meta = {};
    }
  }
  const st = fs.statSync(file);
  return {
    available: true,
    fileName: meta.fileName || APK_FILE,
    versionName: meta.versionName || "unknown",
    versionCode:
      meta.versionCode != null ? Number(meta.versionCode) : undefined,
    sizeBytes: meta.sizeBytes != null ? Number(meta.sizeBytes) : st.size,
    sha256: meta.sha256 || undefined,
    updatedAt: meta.updatedAt || st.mtime.toISOString(),
    gitSha: meta.gitSha || undefined,
    runId: meta.runId || undefined,
  };
}

export function openAndroidApkStream() {
  const file = apkPath();
  if (!fs.existsSync(file)) return null;
  return {
    stream: fs.createReadStream(file),
    size: fs.statSync(file).size,
    fileName: APK_FILE,
  };
}
