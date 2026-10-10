#!/usr/bin/env node
/**
 * Read-only post-deploy SQLite integrity and catalog-loss guard.
 * Run AFTER npm installation, before declaring the API deployment healthy.
 */
import fs from "node:fs";
import path from "node:path";
import initSqlJs from "sql.js";

const appDir = path.resolve(process.argv[2] || ".");
const envText = fs.existsSync(path.join(appDir, ".env"))
  ? fs.readFileSync(path.join(appDir, ".env"), "utf8") : "";
function envValue(name) {
  if (process.env[name] != null) return process.env[name];
  const match = envText.match(new RegExp("^\\s*" + name + "=([^\\r\\n]*)", "m"));
  return match ? match[1].trim().replace(/^['"]|['"]$/g, "") : "";
}
if (envValue("DATABASE_URL")) {
  console.log("[lms-db-check] PostgreSQL configured; use PostgreSQL backups/monitoring.");
  process.exit(0);
}
const dataDir = path.resolve(envValue("LMS_DATA_DIR") || path.join(appDir, "data"));
const file = path.join(dataDir, "lms.sqlite");
if (!fs.existsSync(file)) {
  console.error("[lms-db-check] SQLite file missing after startup.");
  process.exit(1);
}
const SQL = await initSqlJs();
const watched = ["schools", "tracks", "modules", "lessons", "enrollments"];
function snapshot(dbFile) {
  const db = new SQL.Database(fs.readFileSync(dbFile));
  try {
    const integrity = db.exec("PRAGMA integrity_check");
    if (integrity[0]?.values?.[0]?.[0] !== "ok") {
      throw new Error(`SQLite integrity_check failed for ${dbFile}`);
    }
    const tables = new Set((db.exec("SELECT name FROM sqlite_master WHERE type = 'table'")[0]?.values || [])
      .map((row) => row[0]));
    return Object.fromEntries(watched.map((name) => {
      if (!tables.has(name)) throw new Error(`Missing critical SQLite table: ${name}`);
      return [name, db.exec(`SELECT COUNT(*) FROM "${name}"`)[0]?.values?.[0]?.[0] || 0];
    }));
  } finally {
    db.close();
  }
}
const current = snapshot(file);
console.log("[lms-db-check] current counts:", JSON.stringify(current));
const backupDir = path.join(dataDir, "backups");
const backups = fs.existsSync(backupDir)
  ? fs.readdirSync(backupDir).filter((name) => /^lms-.*\.sqlite$/.test(name)).sort().reverse()
  : [];
if (backups.length && envValue("LMS_ALLOW_DATA_DECREASE") !== "1") {
  const previous = snapshot(path.join(backupDir, backups[0]));
  for (const table of watched) {
    if (current[table] < previous[table]) {
      throw new Error(`Possible data loss: ${table} has ${current[table]} rows; pre-deploy snapshot had ${previous[table]}. Deployment must be investigated.`);
    }
  }
}
for (const table of watched) {
  const minimum = Number(envValue(`LMS_MIN_${table.toUpperCase()}`) || 0);
  if (current[table] < minimum) {
    throw new Error(`Required minimum ${table} count is ${minimum}, found ${current[table]}`);
  }
}
console.log("[lms-db-check] integrity and catalog safeguards passed.");
