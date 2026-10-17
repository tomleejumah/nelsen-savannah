/**
 * Database-side idempotent heartbeat accounting. The client provides an
 * upper bound, but the server never credits more seconds than elapsed since
 * the last accepted heartbeat. Duplicate/retried requests are safe even
 * when they race other requests in the same millisecond.
 *
 * Works in sql.js SQLite and PostgreSQL.
 */
export const LIVE_ATTENDANCE_HEARTBEAT_SQL = `
  INSERT INTO live_attendance
    (event_id, uid, first_joined_at, last_seen_at, watch_seconds)
  VALUES (?, ?, ?, ?, ?)
  ON CONFLICT(event_id, uid) DO UPDATE SET
    watch_seconds = live_attendance.watch_seconds +
      CASE
        WHEN excluded.last_seen_at <= live_attendance.last_seen_at THEN 0
        WHEN excluded.watch_seconds <= 0 THEN 0
        WHEN excluded.last_seen_at - live_attendance.last_seen_at
          < excluded.watch_seconds * 1000
          THEN CAST((excluded.last_seen_at - live_attendance.last_seen_at) / 1000 AS INTEGER)
        ELSE excluded.watch_seconds
      END,
    last_seen_at = CASE
      WHEN excluded.last_seen_at > live_attendance.last_seen_at
        THEN excluded.last_seen_at
      ELSE live_attendance.last_seen_at
    END
`;

export const LIVE_ATTENDANCE_LEAVE_SQL = `
  UPDATE live_attendance
  SET last_seen_at = ?
  WHERE event_id = ? AND uid = ? AND last_seen_at >= ?
`;
