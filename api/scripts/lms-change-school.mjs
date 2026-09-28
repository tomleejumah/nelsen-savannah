#!/usr/bin/env node
/**
 * Change a user's home / active school (SchoolAdmin tooling until SuperAdmin UI exists).
 *
 * Mirrors `lms-promote-mentors.mjs`:
 *   Interactive:
 *     node scripts/lms-change-school.mjs
 *     → prompts for emails + schoolId (lists schools)
 *
 *   Non-interactive:
 *     node scripts/lms-change-school.mjs "a@x.com, b@y.com" school-qa-edenpulp
 *     node scripts/lms-change-school.mjs "a@x.com" school-id SchoolAdmin
 *
 * Updates LMS DB (users_mirror + school_memberships) and RTDB role/user mirror.
 * Run on the API host (or with the same DATABASE_URL / LMS_DATA_DIR as production).
 */
import "dotenv/config";
import crypto from "node:crypto";
import readline from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";
import admin from "../src/config/firebase.js";
import { ROLES, normalizeRole } from "../src/constants/lmsRoles.js";
import { dbAll, dbGet, dbRun, initLmsDb } from "../src/db/lmsDb.js";
import { setUserRole } from "../src/services/lmsMeService.js";

const STAFF = [ROLES.SchoolAdmin, ROLES.Mentor, ROLES.Mentee];

function parseEmails(raw) {
  return String(raw || "")
    .split(/[,;\s]+/)
    .map((e) => e.trim().toLowerCase())
    .filter((e) => e.includes("@"));
}

async function listSchools() {
  return dbAll(
    `SELECT school_id, name FROM schools ORDER BY name ASC, school_id ASC`,
  );
}

async function promptInputs() {
  const argvEmails = process.argv[2];
  const argvSchool = process.argv[3];
  const argvRole = process.argv[4];

  if (argvEmails && argvSchool) {
    return {
      emails: parseEmails(argvEmails),
      schoolId: String(argvSchool).trim(),
      role: normalizeRole(argvRole || ROLES.SchoolAdmin),
    };
  }

  await initLmsDb();
  const schools = await listSchools();
  console.log("\nSchools:");
  if (!schools.length) {
    console.log("  (none — create one via SuperAdmin / POST /lms/schools first)");
  } else {
    for (const s of schools) {
      console.log(`  ${s.school_id}  —  ${s.name || "(unnamed)"}`);
    }
  }

  const rl = readline.createInterface({ input, output });
  try {
    const emailLine = await rl.question(
      "\nEmails to move (comma-separated):\n> ",
    );
    const schoolLine = await rl.question("Target schoolId:\n> ");
    const roleLine = await rl.question(
      `Role at that school [${STAFF.join(" | ")}] (default SchoolAdmin):\n> `,
    );
    const roleRaw = roleLine.trim() || ROLES.SchoolAdmin;
    if (!STAFF.map((r) => r.toLowerCase()).includes(roleRaw.toLowerCase())) {
      console.error(`Unknown role "${roleRaw}". Use one of: ${STAFF.join(", ")}`);
      process.exit(1);
    }
    return {
      emails: parseEmails(emailLine),
      schoolId: schoolLine.trim(),
      role: normalizeRole(roleRaw),
    };
  } finally {
    rl.close();
  }
}

async function ensureMembership({ schoolId, uid, email, role, displayName }) {
  const now = Date.now();
  const existing = await dbGet(
    `SELECT * FROM school_memberships
     WHERE school_id = ? AND (uid = ? OR lower(email) = ?)
     ORDER BY CASE WHEN uid = ? THEN 0 ELSE 1 END
     LIMIT 1`,
    [schoolId, uid, email, uid],
  );
  if (existing) {
    await dbRun(
      `UPDATE school_memberships SET
        uid = ?,
        email = ?,
        role = ?,
        status = 'active',
        display_name = COALESCE(NULLIF(?, ''), display_name),
        updated_at = ?
       WHERE id = ?`,
      [uid, email, role, displayName || "", now, existing.id],
    );
    return existing.id;
  }
  const id = `sm-${crypto.randomBytes(6).toString("hex")}`;
  await dbRun(
    `INSERT INTO school_memberships
      (id, school_id, uid, email, role, status, display_name, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, 'active', ?, ?, ?)`,
    [id, schoolId, uid, email, role, displayName || "", now, now],
  );
  return id;
}

async function ensureUserMirror({ uid, email, displayName, schoolId }) {
  const now = Date.now();
  const row = await dbGet("SELECT uid FROM users_mirror WHERE uid = ?", [uid]);
  if (!row) {
    await dbRun(
      `INSERT INTO users_mirror
        (uid, email, display_name, first_name, last_name, photo_url, created_at, updated_at)
       VALUES (?, ?, ?, '', '', '', ?, ?)`,
      [uid, email, displayName || email, now, now],
    );
  }
  await dbRun(
    `UPDATE users_mirror
     SET school_id = ?, active_school_id = ?, email = COALESCE(NULLIF(?, ''), email),
         display_name = COALESCE(NULLIF(?, ''), display_name), updated_at = ?
     WHERE uid = ?`,
    [schoolId, schoolId, email, displayName || "", now, uid],
  );
}

const { emails, schoolId, role } = await promptInputs();

if (!emails.length) {
  console.error("No valid emails provided.");
  process.exit(1);
}
if (!schoolId) {
  console.error("schoolId required.");
  process.exit(1);
}

await initLmsDb();

const school = await dbGet(
  "SELECT school_id, name FROM schools WHERE school_id = ?",
  [schoolId],
);
if (!school) {
  console.error(`School not found: ${schoolId}`);
  const schools = await listSchools();
  for (const s of schools) {
    console.error(`  ${s.school_id}  —  ${s.name || ""}`);
  }
  process.exit(1);
}

console.log(
  `\nMoving ${emails.length} user(s) → school ${school.school_id} (${school.name || ""}) as ${role}\n`,
);

let failed = 0;
for (const email of emails) {
  try {
    const user = await admin.auth().getUserByEmail(email);
    const providers = user.providerData.map((p) => p.providerId);
    const displayName = user.displayName || "";

    await setUserRole(user.uid, role);
    await ensureUserMirror({
      uid: user.uid,
      email: user.email || email,
      displayName,
      schoolId: school.school_id,
    });
    const membershipId = await ensureMembership({
      schoolId: school.school_id,
      uid: user.uid,
      email: user.email || email,
      role,
      displayName,
    });

    await admin.database().ref(`lms/users/${user.uid}`).update({
      email: user.email || email,
      displayName,
      userRole: role,
      schoolId: school.school_id,
      activeSchoolId: school.school_id,
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });
    await admin.database().ref(`roles/${user.uid}`).set(role);

    console.log(
      `OK  ${email} → ${school.school_id} / ${role} (${user.uid}) membership=${membershipId} providers=${providers.join(",") || "none"}`,
    );
  } catch (e) {
    failed++;
    console.error(`FAIL ${email}: ${e.code || ""} ${e.message}`);
  }
}

console.log(failed ? `\nDone with ${failed} failure(s).` : "\nDone.");
process.exit(failed ? 1 : 0);
