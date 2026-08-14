import {
  capabilitiesFor,
  DEFAULT_SCHOOL_ID,
  DEFAULT_SCHOOL_NAME,
  normalizeRole,
  ROLES,
  shellFor,
} from "../constants/lmsRoles.js";
import {
  checkPrimaryHealth,
  dbGet,
  dbRun,
  getPrimaryEngine,
} from "../db/lmsDb.js";
import {
  checkRtdbHealth,
  dualWrite,
  mirrorRole,
  mirrorUserMeta,
  readRoleFromRtdb,
  readUserFromRtdb,
} from "./lmsMirror.js";

function splitName(displayName = "") {
  const parts = String(displayName).trim().split(/\s+/).filter(Boolean);
  return {
    firstName: parts[0] || "",
    lastName: parts.slice(1).join(" ") || "",
  };
}

function mePayload(profile, user, role, source, extras = {}) {
  return {
    source,
    data: {
      uid: profile.uid,
      email: user?.email || profile.email || "",
      displayName:
        user?.displayName || user?.display_name || profile.displayName || "",
      firstName: user?.firstName || user?.first_name || "",
      lastName: user?.lastName || user?.last_name || "",
      photoUrl: user?.photoUrl || user?.photo_url || profile.photoUrl || "",
      userRole: role,
      /** @deprecated use userRole — kept for older clients */
      role,
      schoolId: user?.schoolId || user?.school_id || DEFAULT_SCHOOL_ID,
      schoolName: user?.schoolName || DEFAULT_SCHOOL_NAME,
      activeSchoolId:
        extras.activeSchoolId ||
        user?.activeSchoolId ||
        user?.active_school_id ||
        user?.schoolId ||
        user?.school_id ||
        DEFAULT_SCHOOL_ID,
      memberships: extras.memberships || [],
      unaffiliated: Boolean(extras.unaffiliated),
      shell: shellFor(role),
      capabilities: capabilitiesFor(role),
    },
  };
}

/**
 * Upsert user + default role in primary, mirror to RTDB.
 * @param {{ uid: string, email?: string, displayName?: string, photoUrl?: string }} profile
 */
