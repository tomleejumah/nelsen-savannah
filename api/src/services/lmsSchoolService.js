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

export async function updateSchoolBranding(actorUid, schoolId, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  if (!school) {
    const err = new Error("School not found");
    err.status = 404;
    throw err;
  }
  const name = body.name != null ? String(body.name).trim() : school.name;
  const logoUrl = body.logoUrl != null ? String(body.logoUrl) : school.logo_url;
  const accentColor =
    body.accentColor != null ? String(body.accentColor) : school.accent_color;
  const brandingJson =
    body.branding != null
      ? JSON.stringify(body.branding)
      : school.branding_json;
  await dbRun(
    `UPDATE schools SET name = ?, logo_url = ?, accent_color = ?,
     branding_json = ?, updated_at = ? WHERE school_id = ?`,
    [name, logoUrl || null, accentColor || null, brandingJson || null, Date.now(), schoolId],
  );
  const row = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  return {
    source: getPrimaryEngine(),
    data: {
      school: {
        ...mapSchool(row),
        logoUrl: row.logo_url || null,
        accentColor: row.accent_color || null,
      },
    },
  };
}

/** CSV: uid,email,displayName,role per line (header optional). */
export async function importSchoolRoster(actorUid, schoolId, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const csv = String(body.csv || body.text || "");
  const lines = csv
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  if (!lines.length) {
    const err = new Error("csv required");
    err.status = 400;
    throw err;
  }
  let start = 0;
  if (/uid|email/i.test(lines[0])) start = 1;
  const imported = [];
  const errors = [];
  for (let i = start; i < lines.length; i++) {
    const parts = lines[i].split(",").map((p) => p.trim().replace(/^"|"$/g, ""));
    const [uid, email, displayName, roleRaw] = parts;
    if (!uid) {
      errors.push({ line: i + 1, error: "uid missing" });
      continue;
    }
    try {
      await ensureUserRow({ uid, email: email || "", displayName: displayName || "" });
      const role =
        normalizeRole(roleRaw || ROLES.Mentee) === ROLES.Mentor
          ? ROLES.Mentor
          : ROLES.Mentee;
      await setMemberSchoolAndRole(uid, schoolId, role);
      imported.push({ uid, userRole: role });
    } catch (err) {
      errors.push({ line: i + 1, error: err.message });
    }
  }
  return {
    source: getPrimaryEngine(),
    data: { imported: imported.length, members: imported, errors },
  };
}

export async function schoolDashboard(actorUid, schoolId) {
  await assertCanManageSchool(actorUid, schoolId);
  const members = await dbAll(
    `SELECT u.uid, u.display_name, u.email, r.role
     FROM users_mirror u
     LEFT JOIN roles r ON r.uid = u.uid
     WHERE u.school_id = ?`,
    [schoolId],
  );
  const enrollments = await dbAll(
    `SELECT e.uid, e.track_id, e.track_percent, e.last_active_at, u.display_name
     FROM enrollments e
     JOIN users_mirror u ON u.uid = e.uid
     WHERE COALESCE(u.school_id, 'nelsen-digital') = ?`,
    [schoolId],
  );
  const rosterCount = members.length;
  const avgCompletion =
    enrollments.length === 0
      ? 0
      : Math.round(
          enrollments.reduce((s, e) => s + Number(e.track_percent || 0), 0) /
            enrollments.length,
        );
  const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
  const atRisk = enrollments
    .filter(
      (e) =>
        Number(e.track_percent || 0) < 40 &&
        Number(e.last_active_at || 0) < weekAgo,
    )
    .map((e) => ({
      uid: e.uid,
      displayName: e.display_name || "",
      trackId: e.track_id,
      trackPercent: Number(e.track_percent || 0),
      lastActiveAt: Number(e.last_active_at || 0),
    }));
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  return {
    source: getPrimaryEngine(),
    data: {
      schoolId,
      schoolName: school?.name || "",
      rosterCount,
      mentors: members.filter((m) => normalizeRole(m.role) === ROLES.Mentor)
        .length,
      mentees: members.filter((m) => normalizeRole(m.role) === ROLES.Mentee)
        .length,
      enrollments: enrollments.length,
      avgCompletion,
      atRisk,
      logoUrl: school?.logo_url || null,
      accentColor: school?.accent_color || null,
    },
  };
}

