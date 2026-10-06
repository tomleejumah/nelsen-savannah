import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { canViewHubEvent, getHubEvent } from "./lmsHubEventService.js";

async function ensureTable() {
  await dbRun(`CREATE TABLE IF NOT EXISTS live_attendance (
    event_id TEXT NOT NULL,
    uid TEXT NOT NULL,
    first_joined_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    watch_seconds INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id, uid)
  )`);
}

export async function recordLiveAttendance(eventId, uid, body = {}) {
  await ensureTable();
  const event = await getHubEvent(eventId);
  if (!event || event.eventType !== "live" || !(await canViewHubEvent(event, uid))) {
    const err = new Error("Live session not found");
    err.status = 404;
    throw err;
  }
  const now = Date.now();
  const requested = Math.max(0, Math.min(30, Number(body.watchedSeconds) || 0));
  const existing = await dbGet(
    "SELECT first_joined_at, last_seen_at, watch_seconds FROM live_attendance WHERE event_id = ? AND uid = ?",
    [eventId, uid],
  );
  if (existing) {
    await dbRun(
      `UPDATE live_attendance
       SET last_seen_at = ?, watch_seconds = watch_seconds + ?
       WHERE event_id = ? AND uid = ?`,
      [now, requested, eventId, uid],
    );
  } else {
    await dbRun(
      `INSERT INTO live_attendance
       (event_id, uid, first_joined_at, last_seen_at, watch_seconds)
       VALUES (?, ?, ?, ?, ?)`,
      [eventId, uid, now, now, requested],
    );
  }
  return { eventId, joinedAt: Number(existing?.first_joined_at || now), lastSeenAt: now };
}

export async function getLiveAttendanceSummary(eventId, requesterUid) {
  await ensureTable();
  const event = await getHubEvent(eventId);
  if (!event || event.eventType !== "live") {
    const err = new Error("Live session not found");
    err.status = 404;
    throw err;
  }
  if (String(event.createdBy || "") !== String(requesterUid || "")) {
    const role = await dbGet("SELECT role FROM roles WHERE uid = ?", [requesterUid]);
    if (!["Admin", "SuperAdmin", "SchoolAdmin"].includes(String(role?.role || ""))) {
      const err = new Error("Only the host or an administrator can view attendance");
      err.status = 403;
      throw err;
    }
  }
  const rows = await dbAll(
    `SELECT a.uid, a.first_joined_at, a.last_seen_at, a.watch_seconds,
            COALESCE(u.display_name, u.email, a.uid) AS display_name
     FROM live_attendance a
     LEFT JOIN users_mirror u ON u.uid = a.uid
     WHERE a.event_id = ?
     ORDER BY a.first_joined_at ASC`,
    [eventId],
  );
  const attendees = rows.map((row) => ({
    uid: String(row.uid),
    displayName: String(row.display_name || row.uid),
    firstJoinedAt: Number(row.first_joined_at),
    lastSeenAt: Number(row.last_seen_at),
    watchSeconds: Number(row.watch_seconds || 0),
  }));
  return {
    eventId,
    attendees,
    uniqueAttendees: attendees.length,
    totalWatchSeconds: attendees.reduce((sum, item) => sum + item.watchSeconds, 0),
    source: getPrimaryEngine() || "sqlite",
  };
}