export async function upsertUserFromToken(profile) {
  const now = Date.now();
  const { firstName, lastName } = splitName(profile.displayName);
  const email = profile.email || "";
  const displayName = profile.displayName || "";
  const photoUrl = profile.photoUrl || "";

  await dualWrite({
    label: "users_mirror",
    writeFn: async () => {
      const existing = await dbGet(
        "SELECT uid FROM users_mirror WHERE uid = ?",
        [profile.uid],
      );
      if (existing) {
        await dbRun(
          `UPDATE users_mirror
           SET email = ?, display_name = ?, first_name = ?, last_name = ?,
               photo_url = ?, updated_at = ?
           WHERE uid = ?`,
          [
            email,
            displayName,
            firstName,
            lastName,
            photoUrl,
            now,
            profile.uid,
          ],
        );
      } else {
        await dbRun(
          `INSERT INTO users_mirror
            (uid, email, display_name, first_name, last_name, photo_url, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            profile.uid,
            email,
            displayName,
            firstName,
            lastName,
            photoUrl,
            now,
            now,
          ],
        );
      }

      const roleRow = await dbGet("SELECT role FROM roles WHERE uid = ?", [
        profile.uid,
      ]);
      if (!roleRow) {
        await dbRun(
          "INSERT INTO roles (uid, role, updated_at) VALUES (?, ?, ?)",
          [profile.uid, ROLES.Mentee, now],
        );
      }
      return {
        uid: profile.uid,
        email,
        displayName,
        firstName,
        lastName,
        photoUrl,
        role: roleRow?.role || ROLES.Mentee,
      };
    },
    mirrorFn: async (row) => {
      await mirrorUserMeta(row.uid, {
        email: row.email,
        displayName: row.displayName,
        firstName: row.firstName,
        lastName: row.lastName,
        photoUrl: row.photoUrl,
      });
      if (!row.role || row.role === ROLES.Mentee) {
        const rtdbRole = await readRoleFromRtdb(row.uid);
        if (!rtdbRole) {
          await mirrorRole(row.uid, ROLES.Mentee);
        }
      }
    },
  });
}

/**
 * GET /lms/me — prefer primary; on failure fall back to RTDB.
 */
export async function getMe(profile) {
  const health = await checkPrimaryHealth();

  if (health.ok) {
    try {
      await upsertUserFromToken(profile);
      const user = await dbGet(
        `SELECT u.uid, u.email, u.display_name, u.first_name, u.last_name, u.photo_url,
                u.school_id, u.active_school_id, r.role
         FROM users_mirror u
         LEFT JOIN roles r ON r.uid = u.uid
         WHERE u.uid = ?`,
        [profile.uid],
      );

      let role = normalizeRole(user?.role);
      try {
        const rtdbRole = await readRoleFromRtdb(profile.uid);
        if (rtdbRole) {
          role = normalizeRole(rtdbRole);
          if (role !== normalizeRole(user?.role)) {
            await setUserRole(profile.uid, role);
          }
        }
      } catch {
        /* ignore RTDB role peek */
      }

      let memberships = [];
      let activeSchoolId = user?.active_school_id || user?.school_id || DEFAULT_SCHOOL_ID;
      try {
        const membership = await import("./lmsMembershipService.js");
        await membership.claimInvitesForUser(profile.uid, user?.email || profile.email);
        memberships = await membership.listMembershipsForUid(
          profile.uid,
          user?.email || profile.email,
        );
        activeSchoolId = await membership.ensureActiveSchool(profile.uid, memberships);
      } catch (err) {
        console.warn("[lms-me] memberships:", err.message);
      }

      const schoolId = activeSchoolId || user?.school_id || DEFAULT_SCHOOL_ID;
      let schoolName = DEFAULT_SCHOOL_NAME;
      try {
        const school = await dbGet(
          "SELECT name FROM schools WHERE school_id = ?",
          [schoolId],
        );
        if (school?.name) schoolName = school.name;
      } catch {
        /* schools table optional during migrate */
      }

      const activeMemberships = memberships.filter((m) => m.status === "active");

      return mePayload(
        profile,
        {
          email: user?.email,
          display_name: user?.display_name,
          first_name: user?.first_name,
          last_name: user?.last_name,
          photo_url: user?.photo_url,
          school_id: schoolId,
          schoolName,
          active_school_id: activeSchoolId,
        },
        role,
        getPrimaryEngine(),
        {
          activeSchoolId,
          memberships,
          unaffiliated: activeMemberships.length === 0,
        },
      );
    } catch (err) {
      console.error("[lms-me] primary read failed, trying RTDB:", err.message);
    }
  }

  const [rtdbUser, rtdbRole] = await Promise.all([
    readUserFromRtdb(profile.uid),
    readRoleFromRtdb(profile.uid),
  ]);
  const role = normalizeRole(rtdbRole || ROLES.Mentee);
  const { firstName, lastName } = splitName(
    rtdbUser?.displayName || profile.displayName,
  );

  return mePayload(
    profile,
    {
      email: rtdbUser?.email,
      displayName: rtdbUser?.displayName,
      firstName: rtdbUser?.firstName || firstName,
      lastName: rtdbUser?.lastName || lastName,
      photoUrl: rtdbUser?.photoUrl,
      schoolId: DEFAULT_SCHOOL_ID,
    },
    role,
    "rtdb",
  );
}

export async function getStoreHealth() {
  const [primary, rtdb] = await Promise.all([
    checkPrimaryHealth(),
    checkRtdbHealth(),
  ]);
  return {
    primary,
    rtdb,
    engine: getPrimaryEngine(),
  };
}

export async function setUserRole(uid, role) {
  const now = Date.now();
  const normalized = normalizeRole(role);
  const engine = getPrimaryEngine();

  if (engine === "sqlite") {
    await dbRun(
      `INSERT INTO roles (uid, role, updated_at) VALUES (?, ?, ?)
       ON CONFLICT(uid) DO UPDATE SET role = excluded.role, updated_at = excluded.updated_at`,
      [uid, normalized, now],
    );
  } else {
    await dbRun(
      `INSERT INTO roles (uid, role, updated_at) VALUES (?, ?, ?)
       ON CONFLICT (uid) DO UPDATE SET role = EXCLUDED.role, updated_at = EXCLUDED.updated_at`,
      [uid, normalized, now],
    );
  }

  await mirrorRole(uid, normalized);
  return normalized;
}
