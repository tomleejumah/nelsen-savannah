// School member directory for chat pickers (R2-3). Callers only ever see
// ACTIVE members of schools where they themselves hold an ACTIVE membership.
// No email, no pending/suspended members. Portable SQL (SQLite + PostgreSQL).
import { dbAll } from "../db/lmsDb.js";

export const DIRECTORY_MAX = 200;
const NAME_EXPR = `COALESCE(
  NULLIF(TRIM(u.display_name), ''),
  NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), ''),
  NULLIF(TRIM(m.display_name), ''),
  'Member')`;

export const SCHOOL_DIRECTORY_SQL = `
SELECT m.uid AS uid, m.school_id AS school_id, m.role AS role,
       ${NAME_EXPR} AS name, u.photo_url AS photo_url
FROM school_memberships m
JOIN school_memberships me
  ON me.school_id = m.school_id AND me.uid = ? AND me.status = 'active'
LEFT JOIN users_mirror u ON u.uid = m.uid
WHERE m.status = 'active' AND m.uid IS NOT NULL AND m.uid <> '' AND m.uid <> ?
  AND (? = '' OR lower(${NAME_EXPR}) LIKE ? ESCAPE '\\')
  AND (? = '' OR m.school_id = ?)
ORDER BY name, m.uid
LIMIT ?`;

const ROLE_CANON = {
  mentee: "Mentee", mentor: "Mentor",
  schooladmin: "SchoolAdmin", school_admin: "SchoolAdmin",
  superadmin: "SuperAdmin", admin: "SuperAdmin",
};
export const canonRole = (r) => ROLE_CANON[String(r || "").toLowerCase()] || "Mentee";

export function directoryParams(uid, { q = "", schoolId = "", limit = 50 } = {}) {
  const query = String(q).trim().toLowerCase().slice(0, 60);
  const like = `%${query.replace(/[\\%_]/g, (c) => "\\" + c)}%`;
  const school = String(schoolId || "").trim();
  const lim = Math.min(Math.max(parseInt(limit, 10) || 50, 1), DIRECTORY_MAX);
  return [uid, uid, query, like, school, school, lim];
}

export function mapDirectoryRow(r) {
  return {
    uid: r.uid, schoolId: r.school_id, name: r.name,
    avatarUrl: r.photo_url || null, role: canonRole(r.role),
  };
}

export async function listSchoolDirectory(uid, opts = {}) {
  if (!uid) throw Object.assign(new Error("Unauthorized"), { status: 401 });
  const rows = await dbAll(SCHOOL_DIRECTORY_SQL, directoryParams(uid, opts));
  return { members: rows.map(mapDirectoryRow) };
}
