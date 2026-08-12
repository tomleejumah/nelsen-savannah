/** Role gate for LMS admin/mentor routes */

import { dbGet } from "../db/lmsDb.js";
import { expandAllowedRoles, normalizeRole } from "../constants/lmsRoles.js";
import { lmsErr } from "../utils/lmsResponse.js";
import { getPrimaryEngine } from "../db/lmsDb.js";

export async function loadUserRole(uid) {
  const row = await dbGet("SELECT role FROM roles WHERE uid = ?", [uid]);
  return normalizeRole(row?.role);
}

export function requireRoles(...allowed) {
  return async (req, res, next) => {
    try {
      const role = await loadUserRole(req.user.uid);
      req.user.role = role;
      const allowedSet = expandAllowedRoles(allowed);
      if (!allowedSet.has(role)) {
        return lmsErr(
          res,
          `Requires role: ${allowed.join("|")}`,
          403,
          getPrimaryEngine(),
        );
      }
      next();
    } catch (err) {
      return lmsErr(res, err.message, 500, getPrimaryEngine());
    }
  };
}
