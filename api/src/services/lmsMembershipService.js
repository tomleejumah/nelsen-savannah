/**
 * Multi-school tenancy (prototype map — each school is its own institution)
 *
 * DOORS
 *  A · Roster: admin adds email → membership status=invited → on signup/signin
 *    match email → status=active, uid bound → learning at that school
 *  B · Marketplace: unaffiliated user browses/enrolls → membership applied→active
 *    on the school that owns the track
 *
 * MONEY (stub for demo; wire M-Pesa/card later)
 *  - Tuition / seat fees land in the *school's* ledger, not a shared pot
 *  - Platform cut % TBD (config PLATFORM_CUT_BPS)
 *  - Tutor/mentor payouts: school pays tutors from *their* balance (dashboard stub)
 *
 * ROUTING
 *  - /s/:schoolId/login → school-scoped auth (Door A) — optional later
 *  - bare /learning → if memberships: active school courses; else marketplace
 *  - multi active memberships → switcher sets users_mirror.active_school_id
 */

import crypto from "crypto";
import {
  DEFAULT_SCHOOL_ID,
  DEFAULT_SCHOOL_NAME,
  ROLES,
} from "../constants/lmsRoles.js";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";

const STATUSES = {
  invited: "invited",
  applied: "applied",
  active: "active",
  rejected: "rejected",
  suspended: "suspended",
};

function mapMembership(row) {
  return {
    id: row.id,
    schoolId: row.school_id,
    schoolName: row.school_name || row.name || DEFAULT_SCHOOL_NAME,
    uid: row.uid || null,
    email: row.email || "",
    role: row.role || ROLES.Mentee,
    status: row.status,
    createdAt: Number(row.created_at || 0),
    updatedAt: Number(row.updated_at || 0),
  };
}

export async function listMembershipsForUid(uid, email = "") {
  const byUid = await dbAll(
    `SELECT m.*, s.name AS school_name
     FROM school_memberships m
     LEFT JOIN schools s ON s.school_id = m.school_id
     WHERE m.uid = ?
     ORDER BY m.updated_at DESC`,
    [uid],
  );
  const norm = String(email || "")
    .trim()
    .toLowerCase();
  let byEmail = [];
  if (norm) {
    byEmail = await dbAll(
      `SELECT m.*, s.name AS school_name
       FROM school_memberships m
       LEFT JOIN schools s ON s.school_id = m.school_id
       WHERE lower(m.email) = ? AND (m.uid IS NULL OR m.uid = '')
       ORDER BY m.updated_at DESC`,
      [norm],
    );
  }
  const seen = new Set();
  const out = [];
  for (const row of [...byUid, ...byEmail]) {
    if (seen.has(row.id)) continue;
    seen.add(row.id);
    out.push(mapMembership(row));
  }
  return out;
}

/** Claim pending Door-A invites for this email onto uid. */
export async function claimInvitesForUser(uid, email) {
  const norm = String(email || "")
    .trim()
    .toLowerCase();
  if (!norm) return [];
  const pending = await dbAll(
    `SELECT * FROM school_memberships
     WHERE lower(email) = ? AND status = ? AND (uid IS NULL OR uid = '')`,
    [norm, STATUSES.invited],
  );
  const now = Date.now();
  const claimed = [];
  for (const row of pending) {
    await dbRun(
      `UPDATE school_memberships
       SET uid = ?, status = ?, updated_at = ?
       WHERE id = ?`,
      [uid, STATUSES.active, now, row.id],
    );
    claimed.push(row.school_id);
    // Keep legacy single school_id in sync with first claim / latest
    await dbRun(
      `UPDATE users_mirror SET school_id = ?, updated_at = ? WHERE uid = ?`,
      [row.school_id, now, uid],
    );
  }
  return claimed;
}

export async function ensureActiveSchool(uid, memberships) {
  const activeRows = memberships.filter((m) => m.status === STATUSES.active);
  const user = await dbGet(
    "SELECT active_school_id, school_id FROM users_mirror WHERE uid = ?",
    [uid],
  );
  let active =
    user?.active_school_id ||
    user?.school_id ||
    activeRows[0]?.schoolId ||
    DEFAULT_SCHOOL_ID;

  if (activeRows.length && !activeRows.some((m) => m.schoolId === active)) {
    active = activeRows[0].schoolId;
  }

  if (!activeRows.length && !user?.active_school_id) {
    // Unaffiliated → default digital school context for marketplace browse
    active = DEFAULT_SCHOOL_ID;
  }

  try {
    await dbRun(
      `UPDATE users_mirror SET active_school_id = ?, updated_at = ? WHERE uid = ?`,
      [active, Date.now(), uid],
    );
  } catch {
    /* column missing until migrate */
  }
  return active;
}

