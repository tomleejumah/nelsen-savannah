import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { canViewHubEvent, getHubEvent } from "./lmsHubEventService.js";

const ACTIVE_MS = 30_000;
const CHAT_COOLDOWN_MS = 1_500;

function liveError(message, status) {
  const error = new Error(message);
  error.status = status;
  return error;
}

async function requireLiveEvent(eventId, uid, { active = false } = {}) {
  const event = await getHubEvent(eventId);
  if (!event || event.eventType !== "live" || !(await canViewHubEvent(event, uid))) {
    throw liveError("Live session not found", 404);
  }
  if (active && event.liveStatus !== "live") {
    throw liveError("This live session is not accepting messages or attendance", 409);
  }
  return event;
}

async function ensureTable() {
  await dbRun(`CREATE TABLE IF NOT EXISTS live_attendance (
    event_id TEXT NOT NULL,
    uid TEXT NOT NULL,
    first_joined_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    watch_seconds INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id, uid)
  )`);
  await dbRun(`CREATE TABLE IF NOT EXISTS live_chat_messages (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL,
    uid TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at BIGINT NOT NULL
  )`);
}

export async function recordLiveAttendance(eventId, uid, body = {}) {
  await ensureTable();
  await requireLiveEvent(eventId, uid, { active: true });
  const now = Date.now();
  const action = String(body.action || "heartbeat").toLowerCase();
  if (!["heartbeat", "leave"].includes(action)) throw liveError("Unsupported attendance action", 400);
  if (action === "leave") {
    const updated = await dbRun(
      "UPDATE live_attendance SET last_seen_at = ? WHERE event_id = ? AND uid = ? AND last_seen_at >= ?",
      [now - ACTIVE_MS - 1, eventId, uid, now - ACTIVE_MS],
    );
    if (Number(updated?.changes ?? updated?.rowCount ?? 0) > 0) {
      console.info("[live-attendance] leave", { eventId, uid });
    }
    return { eventId, active: false, lastSeenAt: now };
  }
  const requested = Math.max(0, Math.min(30, Number(body.watchedSeconds) || 0));
  const existing = await dbGet(
    "SELECT first_joined_at, last_seen_at, watch_seconds FROM live_attendance WHERE event_id = ? AND uid = ?",
    [eventId, uid],
  );
  if (existing) {
    if (Number(existing.last_seen_at) < now - ACTIVE_MS) console.info("[live-attendance] rejoin", { eventId, uid });
    await dbRun(
      `UPDATE live_attendance
       SET last_seen_at = ?, watch_seconds = watch_seconds + ?
       WHERE event_id = ? AND uid = ?`,
      [now, requested, eventId, uid],
    );
  } else {
    console.info("[live-attendance] join", { eventId, uid });
    await dbRun(
      `INSERT INTO live_attendance
       (event_id, uid, first_joined_at, last_seen_at, watch_seconds)
       VALUES (?, ?, ?, ?, ?)`,
      [eventId, uid, now, now, requested],
    );
  }
  return { eventId, active: true, joinedAt: Number(existing?.first_joined_at || now), lastSeenAt: now };
}

export async function getLiveAttendanceSummary(eventId, requesterUid) {
  await ensureTable();
  const event = await getHubEvent(eventId);
  if (!event || event.eventType !== "live") throw liveError("Live session not found", 404);
  if (String(event.createdBy || "") !== String(requesterUid || "")) {
    const role = await dbGet("SELECT role FROM roles WHERE uid = ?", [requesterUid]);
    const platformAdmin = ["Admin", "SuperAdmin"].includes(String(role?.role || ""));
    const schoolAdmin = String(role?.role || "") === "SchoolAdmin"
      && !!event.schoolId && await canViewHubEvent(event, requesterUid);
    if (!platformAdmin && !schoolAdmin) {
      throw liveError("Only the host or an authorized administrator can view attendance", 403);
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


export async function getLiveAudienceState(eventId, uid) {
  await ensureTable();
  await requireLiveEvent(eventId, uid);
  const cutoff = Date.now() - ACTIVE_MS;
  const attendees = await dbAll(
    `SELECT a.uid, a.first_joined_at, a.last_seen_at, a.watch_seconds,
            COALESCE(u.display_name, u.email, a.uid) AS display_name
     FROM live_attendance a
     LEFT JOIN users_mirror u ON u.uid = a.uid
     WHERE a.event_id = ? AND a.last_seen_at >= ?
     ORDER BY a.first_joined_at ASC`,
    [eventId, cutoff],
  );
  const chat = await dbAll(
    `SELECT c.id, c.uid, c.message, c.created_at,
            COALESCE(u.display_name, u.email, c.uid) AS display_name
     FROM live_chat_messages c
     LEFT JOIN users_mirror u ON u.uid = c.uid
     WHERE c.event_id = ?
     ORDER BY c.created_at DESC LIMIT 50`,
    [eventId],
  );
  return {
    eventId,
    concurrentViewers: attendees.length,
    attendees: attendees.map((row) => ({
      uid: String(row.uid),
      displayName: String(row.display_name || row.uid),
      firstJoinedAt: Number(row.first_joined_at),
      lastSeenAt: Number(row.last_seen_at),
      watchSeconds: Number(row.watch_seconds || 0),
    })),
    chat: chat.reverse().map((row) => ({
      id: String(row.id),
      uid: String(row.uid),
      author: String(row.display_name || row.uid),
      message: String(row.message),
      createdAt: Number(row.created_at),
    })),
  };
}

export async function postLiveChatMessage(eventId, uid, body = {}) {
  await ensureTable();
  await requireLiveEvent(eventId, uid, { active: true });
  const message = String(body.message || "").trim();
  if (!message || message.length > 500) {
    const err = new Error("Message must be between 1 and 500 characters");
    err.status = 400;
    throw err;
  }
  const recent = await dbGet(
    "SELECT message, created_at FROM live_chat_messages WHERE event_id = ? AND uid = ? ORDER BY created_at DESC LIMIT 1",
    [eventId, uid],
  );
  const now = Date.now();
  if (recent && now - Number(recent.created_at) < CHAT_COOLDOWN_MS) {
    throw liveError("Slow down before sending another message", 429);
  }
  if (recent && recent.message === message && now - Number(recent.created_at) < 10_000) {
    throw liveError("Duplicate message", 409);
  }
  const id = `lchat_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
  await dbRun(
    "INSERT INTO live_chat_messages (id, event_id, uid, message, created_at) VALUES (?, ?, ?, ?, ?)",
    [id, eventId, uid, message, now],
  );
  return { id, eventId, uid, message, createdAt: now };
}
