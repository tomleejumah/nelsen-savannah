/**
 * Single-statement, atomic guard for removal of accidental *empty* courses.
 * Any learner progress, media, assignments, commerce or live dependency
 * keeps the course intact. This helper is pure so destructive SQL is tested.
 */
export const TRACK_DELETION_BLOCKERS = Object.freeze([
  "modules", "lessons", "enrollments", "progress", "submissions",
  "assignments", "cohort_track_runs", "track_pricing", "purchases",
  "entitlements", "certificates", "track_mentors", "track_likes",
  "media_assets", "hub_events",
]);

export function deleteUnusedTrackSql() {
  const conditions = TRACK_DELETION_BLOCKERS.map(
    (table) =>
      `NOT EXISTS (SELECT 1 FROM ${table} WHERE ${table}.track_id = tracks.track_id)`,
  );
  return `DELETE FROM tracks WHERE track_id = ? AND ${conditions.join(" AND ")}`;
}
