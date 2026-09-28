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
import { inviteUrlForToken } from "./lmsMembershipService.js";
import { notifySchoolDecisionEmail } from "./inquiryEmail.js";

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
    uid: row.uid || "",
    email: row.email || "",
    displayName: row.display_name || "",
    photoUrl: row.photo_url || "",
    userRole: normalizeRole(row.role || ROLES.Mentee),
    schoolId: row.school_id || DEFAULT_SCHOOL_ID,
    status: row.status || "active",
    inviteUrl: inviteUrlForToken(row.invite_token) || null,
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

/** Any signed-in user — school picker / Explore other schools. */
export async function listSchoolsCatalog(_actorUid) {
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
  // New school is institution-ready: roster, CMS, money ledger, tutor payouts
  // all key off schoolId — no extra enable flags.
  if (body.adminUid) {
    await ensureUserRow({
      uid: body.adminUid,
      email: body.adminEmail || "",
      displayName: body.adminDisplayName || "",
    });
    await setMemberSchoolAndRole(body.adminUid, schoolId, ROLES.SchoolAdmin);
    try {
      const { inviteMenteeByEmail } = await import("./lmsMembershipService.js");
      // Ensure admin also has an active membership row for multi-school switcher
      if (body.adminEmail) {
        await inviteMenteeByEmail(schoolId, {
          email: body.adminEmail,
          displayName: body.adminDisplayName,
          uid: body.adminUid,
        });
      }
    } catch {
      /* membership optional */
    }
  }
  const row = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  return {
    source: getPrimaryEngine(),
    data: {
      school: mapSchool(row),
      ready: [
        "roster / invite mentees",
        "register mentors",
        "school catalog CMS",
        "dashboard",
        "school money ledger (stub)",
        "tutor payouts (stub)",
      ],
    },
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
    `SELECT u.uid, u.email, u.display_name, u.photo_url, u.school_id,
            COALESCE(m.role, r.role) AS role,
            COALESCE(m.status, 'active') AS status, m.invite_token
     FROM users_mirror u
     LEFT JOIN roles r ON r.uid = u.uid
     LEFT JOIN school_memberships m
       ON m.school_id = u.school_id AND m.uid = u.uid
     WHERE u.school_id = ?
     ORDER BY u.display_name ASC
     LIMIT 500`,
    [schoolId],
  );
  const pending = await dbAll(
    `SELECT uid, email, display_name, NULL AS photo_url, school_id, role, status, invite_token
     FROM school_memberships
     WHERE school_id = ? AND status IN ('invited', 'applied')
     ORDER BY created_at DESC
     LIMIT 200`,
    [schoolId],
  );
  const byKey = new Map();
  for (const row of rows) {
    byKey.set(row.uid || `email:${String(row.email || "").toLowerCase()}`, mapMember(row));
  }
  for (const row of pending) {
    const key = row.uid || `email:${String(row.email || "").toLowerCase()}`;
    if (byKey.has(key)) continue;
    byKey.set(
      key,
      mapMember({
        ...row,
        display_name: row.display_name,
        photo_url: "",
      }),
    );
  }
  return {
    source: getPrimaryEngine(),
    data: { members: [...byKey.values()] },
  };
}

export async function registerSchoolMentor(actorUid, schoolId, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const email = String(body.email || "").trim();
  const uid = String(body.uid || "").trim();
  // Door A: invite by email (school admin never needs Firebase uid)
  if (email) {
    const { inviteMemberByEmail } = await import("./lmsMembershipService.js");
    const invited = await inviteMemberByEmail(schoolId, {
      email,
      displayName: body.displayName,
      uid: uid || undefined,
      role: ROLES.Mentor,
    });
    return {
      source: invited.source,
      data: {
        member: {
          uid: invited.data.membership.uid || "",
          userRole: ROLES.Mentor,
          schoolId,
          email: invited.data.membership.email,
          displayName: body.displayName || "",
          status: invited.data.membership.status,
          inviteUrl: invited.data.membership.inviteUrl || null,
        },
      },
    };
  }
  if (!uid) {
    const err = new Error("email required (or Firebase uid for legacy)");
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
        status: "active",
      },
    },
  };
}

