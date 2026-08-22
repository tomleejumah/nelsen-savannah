/**
 * LMS primary DB — Postgres if DATABASE_URL is set, else SQLite via sql.js
 * (WASM — no native addon; better-sqlite3 segfaults on the VPS Node 20 host).
 */

import fs from "fs";
import path from "path";
import { createRequire } from "module";
import { fileURLToPath } from "url";
import initSqlJs from "sql.js";
import pg from "pg";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
// resolve("sql.js") → …/dist/sql-wasm.js
const sqlJsDist = path.dirname(require.resolve("sql.js"));

let engine = null; // "postgres" | "sqlite"
let sqlite = null;
let sqlitePath = null;
let pgPool = null;

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function persistSqlite() {
  if (!sqlite || !sqlitePath) return;
  const data = sqlite.export();
  fs.writeFileSync(sqlitePath, Buffer.from(data));
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
  school_id TEXT,
  scope TEXT,
  scope_id TEXT,
  track_id TEXT,
  filename TEXT,
  mime_type TEXT,
  size_bytes INTEGER,
  storage_driver TEXT,
  bucket TEXT,
  object_key TEXT,
  storage_path TEXT,
  public_url TEXT,
  checksum TEXT,
  status TEXT NOT NULL,
  duration_sec INTEGER,
  finalized_at INTEGER,
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

CREATE TABLE IF NOT EXISTS schools (
  school_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS assignments (
  assignment_id TEXT PRIMARY KEY,
  school_id TEXT,
  track_id TEXT,
  lesson_id TEXT,
  title TEXT NOT NULL,
  prompt TEXT,
  assigned_by TEXT NOT NULL,
  assignee_uid TEXT,
  cohort TEXT,
  due_at INTEGER,
  created_at INTEGER NOT NULL
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
  school_id TEXT,
  scope TEXT,
  scope_id TEXT,
  track_id TEXT,
  filename TEXT,
  mime_type TEXT,
  size_bytes BIGINT,
  storage_driver TEXT,
  bucket TEXT,
  object_key TEXT,
  storage_path TEXT,
  public_url TEXT,
  checksum TEXT,
  status TEXT NOT NULL,
  duration_sec INTEGER,
  finalized_at BIGINT,
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

CREATE TABLE IF NOT EXISTS schools (
  school_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS assignments (
  assignment_id TEXT PRIMARY KEY,
  school_id TEXT,
  track_id TEXT,
  lesson_id TEXT,
  title TEXT NOT NULL,
  prompt TEXT,
  assigned_by TEXT NOT NULL,
  assignee_uid TEXT,
  cohort TEXT,
  due_at BIGINT,
  created_at BIGINT NOT NULL
);
`;

/** Additive columns for existing DBs (CREATE TABLE IF NOT EXISTS won't alter). */
async function ensureMigrations() {
  const alters = [
    "ALTER TABLE users_mirror ADD COLUMN school_id TEXT",
    "ALTER TABLE users_mirror ADD COLUMN active_school_id TEXT",
    "ALTER TABLE submissions ADD COLUMN assignment_id TEXT",
    "ALTER TABLE tracks ADD COLUMN school_id TEXT",
    "ALTER TABLE enrollments ADD COLUMN school_id TEXT",
    "ALTER TABLE schools ADD COLUMN logo_url TEXT",
    "ALTER TABLE schools ADD COLUMN accent_color TEXT",
    "ALTER TABLE schools ADD COLUMN branding_json TEXT",
    "ALTER TABLE media_assets ADD COLUMN school_id TEXT",
    "ALTER TABLE media_assets ADD COLUMN scope TEXT",
    "ALTER TABLE media_assets ADD COLUMN scope_id TEXT",
    "ALTER TABLE media_assets ADD COLUMN track_id TEXT",
    "ALTER TABLE media_assets ADD COLUMN storage_driver TEXT",
    "ALTER TABLE media_assets ADD COLUMN bucket TEXT",
    "ALTER TABLE media_assets ADD COLUMN object_key TEXT",
    "ALTER TABLE media_assets ADD COLUMN checksum TEXT",
    engine === "postgres"
      ? "ALTER TABLE media_assets ADD COLUMN finalized_at BIGINT"
      : "ALTER TABLE media_assets ADD COLUMN finalized_at INTEGER",
    "ALTER TABLE assignments ADD COLUMN model_answer TEXT",
    engine === "postgres"
      ? "ALTER TABLE milestones ADD COLUMN due_at BIGINT"
      : "ALTER TABLE milestones ADD COLUMN due_at INTEGER",
    "ALTER TABLE milestones ADD COLUMN requires_previous_completion INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE event_reservations ADD COLUMN uid TEXT",
  ];
  for (const sql of alters) {
    try {
      await dbRun(sql);
    } catch {
      /* column already exists */
    }
  }

  const additiveTables = [
    `CREATE TABLE IF NOT EXISTS cohorts (
      cohort_id TEXT PRIMARY KEY,
      school_id TEXT NOT NULL,
      name TEXT NOT NULL,
      status TEXT NOT NULL,
      starts_at BIGINT,
      ends_at BIGINT,
      created_by TEXT NOT NULL,
      created_at BIGINT NOT NULL,
      updated_at BIGINT NOT NULL,
      FOREIGN KEY (school_id) REFERENCES schools(school_id)
    )`,
    `CREATE TABLE IF NOT EXISTS cohort_members (
      cohort_id TEXT NOT NULL,
      uid TEXT NOT NULL,
      role TEXT NOT NULL,
      joined_at BIGINT NOT NULL,
      PRIMARY KEY (cohort_id, uid),
      FOREIGN KEY (cohort_id) REFERENCES cohorts(cohort_id),
      FOREIGN KEY (uid) REFERENCES users_mirror(uid)
    )`,
    `CREATE TABLE IF NOT EXISTS cohort_track_runs (
      run_id TEXT PRIMARY KEY,
      cohort_id TEXT NOT NULL,
      track_id TEXT NOT NULL,
      starts_at BIGINT,
      ends_at BIGINT,
      created_by TEXT NOT NULL,
      created_at BIGINT NOT NULL,
      FOREIGN KEY (cohort_id) REFERENCES cohorts(cohort_id),
      FOREIGN KEY (track_id) REFERENCES tracks(track_id)
    )`,
    `CREATE TABLE IF NOT EXISTS milestones (
      milestone_id TEXT PRIMARY KEY,
      run_id TEXT NOT NULL,
      lesson_id TEXT NOT NULL,
      title TEXT,
      release_at BIGINT NOT NULL,
      due_at BIGINT,
      requires_previous_completion INTEGER NOT NULL DEFAULT 1,
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at BIGINT NOT NULL,
      updated_at BIGINT NOT NULL,
      FOREIGN KEY (run_id) REFERENCES cohort_track_runs(run_id),
      FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
    )`,
    `CREATE TABLE IF NOT EXISTS quizzes (
      quiz_id TEXT PRIMARY KEY,
      lesson_id TEXT NOT NULL UNIQUE,
      current_version INTEGER NOT NULL,
      created_at BIGINT NOT NULL,
      updated_at BIGINT NOT NULL,
      FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
    )`,
    `CREATE TABLE IF NOT EXISTS quiz_versions (
      quiz_id TEXT NOT NULL,
      version INTEGER NOT NULL,
      prompt TEXT NOT NULL,
      options_json TEXT NOT NULL,
      correct_option_id TEXT NOT NULL,
      passing_score INTEGER NOT NULL DEFAULT 80,
      created_by TEXT NOT NULL,
      created_at BIGINT NOT NULL,
      PRIMARY KEY (quiz_id, version),
      FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id)
    )`,
    `CREATE TABLE IF NOT EXISTS quiz_attempts (
      attempt_id TEXT PRIMARY KEY,
      quiz_id TEXT NOT NULL,
      quiz_version INTEGER NOT NULL,
      lesson_id TEXT NOT NULL,
      uid TEXT NOT NULL,
      selected_option_id TEXT NOT NULL,
      score INTEGER NOT NULL,
      passed INTEGER NOT NULL,
      submitted_at BIGINT NOT NULL,
      FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id),
      FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id),
      FOREIGN KEY (uid) REFERENCES users_mirror(uid)
    )`,
    `CREATE TABLE IF NOT EXISTS cohort_quiz_versions (
      run_id TEXT NOT NULL,
      lesson_id TEXT NOT NULL,
      version INTEGER NOT NULL,
      prompt TEXT NOT NULL,
      options_json TEXT NOT NULL,
      correct_option_id TEXT NOT NULL,
      passing_score INTEGER NOT NULL DEFAULT 80,
      created_by TEXT NOT NULL,
      created_at BIGINT NOT NULL,
      PRIMARY KEY (run_id, lesson_id, version),
      FOREIGN KEY (run_id) REFERENCES cohort_track_runs(run_id),
      FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
    )`,
    `CREATE TABLE IF NOT EXISTS cohort_quiz_attempts (
      attempt_id TEXT PRIMARY KEY,
      run_id TEXT NOT NULL,
      lesson_id TEXT NOT NULL,
      quiz_version INTEGER NOT NULL,
      uid TEXT NOT NULL,
      selected_option_id TEXT NOT NULL,
      score INTEGER NOT NULL,
      passed INTEGER NOT NULL,
      submitted_at BIGINT NOT NULL,
      FOREIGN KEY (run_id) REFERENCES cohort_track_runs(run_id),
      FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id),
      FOREIGN KEY (uid) REFERENCES users_mirror(uid)
    )`,
    `CREATE TABLE IF NOT EXISTS track_pricing (
      track_id TEXT PRIMARY KEY,
      school_id TEXT NOT NULL,
      currency TEXT NOT NULL,
      amount_minor BIGINT NOT NULL,
      active INTEGER NOT NULL DEFAULT 1,
      updated_by TEXT NOT NULL,
      updated_at BIGINT NOT NULL,
      FOREIGN KEY (track_id) REFERENCES tracks(track_id),
      FOREIGN KEY (school_id) REFERENCES schools(school_id)
    )`,
    `CREATE TABLE IF NOT EXISTS purchases (
      purchase_id TEXT PRIMARY KEY,
      uid TEXT NOT NULL,
      school_id TEXT NOT NULL,
      track_id TEXT NOT NULL,
      currency TEXT NOT NULL,
      amount_minor BIGINT NOT NULL,
      status TEXT NOT NULL,
      provider TEXT NOT NULL,
      provider_reference TEXT,
      created_at BIGINT NOT NULL,
      paid_at BIGINT,
      FOREIGN KEY (uid) REFERENCES users_mirror(uid),
      FOREIGN KEY (school_id) REFERENCES schools(school_id),
      FOREIGN KEY (track_id) REFERENCES tracks(track_id)
    )`,
    `CREATE TABLE IF NOT EXISTS entitlements (
      uid TEXT NOT NULL,
      track_id TEXT NOT NULL,
      purchase_id TEXT NOT NULL,
      status TEXT NOT NULL,
      granted_at BIGINT NOT NULL,
      expires_at BIGINT,
      PRIMARY KEY (uid, track_id),
      FOREIGN KEY (uid) REFERENCES users_mirror(uid),
      FOREIGN KEY (track_id) REFERENCES tracks(track_id),
      FOREIGN KEY (purchase_id) REFERENCES purchases(purchase_id)
    )`,
    `CREATE TABLE IF NOT EXISTS event_reservations (
      reservation_id TEXT PRIMARY KEY,
      event_id TEXT NOT NULL,
      uid TEXT,
      full_name TEXT NOT NULL,
      email TEXT NOT NULL,
      phone TEXT,
      program TEXT,
      created_at BIGINT NOT NULL,
      UNIQUE(event_id, email)
    )`,
    `CREATE TABLE IF NOT EXISTS hub_events (
      event_id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      date_ms BIGINT NOT NULL,
      start_time TEXT,
      end_time TEXT,
      event_type TEXT NOT NULL DEFAULT 'event',
      mentor_id TEXT,
      mentee_id TEXT,
      mentor_name TEXT,
      mentee_name TEXT,
      status INTEGER NOT NULL DEFAULT 0,
      description TEXT,
      mode TEXT NOT NULL DEFAULT 'physical',
      location TEXT,
      meeting_link TEXT,
      participants_json TEXT,
      program TEXT,
      seats INTEGER NOT NULL DEFAULT 0,
      price TEXT,
      is_public INTEGER NOT NULL DEFAULT 1,
      facilitators_json TEXT,
      created_by TEXT,
      created_at BIGINT NOT NULL,
      updated_at BIGINT NOT NULL
    )`,
  ];
  for (const sql of additiveTables) {
    await dbRun(sql);
  }

  try {
    await dbRun(`
