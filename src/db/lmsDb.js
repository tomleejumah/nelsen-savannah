/**
 * LMS primary DB — Postgres if DATABASE_URL is set, else SQLite at data/lms.sqlite.
 * Documented choice is logged at startup via getPrimaryEngine().
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import Database from "better-sqlite3";
import pg from "pg";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");

let engine = null; // "postgres" | "sqlite"
let sqlite = null;
let pgPool = null;

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

const SQLITE_SCHEMA = `
CREATE TABLE IF NOT EXISTS users_mirror (
  uid TEXT PRIMARY KEY,
  email TEXT,
  display_name TEXT,
  first_name TEXT,
  last_name TEXT,
  photo_url TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS roles (
  uid TEXT PRIMARY KEY,
  role TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (uid) REFERENCES users_mirror(uid)
);

CREATE TABLE IF NOT EXISTS tracks (
  track_id TEXT PRIMARY KEY,
  program_slug TEXT,
  title TEXT NOT NULL,
  does TEXT,
  course_image_url TEXT,
  tutor_id TEXT,
  tutor_name TEXT,
  tutor_avatar_url TEXT,
  duration TEXT,
  audience_json TEXT,
  sort_order INTEGER DEFAULT 0,
  published INTEGER DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS modules (
  module_id TEXT PRIMARY KEY,
  track_id TEXT NOT NULL,
  title TEXT NOT NULL,
  does TEXT,
  estimated_minutes INTEGER DEFAULT 0,
  sort_order INTEGER DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (track_id) REFERENCES tracks(track_id)
);

CREATE TABLE IF NOT EXISTS lessons (
  lesson_id TEXT PRIMARY KEY,
  module_id TEXT NOT NULL,
  track_id TEXT NOT NULL,
  title TEXT NOT NULL,
  does TEXT,
  type TEXT NOT NULL,
  estimated_minutes INTEGER DEFAULT 0,
  has_quiz INTEGER DEFAULT 0,
  has_assignment INTEGER DEFAULT 0,
  content_url TEXT,
  media_id TEXT,
  sort_order INTEGER DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (module_id) REFERENCES modules(module_id),
  FOREIGN KEY (track_id) REFERENCES tracks(track_id)
);

CREATE TABLE IF NOT EXISTS enrollments (
  uid TEXT NOT NULL,
  track_id TEXT NOT NULL,
  role TEXT,
  status TEXT NOT NULL,
  enrolled_at INTEGER NOT NULL,
  last_active_at INTEGER NOT NULL,
  track_percent INTEGER DEFAULT 0,
  modules_completed INTEGER DEFAULT 0,
  modules_total INTEGER DEFAULT 0,
  lessons_completed INTEGER DEFAULT 0,
  lessons_total INTEGER DEFAULT 0,
  mentor_id TEXT,
  platform TEXT,
  PRIMARY KEY (uid, track_id),
  FOREIGN KEY (uid) REFERENCES users_mirror(uid),
  FOREIGN KEY (track_id) REFERENCES tracks(track_id)
);

CREATE TABLE IF NOT EXISTS progress (
  uid TEXT NOT NULL,
  lesson_id TEXT NOT NULL,
  module_id TEXT NOT NULL,
  track_id TEXT NOT NULL,
  opened INTEGER DEFAULT 0,
  content_pct INTEGER DEFAULT 0,
  quiz_pct INTEGER DEFAULT 0,
  assignment_pct INTEGER DEFAULT 0,
  lesson_percent INTEGER DEFAULT 0,
  status TEXT,
  last_platform TEXT,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (uid, lesson_id),
  FOREIGN KEY (uid) REFERENCES users_mirror(uid),
  FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
);

CREATE TABLE IF NOT EXISTS submissions (
  submission_id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  lesson_id TEXT NOT NULL,
  track_id TEXT NOT NULL,
  module_id TEXT,
  body TEXT,
  media_urls_json TEXT,
  status TEXT NOT NULL,
  score INTEGER,
  feedback TEXT,
  mentor_id TEXT,
  submitted_at INTEGER NOT NULL,
  marked_at INTEGER,
  FOREIGN KEY (uid) REFERENCES users_mirror(uid),
  FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
);

CREATE TABLE IF NOT EXISTS certificates (
  uid TEXT NOT NULL,
  track_id TEXT NOT NULL,
  issued_at INTEGER NOT NULL,
  cert_url TEXT,
  track_percent INTEGER,
  PRIMARY KEY (uid, track_id),
  FOREIGN KEY (uid) REFERENCES users_mirror(uid),
  FOREIGN KEY (track_id) REFERENCES tracks(track_id)
);

CREATE TABLE IF NOT EXISTS media_assets (
  media_id TEXT PRIMARY KEY,
  uid TEXT,
  filename TEXT,
  mime_type TEXT,
  size_bytes INTEGER,
  storage_path TEXT,
  public_url TEXT,
  status TEXT NOT NULL,
  duration_sec INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS track_likes (
  uid TEXT NOT NULL,
  track_id TEXT NOT NULL,
  liked INTEGER DEFAULT 1,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (uid, track_id)
);
`;

const PG_SCHEMA = `
CREATE TABLE IF NOT EXISTS users_mirror (
  uid TEXT PRIMARY KEY,
  email TEXT,
  display_name TEXT,
  first_name TEXT,
  last_name TEXT,
  photo_url TEXT,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS roles (
  uid TEXT PRIMARY KEY REFERENCES users_mirror(uid),
  role TEXT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS tracks (
  track_id TEXT PRIMARY KEY,
  program_slug TEXT,
  title TEXT NOT NULL,
  does TEXT,
  course_image_url TEXT,
  tutor_id TEXT,
  tutor_name TEXT,
  tutor_avatar_url TEXT,
  duration TEXT,
  audience_json TEXT,
  sort_order INTEGER DEFAULT 0,
  published INTEGER DEFAULT 1,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS modules (
  module_id TEXT PRIMARY KEY,
  track_id TEXT NOT NULL REFERENCES tracks(track_id),
  title TEXT NOT NULL,
  does TEXT,
  estimated_minutes INTEGER DEFAULT 0,
  sort_order INTEGER DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS lessons (
  lesson_id TEXT PRIMARY KEY,
  module_id TEXT NOT NULL REFERENCES modules(module_id),
  track_id TEXT NOT NULL REFERENCES tracks(track_id),
  title TEXT NOT NULL,
  does TEXT,
  type TEXT NOT NULL,
  estimated_minutes INTEGER DEFAULT 0,
  has_quiz INTEGER DEFAULT 0,
  has_assignment INTEGER DEFAULT 0,
  content_url TEXT,
  media_id TEXT,
  sort_order INTEGER DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS enrollments (
  uid TEXT NOT NULL REFERENCES users_mirror(uid),
  track_id TEXT NOT NULL REFERENCES tracks(track_id),
  role TEXT,
  status TEXT NOT NULL,
  enrolled_at BIGINT NOT NULL,
  last_active_at BIGINT NOT NULL,
  track_percent INTEGER DEFAULT 0,
  modules_completed INTEGER DEFAULT 0,
  modules_total INTEGER DEFAULT 0,
  lessons_completed INTEGER DEFAULT 0,
  lessons_total INTEGER DEFAULT 0,
  mentor_id TEXT,
  platform TEXT,
  PRIMARY KEY (uid, track_id)
);

CREATE TABLE IF NOT EXISTS progress (
  uid TEXT NOT NULL REFERENCES users_mirror(uid),
  lesson_id TEXT NOT NULL REFERENCES lessons(lesson_id),
  module_id TEXT NOT NULL,
  track_id TEXT NOT NULL,
  opened INTEGER DEFAULT 0,
  content_pct INTEGER DEFAULT 0,
  quiz_pct INTEGER DEFAULT 0,
  assignment_pct INTEGER DEFAULT 0,
  lesson_percent INTEGER DEFAULT 0,
  status TEXT,
  last_platform TEXT,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (uid, lesson_id)
);

CREATE TABLE IF NOT EXISTS submissions (
  submission_id TEXT PRIMARY KEY,
  uid TEXT NOT NULL REFERENCES users_mirror(uid),
  lesson_id TEXT NOT NULL REFERENCES lessons(lesson_id),
  track_id TEXT NOT NULL,
  module_id TEXT,
  body TEXT,
  media_urls_json TEXT,
  status TEXT NOT NULL,
  score INTEGER,
  feedback TEXT,
  mentor_id TEXT,
  submitted_at BIGINT NOT NULL,
  marked_at BIGINT
);

CREATE TABLE IF NOT EXISTS certificates (
  uid TEXT NOT NULL REFERENCES users_mirror(uid),
  track_id TEXT NOT NULL REFERENCES tracks(track_id),
  issued_at BIGINT NOT NULL,
  cert_url TEXT,
  track_percent INTEGER,
  PRIMARY KEY (uid, track_id)
);

CREATE TABLE IF NOT EXISTS media_assets (
  media_id TEXT PRIMARY KEY,
  uid TEXT,
  filename TEXT,
  mime_type TEXT,
  size_bytes BIGINT,
  storage_path TEXT,
  public_url TEXT,
  status TEXT NOT NULL,
  duration_sec INTEGER,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS track_likes (
  uid TEXT NOT NULL,
  track_id TEXT NOT NULL,
  liked INTEGER DEFAULT 1,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (uid, track_id)
);
`;

async function initPostgres(databaseUrl) {
  pgPool = new pg.Pool({ connectionString: databaseUrl });
  await pgPool.query(PG_SCHEMA);
  engine = "postgres";
}

function initSqlite() {
  const dataDir = process.env.LMS_DATA_DIR || path.join(ROOT, "data");
  ensureDir(dataDir);
  const dbPath = path.join(dataDir, "lms.sqlite");
  sqlite = new Database(dbPath);
  sqlite.pragma("journal_mode = WAL");
  sqlite.pragma("foreign_keys = ON");
  sqlite.exec(SQLITE_SCHEMA);
  engine = "sqlite";
}

/**
 * Initialize primary store. Tries Postgres when DATABASE_URL is set;
 * on auth/connect failure falls back to SQLite (documented for VPS).
 */