export async function registerSchoolMentee(actorUid, schoolId, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const email = String(body.email || "").trim();
  const uid = String(body.uid || "").trim();
  // Prefer Door A email invite; uid still accepted for legacy demos
  if (email) {
    const { inviteMenteeByEmail } = await import("./lmsMembershipService.js");
    const invited = await inviteMenteeByEmail(schoolId, {
      email,
      displayName: body.displayName,
      uid: uid || undefined,
    });
    return {
      source: invited.source,
      data: {
        member: {
          uid: invited.data.membership.uid || "",
          userRole: ROLES.Mentee,
          schoolId,
          email: invited.data.membership.email,
          displayName: body.displayName || "",
          status: invited.data.membership.status,
          inviteUrl: invited.data.membership.inviteUrl || null,
        },
      },
    };
  }
  if (!uid) {
    const err = new Error("email required (or Firebase uid for legacy)");
    err.status = 400;
    throw err;
  }
  await ensureUserRow({
    uid,
    email: body.email || "",
    displayName: body.displayName || "",
  });
  await setMemberSchoolAndRole(uid, schoolId, ROLES.Mentee);
  const { inviteMenteeByEmail } = await import("./lmsMembershipService.js");
  if (body.email) {
    await inviteMenteeByEmail(schoolId, {
      email: body.email,
      displayName: body.displayName,
      uid,
    });
  }
  return {
    source: getPrimaryEngine(),
    data: {
      member: {
        uid,
        userRole: ROLES.Mentee,
        schoolId,
        email: body.email || "",
        displayName: body.displayName || "",
        status: "active",
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

/** Mentors the school admin can assign to courses. */
async function loadAssignableMentors(schoolId) {
  const byUid = new Map();
  const push = (row) => {
    const uid = String(row?.uid || "").trim();
    if (!uid || byUid.has(uid)) return;
    byUid.set(uid, {
      uid,
      email: row.email || "",
      displayName: row.display_name || row.displayName || row.email || uid,
      photoUrl: row.photo_url || row.photoUrl || "",
      status: row.status || "active",
    });
  };

  // Rostered at this school with Mentor role (users_mirror.school_id).
  const onSchool = await dbAll(
    `SELECT u.uid, u.email, u.display_name, u.photo_url,
            COALESCE(m.status, 'active') AS status
     FROM users_mirror u
     INNER JOIN roles r ON r.uid = u.uid
     LEFT JOIN school_memberships m
       ON m.school_id = ? AND m.uid = u.uid
     WHERE u.school_id = ? AND r.role = ?`,
    [schoolId, schoolId, ROLES.Mentor],
  );
  for (const row of onSchool || []) push(row);

  // Membership row says Mentor (covers invited→active even if school_id lagged).
  const viaMembership = await dbAll(
    `SELECT COALESCE(m.uid, u.uid) AS uid, m.email, m.display_name,
            u.photo_url, m.status, u.display_name AS user_display_name
     FROM school_memberships m
     LEFT JOIN users_mirror u ON u.uid = m.uid OR lower(u.email) = lower(m.email)
     WHERE m.school_id = ? AND m.role = ? AND m.status != 'suspended'`,
    [schoolId, ROLES.Mentor],
  );
  for (const row of viaMembership || []) {
    push({
      ...row,
      display_name: row.display_name || row.user_display_name || "",
    });
  }

  // Already tutoring a course in this school (even if school_id was never set).
  const onTracks = await dbAll(
    `SELECT tm.uid, u.email, COALESCE(tm.display_name, u.display_name) AS display_name,
            COALESCE(tm.avatar_url, u.photo_url) AS photo_url, 'active' AS status
     FROM track_mentors tm
     INNER JOIN tracks t ON t.track_id = tm.track_id
     LEFT JOIN users_mirror u ON u.uid = tm.uid
     WHERE COALESCE(t.school_id, ?) = ?`,
    [DEFAULT_SCHOOL_ID, schoolId],
  );
  for (const row of onTracks || []) push(row);

  // Course tutor_id fallback (legacy single-tutor field).
  const tutors = await dbAll(
    `SELECT t.tutor_id AS uid, u.email,
            COALESCE(t.tutor_name, u.display_name) AS display_name,
            COALESCE(t.tutor_avatar_url, u.photo_url) AS photo_url,
            'active' AS status
     FROM tracks t
     LEFT JOIN users_mirror u ON u.uid = t.tutor_id
     WHERE COALESCE(t.school_id, ?) = ? AND t.tutor_id IS NOT NULL AND t.tutor_id != ''`,
    [DEFAULT_SCHOOL_ID, schoolId],
  );
  for (const row of tutors || []) push(row);

  return [...byUid.values()].sort((a, b) =>
    String(a.displayName).localeCompare(String(b.displayName)),
  );
}

export async function schoolDashboard(actorUid, schoolId) {
  await assertCanManageSchool(actorUid, schoolId);
  const members = await dbAll(
    `SELECT u.uid, u.display_name, u.email, r.role,
            COALESCE(m.status, 'active') AS status
     FROM users_mirror u
     LEFT JOIN roles r ON r.uid = u.uid
     LEFT JOIN school_memberships m
       ON m.school_id = u.school_id AND m.uid = u.uid
     WHERE u.school_id = ?`,
    [schoolId],
  );
  const assignableMentors = await loadAssignableMentors(schoolId);
  const enrollments = await dbAll(
    `SELECT e.uid, e.track_id, e.track_percent, e.last_active_at, u.display_name,
            u.email, COALESCE(t.title, e.track_id) AS track_title
     FROM enrollments e
     JOIN users_mirror u ON u.uid = e.uid
     LEFT JOIN tracks t ON t.track_id = e.track_id
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
      email: e.email || "",
      trackId: e.track_id,
      trackPercent: Number(e.track_percent || 0),
      lastActiveAt: Number(e.last_active_at || 0),
    }));
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  const tracks = await dbAll(
    `SELECT track_id, title FROM tracks
     WHERE COALESCE(school_id, 'nelsen-digital') = ?`,
    [schoolId],
  );
  const assignmentRows = await dbAll(
    `SELECT p.track_id, AVG(p.assignment_pct) AS avg_assignment
     FROM progress p
     JOIN lessons l ON l.lesson_id = p.lesson_id AND l.has_assignment = 1
     JOIN users_mirror u ON u.uid = p.uid
     WHERE COALESCE(u.school_id, 'nelsen-digital') = ?
     GROUP BY p.track_id`,
    [schoolId],
  );
  const assignmentByTrack = new Map(
    assignmentRows.map((r) => [
      r.track_id,
      Math.round(Number(r.avg_assignment || 0)),
    ]),
  );
  const byCourseMap = new Map();
  for (const t of tracks) {
    byCourseMap.set(t.track_id, {
      trackId: t.track_id,
      title: t.title || t.track_id,
      enrolled: 0,
      percentSum: 0,
    });
  }
  for (const e of enrollments) {
    const key = e.track_id;
    const row = byCourseMap.get(key) || {
      trackId: key,
      title: e.track_title || key,
      enrolled: 0,
      percentSum: 0,
    };
    row.enrolled += 1;
    row.percentSum += Number(e.track_percent || 0);
    byCourseMap.set(key, row);
  }
  const byCourse = [...byCourseMap.values()]
    .map((r) => ({
      trackId: r.trackId,
      title: r.title,
      enrolled: r.enrolled,
      avgPercent: r.enrolled ? Math.round(r.percentSum / r.enrolled) : 0,
      avgAssignment: assignmentByTrack.get(r.trackId) || 0,
      mentors: [],
    }))
    .sort((a, b) => b.enrolled - a.enrolled || a.title.localeCompare(b.title));
  const mentorRows = await dbAll(
    `SELECT tm.track_id, tm.uid, tm.display_name, tm.avatar_url
     FROM track_mentors tm
     JOIN tracks t ON t.track_id = tm.track_id
     WHERE COALESCE(t.school_id, 'nelsen-digital') = ?
     ORDER BY tm.linked_at ASC`,
    [schoolId],
  );
  const mentorsByTrack = new Map();
  for (const row of mentorRows) {
    const list = mentorsByTrack.get(row.track_id) || [];
    list.push({
      uid: row.uid,
      displayName: row.display_name || "",
      avatarUrl: row.avatar_url || "",
    });
    mentorsByTrack.set(row.track_id, list);
  }
  for (const course of byCourse) {
    course.mentors = mentorsByTrack.get(course.trackId) || [];
  }
  const avgAssignment =
    byCourse.length === 0
      ? 0
      : Math.round(
          byCourse.reduce((s, c) => s + Number(c.avgAssignment || 0), 0) /
            byCourse.length,
        );

  return {
    source: getPrimaryEngine(),
    data: {
      schoolId,
      schoolName: school?.name || "",
      rosterCount,
      mentors: Math.max(
        members.filter(
          (m) =>
            normalizeRole(m.role) === ROLES.Mentor && m.status !== "suspended",
        ).length,
        assignableMentors.length,
      ),
      mentees: members.filter((m) => normalizeRole(m.role) === ROLES.Mentee)
        .length,
      enrollments: enrollments.length,
      avgCompletion,
      avgAssignment,
      byCourse,
      atRisk,
      assignableMentors,
      logoUrl: school?.logo_url || null,
      accentColor: school?.accent_color || null,
    },
  };
}

export async function setSchoolTrackMentors(actorUid, schoolId, trackId, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const track = await dbGet(
    "SELECT track_id, school_id FROM tracks WHERE track_id = ?",
    [trackId],
  );
  if (!track) {
    const err = new Error("Track not found");
    err.status = 404;
    throw err;
  }
  if ((track.school_id || DEFAULT_SCHOOL_ID) !== schoolId) {
    const err = new Error("Track is not in this school");
    err.status = 403;
    throw err;
  }
  const uids = [
    ...new Set(
      (Array.isArray(body.uids) ? body.uids : [])
        .map((u) => String(u || "").trim())
        .filter(Boolean),
    ),
  ];
  for (const uid of uids) {
    const user = await dbGet(
      `SELECT u.uid, u.school_id, r.role AS global_role, m.role AS member_role
       FROM users_mirror u
       LEFT JOIN roles r ON r.uid = u.uid
       LEFT JOIN school_memberships m
         ON m.uid = u.uid AND m.school_id = ?
       WHERE u.uid = ?`,
      [schoolId, uid],
    );
    if (!user) {
      const err = new Error("Each assignee must be a mentor of this school");
      err.status = 400;
      throw err;
    }
    const isMentor =
      normalizeRole(user.global_role) === ROLES.Mentor ||
      normalizeRole(user.member_role) === ROLES.Mentor;
    if (!isMentor) {
      const err = new Error("Each assignee must be a mentor of this school");
      err.status = 400;
      throw err;
    }
    // Attach to school when assigning (tutors often have role Mentor but null school_id).
    if (user.school_id !== schoolId || normalizeRole(user.member_role) !== ROLES.Mentor) {
      await setMemberSchoolAndRole(uid, schoolId, ROLES.Mentor);
      const nowAttach = Date.now();
      const profile = await dbGet(
        "SELECT email, display_name FROM users_mirror WHERE uid = ?",
        [uid],
      );
      const existingMem = await dbGet(
        "SELECT id FROM school_memberships WHERE school_id = ? AND uid = ?",
        [schoolId, uid],
      );
      if (!existingMem) {
        await dbRun(
          `INSERT INTO school_memberships
             (id, school_id, uid, email, role, status, display_name, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, 'active', ?, ?, ?)`,
          [
            `sm-${schoolId}-${uid}`.slice(0, 80),
            schoolId,
            uid,
            profile?.email || "",
            ROLES.Mentor,
            profile?.display_name || "",
            nowAttach,
            nowAttach,
          ],
        );
      } else {
        await dbRun(
          `UPDATE school_memberships SET role = ?, status = 'active', updated_at = ?
           WHERE school_id = ? AND uid = ?`,
          [ROLES.Mentor, nowAttach, schoolId, uid],
        );
      }
    }
  }
  const now = Date.now();
  await dbRun("DELETE FROM track_mentors WHERE track_id = ?", [trackId]);
  let lastName = "";
  let lastAvatar = "";
  let lastUid = "";
  for (const uid of uids) {
    const user = await dbGet(
      "SELECT display_name, email, photo_url FROM users_mirror WHERE uid = ?",
      [uid],
    );
    const displayName =
      user?.display_name && user.display_name !== uid
        ? user.display_name
        : user?.email
          ? String(user.email).split("@")[0]
          : "Mentor";
    const avatarUrl = user?.photo_url || "";
    await dbRun(
      `INSERT INTO track_mentors (track_id, uid, display_name, avatar_url, linked_at)
       VALUES (?, ?, ?, ?, ?)`,
      [trackId, uid, displayName, avatarUrl, now],
    );
    lastUid = uid;
    lastName = displayName;
    lastAvatar = avatarUrl;
  }
  await dbRun(
    `UPDATE tracks SET tutor_id = ?, tutor_name = ?, tutor_avatar_url = ?, updated_at = ?
     WHERE track_id = ?`,
    [lastUid, lastName, lastAvatar, now, trackId],
  );
  const mentors = await dbAll(
    `SELECT uid, display_name, avatar_url, linked_at
     FROM track_mentors WHERE track_id = ? ORDER BY linked_at ASC`,
    [trackId],
  );
  return {
    source: getPrimaryEngine(),
    data: {
      trackId,
      mentors: (mentors || []).map((r) => ({
        uid: r.uid,
        displayName: r.display_name || "",
        avatarUrl: r.avatar_url || "",
        linkedAt: Number(r.linked_at) || 0,
      })),
    },
  };
}

export async function setSchoolMemberStatus(actorUid, schoolId, targetUid, body = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const uid = String(targetUid || "").trim();
  if (!uid) {
    const err = new Error("uid required");
    err.status = 400;
    throw err;
  }
  if (uid === actorUid) {
    const err = new Error("Cannot change your own status");
    err.status = 400;
    throw err;
  }
  const next = String(body.status || "").trim().toLowerCase();
  if (next !== "suspended" && next !== "active") {
    const err = new Error("status must be active or suspended");
    err.status = 400;
    throw err;
  }
  const targetRole = await loadUserRole(uid);
  if (isSuperAdmin(targetRole) || targetRole === ROLES.SchoolAdmin) {
    const err = new Error("Cannot disable a school admin");
    err.status = 403;
    throw err;
  }
  const user = await dbGet(
    "SELECT uid, email, display_name, school_id FROM users_mirror WHERE uid = ?",
    [uid],
  );
  let mem = await dbGet(
    `SELECT * FROM school_memberships WHERE school_id = ? AND uid = ?`,
    [schoolId, uid],
  );
  // Applied join requests may not have users_mirror.school_id set yet.
  if (!mem && (!user || (user.school_id || DEFAULT_SCHOOL_ID) !== schoolId)) {
    const err = new Error("Member not found in this school");
    err.status = 404;
    throw err;
  }
  if (!user && !mem) {
    const err = new Error("Member not found in this school");
    err.status = 404;
    throw err;
  }
  const now = Date.now();
  if (!mem) {
    const id = `sm-${crypto.randomBytes(6).toString("hex")}`;
    await dbRun(
      `INSERT INTO school_memberships
        (id, school_id, uid, email, role, status, display_name, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        schoolId,
        uid,
        user?.email || "",
        targetRole || ROLES.Mentor,
        next,
        user?.display_name || "",
        now,
        now,
      ],
    );
    mem = await dbGet("SELECT * FROM school_memberships WHERE id = ?", [id]);
  } else {
    await dbRun(
      `UPDATE school_memberships SET status = ?, updated_at = ? WHERE id = ?`,
      [next, now, mem.id],
    );
  }
  if (next === "active") {
    await dbRun(
      `UPDATE users_mirror SET school_id = ?, active_school_id = ?, updated_at = ? WHERE uid = ?`,
      [schoolId, schoolId, now, uid],
    );
  }
  if (next === "suspended") {
    await dbRun(
      `DELETE FROM track_mentors
       WHERE uid = ? AND track_id IN (
         SELECT track_id FROM tracks WHERE COALESCE(school_id, 'nelsen-digital') = ?
       )`,
      [uid, schoolId],
    );
    if (!isSuperAdmin(targetRole)) {
      await setUserRole(uid, ROLES.Mentee);
    }
  } else {
    const restore = normalizeRole(mem.role || targetRole);
    if (restore === ROLES.Mentor) {
      await setUserRole(uid, ROLES.Mentor);
    }
  }
  const schoolName = await getSchoolName(schoolId);
  const notifyEmail = user?.email || mem?.email || "";
  const notifyName = user?.display_name || mem?.display_name || "";
  if (next === "active" && notifyEmail) {
    await notifySchoolDecisionEmail({
      to: notifyEmail,
      displayName: notifyName,
      schoolName,
      kind: "mentee",
      approved: true,
    });
  }
  if (next === "suspended" && notifyEmail) {
    await notifySchoolDecisionEmail({
      to: notifyEmail,
      displayName: notifyName,
      schoolName,
      kind: "mentee",
      approved: false,
    });
  }
  const fresh = await dbGet(
    `SELECT u.uid, u.email, u.display_name, u.photo_url, u.school_id, r.role,
            m.status, m.invite_token
     FROM users_mirror u
     LEFT JOIN roles r ON r.uid = u.uid
     LEFT JOIN school_memberships m ON m.school_id = ? AND m.uid = u.uid
     WHERE u.uid = ?`,
    [schoolId, uid],
  );
  return {
    source: getPrimaryEngine(),
    data: {
      member: mapMember(
        fresh || {
          uid,
          email: user?.email || mem.email || "",
          display_name: user?.display_name || mem.display_name || "",
          photo_url: "",
          school_id: schoolId,
          role: targetRole || mem.role || ROLES.Mentee,
          status: next,
        },
      ),
    },
  };
}

function mapApplication(row) {
  let answers = {};
  let documentUrls = [];
  try {
    answers = row.answers_json ? JSON.parse(row.answers_json) : {};
  } catch {
    answers = {};
  }
  try {
    documentUrls = row.document_urls_json
      ? JSON.parse(row.document_urls_json)
      : [];
  } catch {
    documentUrls = [];
  }
  return {
    id: row.id,
    schoolId: row.school_id,
    uid: row.uid || "",
    email: row.email || "",
    displayName: row.display_name || "",
    answers,
    documentUrls: Array.isArray(documentUrls) ? documentUrls : [],
    videoUrl: row.video_url || null,
    status: row.status || "pending",
    createdAt: Number(row.created_at || 0),
    updatedAt: Number(row.updated_at || 0),
    decidedBy: row.decided_by || null,
    decidedAt: row.decided_at ? Number(row.decided_at) : null,
  };
}

/** Any signed-in user can apply to teach at a school. */
export async function applyToSchoolAsMentor(actorUid, schoolId, body = {}) {
  const school = await dbGet("SELECT school_id, name FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  if (!school) {
    const err = new Error("School not found");
    err.status = 404;
    throw err;
  }
  const role = await loadUserRole(actorUid);
  if (isSuperAdmin(role) || isSchoolAdmin(role)) {
    const err = new Error("Admins cannot apply as mentors");
    err.status = 400;
    throw err;
  }
  const user = await dbGet(
    "SELECT uid, email, display_name FROM users_mirror WHERE uid = ?",
    [actorUid],
  );
  const email = String(body.email || user?.email || "").trim().toLowerCase();
  const displayName = String(
    body.displayName || user?.display_name || "",
  ).trim();
  const pending = await dbGet(
    `SELECT id FROM school_applications
     WHERE school_id = ? AND uid = ? AND status = 'pending'`,
    [schoolId, actorUid],
  );
  if (pending) {
    const err = new Error("You already have a pending application for this school");
    err.status = 409;
    throw err;
  }
  const now = Date.now();
  const id = `sa-${crypto.randomBytes(6).toString("hex")}`;
  const answers =
    body.answers && typeof body.answers === "object" ? body.answers : {};
  const documentUrls = Array.isArray(body.documentUrls) ? body.documentUrls : [];
  const videoUrl = body.videoUrl ? String(body.videoUrl) : null;
  await dbRun(
    `INSERT INTO school_applications
      (id, school_id, uid, email, display_name, answers_json, document_urls_json,
       video_url, status, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)`,
    [
      id,
      schoolId,
      actorUid,
      email,
      displayName,
      JSON.stringify(answers),
      JSON.stringify(documentUrls),
      videoUrl,
      now,
      now,
    ],
  );
  const row = await dbGet("SELECT * FROM school_applications WHERE id = ?", [id]);
  return {
    source: getPrimaryEngine(),
    data: { application: mapApplication(row) },
  };
}

export async function listSchoolApplications(actorUid, schoolId, query = {}) {
  await assertCanManageSchool(actorUid, schoolId);
  const status = String(query.status || "pending").trim().toLowerCase();
  const rows =
    status === "all"
      ? await dbAll(
          `SELECT * FROM school_applications WHERE school_id = ?
           ORDER BY created_at DESC LIMIT 200`,
          [schoolId],
        )
      : await dbAll(
          `SELECT * FROM school_applications WHERE school_id = ? AND status = ?
           ORDER BY created_at DESC LIMIT 200`,
          [schoolId, status],
        );
  return {
    source: getPrimaryEngine(),
    data: { applications: (rows || []).map(mapApplication) },
  };
}

export async function decideSchoolApplication(
  actorUid,
  schoolId,
  applicationId,
  body = {},
) {
  await assertCanManageSchool(actorUid, schoolId);
  const decision = String(body.status || body.decision || "")
    .trim()
    .toLowerCase();
  if (decision !== "approved" && decision !== "rejected") {
    const err = new Error("status must be approved or rejected");
    err.status = 400;
    throw err;
  }
  const row = await dbGet(
    "SELECT * FROM school_applications WHERE id = ? AND school_id = ?",
    [applicationId, schoolId],
  );
  if (!row) {
    const err = new Error("Application not found");
    err.status = 404;
    throw err;
  }
  if (row.status !== "pending") {
    const err = new Error("Application already decided");
    err.status = 409;
    throw err;
  }
  const now = Date.now();
  await dbRun(
    `UPDATE school_applications
     SET status = ?, decided_by = ?, decided_at = ?, updated_at = ?
     WHERE id = ?`,
    [decision, actorUid, now, now, applicationId],
  );
  if (decision === "approved") {
    await ensureUserRow({
      uid: row.uid,
      email: row.email || "",
      displayName: row.display_name || "",
    });
    await setMemberSchoolAndRole(row.uid, schoolId, ROLES.Mentor);
    const existing = await dbGet(
      `SELECT id FROM school_memberships WHERE school_id = ? AND uid = ?`,
      [schoolId, row.uid],
    );
    if (existing) {
      await dbRun(
        `UPDATE school_memberships SET role = ?, status = 'active', updated_at = ?
         WHERE id = ?`,
        [ROLES.Mentor, now, existing.id],
      );
    } else {
      const mid = `sm-${crypto.randomBytes(6).toString("hex")}`;
      await dbRun(
        `INSERT INTO school_memberships
          (id, school_id, uid, email, role, status, display_name, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, 'active', ?, ?, ?)`,
        [
          mid,
          schoolId,
          row.uid,
          row.email || "",
          ROLES.Mentor,
          row.display_name || "",
          now,
          now,
        ],
      );
    }
  }
  const fresh = await dbGet("SELECT * FROM school_applications WHERE id = ?", [
    applicationId,
  ]);
  const schoolName = await getSchoolName(schoolId);
  await notifySchoolDecisionEmail({
    to: row.email || "",
    displayName: row.display_name || "",
    schoolName,
    kind: "mentor",
    approved: decision === "approved",
  });
  return {
    source: getPrimaryEngine(),
    data: { application: mapApplication(fresh) },
  };
}

