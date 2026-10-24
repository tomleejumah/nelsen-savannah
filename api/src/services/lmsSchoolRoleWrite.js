/**
 * School-specific role changes are applied only to an existing ACTIVE
 * membership in the target user's selected school. A global account role
 * update must never create an unauthorized new school membership or
 * accidentally elevate all schools at once.
 */
export const SYNC_SELECTED_SCHOOL_ROLE_SQL = `
  UPDATE school_memberships
  SET role = ?, updated_at = ?
  WHERE uid = ? AND school_id = ? AND status = 'active'
`;

export function selectedSchoolMembershipRole(globalRole) {
  // The platform SuperAdmin role is global, not a school membership grant.
  // Retain the existing school role when promoting a platform admin.
  if (globalRole === "SuperAdmin" || globalRole === "Admin") return null;
  if (globalRole === "Mentor" || globalRole === "SchoolAdmin") return globalRole;
  return "Mentee";
}
