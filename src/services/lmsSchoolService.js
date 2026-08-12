/**
 * L4 — school tenancy stubs + people ops (school-scoped).
 */

import crypto from "crypto";
import {
  dbAll,
  dbGet,
  dbRun,
  getPrimaryEngine,
} from "../db/lmsDb.js";
import {
  DEFAULT_SCHOOL_ID,
  DEFAULT_SCHOOL_NAME,
  isSchoolAdmin,
  isSuperAdmin,
  normalizeRole,
  ROLES,
} from "../constants/lmsRoles.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { setUserRole } from "./lmsMeService.js";

function slugify(name) {
  return String(name || "school")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 48);
}

function mapSchool(row) {
  return {
    schoolId: row.school_id,
    name: row.name,
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
  };
}

function mapMember(row) {
  return {
    uid: row.uid,
    email: row.email || "",
    displayName: row.display_name || "",
    photoUrl: row.photo_url || "",
    userRole: normalizeRole(row.role || ROLES.Mentee),
    schoolId: row.school_id || DEFAULT_SCHOOL_ID,
  };
}

export async function getActorSchoolId(uid) {
  const row = await dbGet(
    "SELECT school_id FROM users_mirror WHERE uid = ?",
    [uid],
  );
  return row?.school_id || DEFAULT_SCHOOL_ID;
}

export async function getSchoolName(schoolId) {
  const row = await dbGet("SELECT name FROM schools WHERE school_id = ?", [
    schoolId || DEFAULT_SCHOOL_ID,
  ]);
  return row?.name || DEFAULT_SCHOOL_NAME;
}

async function assertCanManageSchool(actorUid, schoolId) {
  const role = await loadUserRole(actorUid);
  if (isSuperAdmin(role)) return { role, schoolId };
  if (!isSchoolAdmin(role)) {
    const err = new Error("School admin required");
    err.status = 403;
    throw err;
  }
  const actorSchool = await getActorSchoolId(actorUid);
  if (actorSchool !== schoolId) {
    const err = new Error("Cannot manage another school");
    err.status = 403;
    throw err;
  }
  return { role, schoolId };
}

async function ensureUserRow({ uid, email, displayName }) {
  const now = Date.now();
  const existing = await dbGet("SELECT uid FROM users_mirror WHERE uid = ?", [
    uid,
  ]);
  if (existing) {
    if (email || displayName) {
      await dbRun(
        `UPDATE users_mirror SET
          email = COALESCE(NULLIF(?, ''), email),
          display_name = COALESCE(NULLIF(?, ''), display_name),
          updated_at = ?
         WHERE uid = ?`,
        [email || "", displayName || "", now, uid],
      );
    }
    return uid;
  }
  if (!uid) {
    const err = new Error("uid required for new member");
    err.status = 400;
    throw err;
  }
  await dbRun(
    `INSERT INTO users_mirror
      (uid, email, display_name, first_name, last_name, photo_url, created_at, updated_at)
     VALUES (?, ?, ?, '', '', '', ?, ?)`,
    [uid, email || "", displayName || email || uid, now, now],
  );
  try {
    await dbRun(
      "UPDATE users_mirror SET school_id = ? WHERE uid = ?",
      [DEFAULT_SCHOOL_ID, uid],
    );
  } catch {
    /* school_id column missing until migrate */
  }
  return uid;
}

async function setMemberSchoolAndRole(uid, schoolId, role) {
  await setUserRole(uid, role);
  await dbRun(
    "UPDATE users_mirror SET school_id = ?, updated_at = ? WHERE uid = ?",
    [schoolId, Date.now(), uid],
  );
}

export async function listSchools(actorUid) {
  const role = await loadUserRole(actorUid);
  if (!isSuperAdmin(role)) {
    const err = new Error("Super admin required");
    err.status = 403;
    throw err;
  }
  const rows = await dbAll("SELECT * FROM schools ORDER BY name ASC");
  return {
    source: getPrimaryEngine(),
    data: { schools: rows.map(mapSchool) },
  };
}

export async function createSchool(actorUid, body = {}) {
  const role = await loadUserRole(actorUid);
  if (!isSuperAdmin(role)) {
    const err = new Error("Super admin required");
    err.status = 403;
    throw err;
  }
  const name = String(body.name || "").trim();
  if (!name) {
    const err = new Error("name required");
    err.status = 400;
    throw err;
  }
  const schoolId =
    String(body.schoolId || "").trim() ||
    `school-${slugify(name)}-${crypto.randomBytes(2).toString("hex")}`;
  const now = Date.now();
  const existing = await dbGet("SELECT school_id FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  if (existing) {
    const err = new Error("School id already exists");
    err.status = 409;
    throw err;
  }
  await dbRun(
    "INSERT INTO schools (school_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
    [schoolId, name, now, now],
  );
  if (body.adminUid) {
    await ensureUserRow({
      uid: body.adminUid,
      email: body.adminEmail || "",
      displayName: body.adminDisplayName || "",
    });
    await setMemberSchoolAndRole(body.adminUid, schoolId, ROLES.SchoolAdmin);
  }
  const row = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  return {
    source: getPrimaryEngine(),
    data: { school: mapSchool(row) },
  };
}

