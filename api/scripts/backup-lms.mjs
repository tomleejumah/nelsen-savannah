#!/usr/bin/env node
/**
 * Create a recoverable pre-deploy snapshot of the production sql.js database.
 * No npm dependencies: safe to run BEFORE npm ci / install / API shutdown.
 *
 * Usage: node scripts/backup-lms.mjs /home/server/Apis/nelsen-savannah
 * Set LMS_BACKUP_RETENTION (default 14); LMS_DATA_DIR overrides the data folder.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const appDir = path.resolve(process.argv[2] || path.dirname(fileURLToPath(import.meta.url)) + "/../");
const envPath = path.join(appDir, ".env");
const envText = fs.existsSync(envPath) ? fs.readFileSync(envPath, "utf8") : "";
function envValue(name) {
  if (process.env[name] != null) return process.env[name];
  const match = envText.match(new RegExp("^\\s*" + name + "=([^\\r\\n]*)", "m"));
  return match ? match[1].trim().replace(/^['"]|['"]$/g, "") : "";
}
// Paths in .env are relative to the API installation, not the checkout
// workspace from which the deployment job happened to invoke this script.
const dataDir = path.resolve(appDir, envValue("LMS_DATA_DIR") || "data");
const source = path.join(dataDir, "lms.sqlite");
const destinationDir = path.join(dataDir, "backups");
const retention = Math.max(1, Math.min(90, Number(envValue("LMS_BACKUP_RETENTION")) || 14));
const postgres = Boolean(envValue("DATABASE_URL"));

if (postgres) {
  console.log("[lms-backup] PostgreSQL configured: SQLite snapshot skipped; use pg_dump separately.");
  process.exit(0);
}

if (!fs.existsSync(source)) {
  const historical = fs.existsSync(destinationDir)
    ? fs.readdirSync(destinationDir).some((file) => /^lms-.*\.sqlite$/.test(file))
    : false;
  if (historical) {
    console.error("[lms-backup] CRITICAL: live SQLite missing but backups exist. Aborting deploy.");
    process.exit(1);
  }
  console.log("[lms-backup] No SQLite file (first install). Nothing to back up.");
  process.exit(0);
}
const stat = fs.statSync(source);
const header = Buffer.alloc(16);
const descriptor = fs.openSync(source, "r");
try { fs.readSync(descriptor, header, 0, header.length, 0); }
finally { fs.closeSync(descriptor); }
if (!stat.isFile() || stat.size < 100 || header.toString("utf8") !== "SQLite format 3\u0000") {
  console.error("[lms-backup] CRITICAL: database is empty or has an invalid SQLite header.");
  process.exit(1);
}

fs.mkdirSync(destinationDir, { recursive: true, mode: 0o700 });
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const target = path.join(destinationDir, `lms-${stamp}-${process.pid}.sqlite`);
fs.copyFileSync(source, target, fs.constants.COPYFILE_EXCL);
fs.chmodSync(target, 0o600);
const written = fs.statSync(target);
if (written.size !== stat.size) {
  console.error("[lms-backup] CRITICAL: backup size mismatch.");
  process.exit(1);
}
const files = fs.readdirSync(destinationDir)
  .filter((name) => /^lms-.*\.sqlite$/.test(name))
  .sort().reverse();
for (const old of files.slice(retention)) fs.unlinkSync(path.join(destinationDir, old));
console.log(`[lms-backup] Snapshot: ${target} (${written.size} bytes). Retained ${Math.min(files.length, retention)}.`);
