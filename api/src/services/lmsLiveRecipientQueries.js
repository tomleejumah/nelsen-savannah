/**
 * Recipient queries mirror the per-school viewer authorization rules.
 * Deliver to every eligible active school member, even when the user has
 * another school selected in their UI.
 *
 * Never return another school's learner IDs for a scoped broadcast.
 */
export function liveRecipientQuery(event) {
  const scope = String(event?.audienceScope || "");
  const schoolId = String(event?.schoolId || "").trim();
  const trackId = String(event?.trackId || "").trim();
  if (scope === "course") {
    if (!schoolId || !trackId) return null;
    return {
      sql: `SELECT DISTINCT membership.uid
        FROM school_memberships membership
        JOIN (
          SELECT uid, 'enrolled' AS basis FROM enrollments
          WHERE track_id = ? AND uid IS NOT NULL AND uid != ''
            AND lower(COALESCE(status, 'in_progress'))
                NOT IN ('cancelled', 'dropped', 'suspended')
          UNION ALL
          SELECT uid, 'mentor' AS basis FROM track_mentors
          WHERE track_id = ? AND uid IS NOT NULL AND uid != ''
          UNION ALL
          SELECT uid, 'admin' AS basis FROM school_memberships
          WHERE school_id = ? AND status = 'active' AND role = 'SchoolAdmin'
        ) eligible ON eligible.uid = membership.uid
        WHERE membership.school_id = ?
          AND membership.status = 'active'
          AND membership.uid IS NOT NULL AND membership.uid != ''
          AND (eligible.basis = 'enrolled'
            OR (eligible.basis = 'mentor' AND membership.role = 'Mentor')
            OR (eligible.basis = 'admin' AND membership.role = 'SchoolAdmin'))
`,
      params: [trackId, trackId, schoolId, schoolId],
    };
  }
  if (scope === "school") {
    if (!schoolId) return null;
    return {
      sql: `SELECT DISTINCT membership.uid
        FROM school_memberships membership
        WHERE membership.school_id = ?
          AND membership.status = 'active'
          AND membership.uid IS NOT NULL AND membership.uid != ''
`,
      params: [schoolId],
    };
  }
  if (scope === "platform") {
    return {
      sql: "SELECT DISTINCT uid FROM users_mirror WHERE uid IS NOT NULL AND uid != ''",
      params: [],
    };
  }
  return null;
}