export async function appointSchoolAdmins(actorUid, schoolId, body = {}) {
  const role = await loadUserRole(actorUid);
  if (!isSuperAdmin(role)) {
    const err = new Error("Super admin required");
    err.status = 403;
    throw err;
  }
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  if (!school) {
    const err = new Error("School not found");
    err.status = 404;
    throw err;
  }
  const uids = Array.isArray(body.adminUids)
    ? body.adminUids
    : body.adminUid
      ? [body.adminUid]
      : [];
  if (!uids.length) {
    const err = new Error("adminUid or adminUids required");
    err.status = 400;
    throw err;
  }
  const admins = [];
  for (const uid of uids) {
    await ensureUserRow({ uid, email: body.email || "", displayName: body.displayName || "" });
    await setMemberSchoolAndRole(uid, schoolId, ROLES.SchoolAdmin);
    admins.push(uid);
  }
  return {
    source: getPrimaryEngine(),
    data: { schoolId, adminUids: admins },
  };
}

export async function listSchoolMembers(actorUid, schoolId) {
  await assertCanManageSchool(actorUid, schoolId);
  const rows = await dbAll(
    `SELECT u.uid, u.email, u.display_name, u.photo_url, u.school_id, r.role
     FROM users_mirror u
     LEFT JOIN roles r ON r.uid = u.uid
     WHERE u.school_id = ?
     ORDER BY u.display_name ASC
     LIMIT 500`,
    [schoolId],
  );
  return {
    source: getPrimaryEngine(),
    data: { members: rows.map(mapMember) },
  };
}

export async function registerSchoolMentor(actorUid, schoolId, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const uid = String(body.uid || "").trim();
  if (!uid) {
    const err = new Error("uid required (Firebase uid of the mentor)");
    err.status = 400;
    throw err;
  }
  await ensureUserRow({
    uid,
    email: body.email || "",
    displayName: body.displayName || "",
  });
  await setMemberSchoolAndRole(uid, schoolId, ROLES.Mentor);
  return {
    source: getPrimaryEngine(),
    data: {
      member: {
        uid,
        userRole: ROLES.Mentor,
        schoolId,
        email: body.email || "",
        displayName: body.displayName || "",
      },
    },
  };
}

export async function registerSchoolMentee(actorUid, schoolId, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const uid = String(body.uid || "").trim();
  if (!uid) {
    const err = new Error("uid required (Firebase uid of the mentee)");
    err.status = 400;
    throw err;
  }
  await ensureUserRow({
    uid,
    email: body.email || "",
    displayName: body.displayName || "",
  });
  await setMemberSchoolAndRole(uid, schoolId, ROLES.Mentee);
  return {
    source: getPrimaryEngine(),
    data: {
      member: {
        uid,
        userRole: ROLES.Mentee,
        schoolId,
        email: body.email || "",
        displayName: body.displayName || "",
      },
    },
  };
}

export async function patchSchoolMemberRole(actorUid, schoolId, targetUid, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const target = await dbGet(
    "SELECT uid, school_id FROM users_mirror WHERE uid = ?",
    [targetUid],
  );
  if (!target || (target.school_id || DEFAULT_SCHOOL_ID) !== schoolId) {
    const err = new Error("Member not in this school");
    err.status = 404;
    throw err;
  }
  const nextRole = normalizeRole(body.userRole || body.role);
  if (nextRole === ROLES.SuperAdmin) {
    const err = new Error("School admin cannot appoint SuperAdmin");
    err.status = 403;
    throw err;
  }
  if (nextRole === ROLES.SchoolAdmin) {
    const actorRole = await loadUserRole(actorUid);
    if (!isSuperAdmin(actorRole)) {
      const err = new Error("Only SuperAdmin can appoint SchoolAdmin");
      err.status = 403;
      throw err;
    }
  }
  // School admin may escalate staff → Mentor (or demote to Mentee)
  if (![ROLES.Mentor, ROLES.Mentee].includes(nextRole) && !isSuperAdmin(await loadUserRole(actorUid))) {
    const err = new Error("School admin may set Mentor or Mentee only");
    err.status = 400;
    throw err;
  }
  await setMemberSchoolAndRole(targetUid, schoolId, nextRole);
  return {
    source: getPrimaryEngine(),
    data: { uid: targetUid, userRole: nextRole, schoolId },
  };
}
