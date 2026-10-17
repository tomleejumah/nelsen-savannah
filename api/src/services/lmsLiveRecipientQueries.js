/**
 * Only recipient queries belong here: no Firebase credentials, network I/O or
 * user-identifying log output. The same tenant boundary is enforced both for
 * in-app live visibility and outgoing notifications.
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
          SELECT uid FROM enrollments
          WHERE track_id = ? AND uid IS NOT NULL AND uid != ''
            AND lower(COALESCE(status, 'in_progress'))
                NOT IN ('cancelled', 'dropped', 'suspended')
          UNION
          SELECT uid FROM track_mentors
          WHERE track_id = ? AND uid IS NOT NULL AND uid != ''
        ) eligible ON eligible.uid = membership.uid
        LEFT JOIN users_mirror user_record ON user_record.uid = membership.uid
        WHERE membership.school_id = ?
          AND membership.status = 'active'
          AND membership.uid IS NOT NULL AND membership.uid != ''
          AND (user_record.active_school_id IS NULL
               OR user_record.active_school_id = ''
               OR user_record.active_school_id = ?)`,
      params: [trackId, trackId, schoolId, schoolId],
    };
  }
  if (scope === "school") {
    if (!schoolId) return null;
    return {
      sql: `SELECT DISTINCT membership.uid
        FROM school_memberships membership
        LEFT JOIN users_mirror user_record ON user_record.uid = membership.uid
        WHERE membership.school_id = ?
          AND membership.status = 'active'
          AND membership.uid IS NOT NULL AND membership.uid != ''
          AND (user_record.active_school_id IS NULL
               OR user_record.active_school_id = ''
               OR user_record.active_school_id = ?)`,
      params: [schoolId, schoolId],
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