export async function initLmsDb() {
  const databaseUrl = process.env.DATABASE_URL?.trim();
  if (databaseUrl) {
    try {
      await initPostgres(databaseUrl);
      console.log(`[lms-db] primary=postgres (DATABASE_URL)`);
      return engine;
    } catch (err) {
      console.warn(
        `[lms-db] Postgres unavailable (${err.message}); falling back to SQLite`,
      );
    }
  }
  initSqlite();
  const dataDir = process.env.LMS_DATA_DIR || path.join(ROOT, "data");
  console.log(`[lms-db] primary=sqlite path=${path.join(dataDir, "lms.sqlite")}`);
  return engine;
}

export function getPrimaryEngine() {
  return engine;
}

export function isDbReady() {
  return engine === "sqlite" ? !!sqlite : !!pgPool;
}

/** Run a SELECT that returns one row or null */
export async function dbGet(sql, params = []) {
  if (engine === "sqlite") {
    return sqlite.prepare(sql).get(...params) ?? null;
  }
  const res = await pgPool.query(toPg(sql), params);
  return res.rows[0] ?? null;
}

/** Run a SELECT that returns many rows */
export async function dbAll(sql, params = []) {
  if (engine === "sqlite") {
    return sqlite.prepare(sql).all(...params);
  }
  const res = await pgPool.query(toPg(sql), params);
  return res.rows;
}

/** Run INSERT/UPDATE/DELETE */
export async function dbRun(sql, params = []) {
  if (engine === "sqlite") {
    return sqlite.prepare(sql).run(...params);
  }
  return pgPool.query(toPg(sql), params);
}

/** Convert ? placeholders to $1, $2 for Postgres */
function toPg(sql) {
  let i = 0;
  return sql.replace(/\?/g, () => `$${++i}`);
}

export async function checkPrimaryHealth() {
  try {
    await dbGet("SELECT 1 AS ok");
    return { ok: true, engine };
  } catch (err) {
    return { ok: false, engine, error: err.message };
  }
}
