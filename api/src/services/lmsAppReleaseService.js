import fs from "fs";
import path from "path";
import { ROOT } from "../loadEnv.js";

const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(ROOT, "uploads");
const APK_DIR = path.join(UPLOAD_DIR, "apk");
const APK_FILE = "nelsen-savannah-latest.apk";
const META_FILE = "latest.json";

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