export async function setActiveSchool(uid, schoolId) {
  const sid = String(schoolId || "").trim();
  if (!sid) {
    const err = new Error("schoolId required");
    err.status = 400;
    throw err;
  }
  const membership = await dbGet(
    `SELECT * FROM school_memberships
     WHERE uid = ? AND school_id = ? AND status = ?`,
    [uid, sid, STATUSES.active],
  );
  if (!membership && sid !== DEFAULT_SCHOOL_ID) {
    const err = new Error("Not an active member of that school");
    err.status = 403;
    throw err;
  }
  const now = Date.now();
  await dbRun(
    `UPDATE users_mirror SET active_school_id = ?, school_id = ?, updated_at = ? WHERE uid = ?`,
    [sid, sid, now, uid],
  );
  const school = await dbGet("SELECT name FROM schools WHERE school_id = ?", [
    sid,
  ]);
  return {
    source: getPrimaryEngine(),
    data: {
      activeSchoolId: sid,
      schoolName: school?.name || DEFAULT_SCHOOL_NAME,
    },
  };
}

/** Door A — admin invites by email (uid optional until they sign in). */
export async function inviteMenteeByEmail(actorSchoolId, body = {}) {
  const email = String(body.email || "")
    .trim()
    .toLowerCase();
  if (!email || !email.includes("@")) {
    const err = new Error("Valid email required");
    err.status = 400;
    throw err;
  }
  const displayName = String(body.displayName || "").trim();
  const uid = String(body.uid || "").trim() || null;
  const now = Date.now();

  const existing = await dbGet(
    `SELECT * FROM school_memberships
     WHERE school_id = ? AND lower(email) = ?`,
    [actorSchoolId, email],
  );
  if (existing) {
    if (uid && !existing.uid) {
      await dbRun(
        `UPDATE school_memberships SET uid = ?, status = ?, updated_at = ? WHERE id = ?`,
        [uid, STATUSES.active, now, existing.id],
      );
    }
    return {
      source: getPrimaryEngine(),
      data: { membership: mapMembership({ ...existing, email }) },
    };
  }

  // If uid already known user with this email, activate immediately
  let bindUid = uid;
  if (!bindUid) {
    const user = await dbGet(
      `SELECT uid FROM users_mirror WHERE lower(email) = ? LIMIT 1`,
      [email],
    );
    bindUid = user?.uid || null;
  }

  const id = `sm-${crypto.randomBytes(6).toString("hex")}`;
  const status = bindUid ? STATUSES.active : STATUSES.invited;
  await dbRun(
    `INSERT INTO school_memberships
      (id, school_id, uid, email, role, status, display_name, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      id,
      actorSchoolId,
      bindUid,
      email,
      ROLES.Mentee,
      status,
      displayName || null,
      now,
      now,
    ],
  );
  if (bindUid) {
    await dbRun(
      `UPDATE users_mirror SET school_id = ?, active_school_id = ?, updated_at = ? WHERE uid = ?`,
      [actorSchoolId, actorSchoolId, now, bindUid],
    );
  }
  const row = await dbGet("SELECT * FROM school_memberships WHERE id = ?", [id]);
  return {
    source: getPrimaryEngine(),
    data: { membership: mapMembership(row) },
  };
}

/** Door B — attach (or apply) when enrolling in a school's track. */
export async function attachOnEnroll(uid, email, schoolId) {
  const sid = schoolId || DEFAULT_SCHOOL_ID;
  const now = Date.now();
  const existing = await dbGet(
    `SELECT * FROM school_memberships WHERE uid = ? AND school_id = ?`,
    [uid, sid],
  );
  if (existing) {
    if (existing.status === STATUSES.invited || existing.status === STATUSES.applied) {
      await dbRun(
        `UPDATE school_memberships SET status = ?, updated_at = ? WHERE id = ?`,
        [STATUSES.active, now, existing.id],
      );
    }
  } else {
    const id = `sm-${crypto.randomBytes(6).toString("hex")}`;
    await dbRun(
      `INSERT INTO school_memberships
        (id, school_id, uid, email, role, status, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        sid,
        uid,
        String(email || "").toLowerCase(),
        ROLES.Mentee,
        STATUSES.active,
        now,
        now,
      ],
    );
  }
  await dbRun(
    `UPDATE users_mirror SET school_id = ?, active_school_id = ?, updated_at = ? WHERE uid = ?`,
    [sid, sid, now, uid],
  );
  return sid;
}

/** Demo ledger stubs — money belongs to the school institution. */
export async function schoolMoneyStub(schoolId) {
  return {
    schoolId,
    currency: "KES",
    balance: 0,
    platformCutBps: Number(process.env.PLATFORM_CUT_BPS || 1000), // 10% placeholder
    note: "Prototype — tuition lands in school ledger; platform cut TBD; wire M-Pesa/card later.",
    recent: [],
  };
}

export async function tutorPayoutsStub(schoolId) {
  return {
    schoolId,
    tutors: [],
    note: "Prototype — tutor payout dashboard; schools pay mentors from their balance.",
  };
}

export { STATUSES, mapMembership };