CREATE TABLE IF NOT EXISTS school_memberships (
  id TEXT PRIMARY KEY,
  school_id TEXT NOT NULL,
  uid TEXT,
  email TEXT,
  role TEXT NOT NULL,
  status TEXT NOT NULL,
  display_name TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
)`);
  } catch (err) {
    console.warn(`[lms-db] school_memberships: ${err.message}`);
  }

  const indexes = [
    "CREATE INDEX IF NOT EXISTS idx_media_assets_school ON media_assets(school_id)",
    "CREATE INDEX IF NOT EXISTS idx_media_assets_scope ON media_assets(scope, scope_id)",
    "CREATE INDEX IF NOT EXISTS idx_media_assets_status_created ON media_assets(status, created_at)",
    "CREATE INDEX IF NOT EXISTS idx_media_assets_object_key ON media_assets(object_key)",
    "CREATE INDEX IF NOT EXISTS idx_lessons_media ON lessons(media_id)",
    "CREATE INDEX IF NOT EXISTS idx_school_memberships_uid ON school_memberships(uid)",
    "CREATE INDEX IF NOT EXISTS idx_school_memberships_email ON school_memberships(email)",
    "CREATE INDEX IF NOT EXISTS idx_school_memberships_school ON school_memberships(school_id)",
    "CREATE INDEX IF NOT EXISTS idx_cohorts_school ON cohorts(school_id)",
    "CREATE INDEX IF NOT EXISTS idx_cohort_members_uid ON cohort_members(uid)",
    "CREATE INDEX IF NOT EXISTS idx_cohort_runs_cohort_track ON cohort_track_runs(cohort_id, track_id)",
    "CREATE INDEX IF NOT EXISTS idx_milestones_run_order ON milestones(run_id, sort_order)",
    "CREATE INDEX IF NOT EXISTS idx_quiz_attempts_uid_lesson ON quiz_attempts(uid, lesson_id)",
    "CREATE INDEX IF NOT EXISTS idx_cohort_quiz_versions_run_lesson ON cohort_quiz_versions(run_id, lesson_id, version)",
    "CREATE INDEX IF NOT EXISTS idx_cohort_quiz_attempts_uid_lesson ON cohort_quiz_attempts(uid, lesson_id)",
    "CREATE INDEX IF NOT EXISTS idx_purchases_uid ON purchases(uid, created_at)",
    "CREATE INDEX IF NOT EXISTS idx_entitlements_track ON entitlements(track_id, status)",
    "CREATE INDEX IF NOT EXISTS idx_event_reservations_event ON event_reservations(event_id)",
    "CREATE INDEX IF NOT EXISTS idx_event_reservations_email ON event_reservations(email)",
    "CREATE INDEX IF NOT EXISTS idx_event_reservations_uid ON event_reservations(uid)",
    "CREATE INDEX IF NOT EXISTS idx_hub_events_date ON hub_events(date_ms)",
    "CREATE INDEX IF NOT EXISTS idx_hub_events_public ON hub_events(is_public, date_ms)",
  ];
  for (const sql of indexes) {
    try {
      await dbRun(sql);
    } catch (err) {
      console.warn(`[lms-db] index skipped: ${err.message}`);
    }
  }

  try {
    await dbRun(
      `UPDATE media_assets SET storage_driver = 'local' WHERE storage_driver IS NULL OR storage_driver = ''`,
    );
    await dbRun(
      `UPDATE media_assets SET scope = 'misc' WHERE scope IS NULL OR scope = ''`,
    );
  } catch {
    /* fresh DB */
  }
  const now = Date.now();
  const existing = await dbGet(
    "SELECT school_id FROM schools WHERE school_id = ?",
    ["nelsen-digital"],
  );
  if (!existing) {
    await dbRun(
      "INSERT INTO schools (school_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
      ["nelsen-digital", "Nelsen Digital School", now, now],
    );
  }
  try {
    await dbRun(
      `UPDATE tracks SET school_id = 'nelsen-digital' WHERE school_id IS NULL OR school_id = ''`,
    );
  } catch {
    /* ignore */
  }
}

async function initPostgres(databaseUrl) {
  pgPool = new pg.Pool({ connectionString: databaseUrl });
  await pgPool.query(PG_SCHEMA);
  engine = "postgres";
  await ensureMigrations();
}

async function initSqlite() {
  const dataDir = process.env.LMS_DATA_DIR || path.join(ROOT, "data");
  ensureDir(dataDir);
  sqlitePath = path.join(dataDir, "lms.sqlite");

  const SQL = await initSqlJs({
    locateFile: (file) => path.join(sqlJsDist, file),
  });

  if (fs.existsSync(sqlitePath)) {
    sqlite = new SQL.Database(fs.readFileSync(sqlitePath));
  } else {
    sqlite = new SQL.Database();
  }
  sqlite.run("PRAGMA foreign_keys = ON;");
  sqlite.exec(SQLITE_SCHEMA);
  persistSqlite();
  engine = "sqlite";
  await ensureMigrations();
}

/**
 * Initialize primary store. Tries Postgres when DATABASE_URL is set;
 * on auth/connect failure falls back to SQLite (sql.js WASM).
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
  await initSqlite();
  console.log(`[lms-db] primary=sqlite(sql.js) path=${sqlitePath}`);
  return engine;
}

export function getPrimaryEngine() {
  return engine;
}

export function isDbReady() {
  return engine === "sqlite" ? !!sqlite : !!pgPool;
}

function sqliteGet(sql, params = []) {
  const stmt = sqlite.prepare(sql);
  stmt.bind(params);
  let row = null;
  if (stmt.step()) {
    row = stmt.getAsObject();
  }
  stmt.free();
  return row;
}

function sqliteAll(sql, params = []) {
  const stmt = sqlite.prepare(sql);
  stmt.bind(params);
  const rows = [];
  while (stmt.step()) {
    rows.push(stmt.getAsObject());
  }
  stmt.free();
  return rows;
}

/** Run a SELECT that returns one row or null */
export async function dbGet(sql, params = []) {
  if (engine === "sqlite") {
    return sqliteGet(sql, params);
  }
  const res = await pgPool.query(toPg(sql), params);
  return res.rows[0] ?? null;
}

/** Run a SELECT that returns many rows */
export async function dbAll(sql, params = []) {
  if (engine === "sqlite") {
    return sqliteAll(sql, params);
  }
  const res = await pgPool.query(toPg(sql), params);
  return res.rows;
}

/** Run INSERT/UPDATE/DELETE */
export async function dbRun(sql, params = []) {
  if (engine === "sqlite") {
    sqlite.run(sql, params);
    persistSqlite();
    return { changes: sqlite.getRowsModified() };
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
