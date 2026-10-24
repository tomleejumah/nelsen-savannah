import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { canViewHubEvent, getHubEvent, getLiveSchoolRole } from "./lmsHubEventService.js";
import { liveChatPageQuery, formatChatPage } from "./lmsLiveChatPagination.js";
import { LIVE_ATTENDANCE_HEARTBEAT_SQL, LIVE_ATTENDANCE_LEAVE_SQL } from "./lmsLiveAttendanceSql.js";
import { LIVE_CHAT_INSERT_GUARDED_SQL, chatInsertParams } from "./lmsLiveChatSql.js";

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

let schemaReadyPromise = null;

/**
 * Initialize once per API process. Repeating CREATE TABLE through sql.js on
 * every 5-second live poll exported and rewrote the full SQLite DB twice per
 * request, unnecessarily inflating API latency during busy broadcasts.
 * On failure we discard the promise so the next request retries migration.
 */
async function ensureTable() {
  if (!schemaReadyPromise) {
    schemaReadyPromise = (async () => {
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
      await dbRun(`CREATE INDEX IF NOT EXISTS idx_live_chat_event_time
        ON live_chat_messages(event_id, created_at DESC, id DESC)`);
      await dbRun(`CREATE INDEX IF NOT EXISTS idx_live_chat_sender_time
        ON live_chat_messages(event_id, uid, created_at DESC)`);
      await dbRun(`CREATE INDEX IF NOT EXISTS idx_live_attendance_current
        ON live_attendance(event_id, last_seen_at)`);
    })().catch((error) => {
      schemaReadyPromise = null;
      throw error;
    });
  }
  return schemaReadyPromise;
}

export async function recordLiveAttendance(eventId, uid, body = {}) {
  await ensureTable();
  const action = String(body.action || "heartbeat").toLowerCase();
  await requireLiveEvent(eventId, uid, { active: action !== "leave" });
  const now = Date.now();
  if (!["heartbeat", "leave"].includes(action)) throw liveError("Unsupported attendance action", 400);
  if (action === "leave") {
    const updated = await dbRun(
      LIVE_ATTENDANCE_LEAVE_SQL,
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
  const previousSeen = Number(existing?.last_seen_at || 0);
  const rejoined = previousSeen > 0 && previousSeen < now - ACTIVE_MS;
  await dbRun(
    LIVE_ATTENDANCE_HEARTBEAT_SQL,
    // The first request only establishes presence; there is no elapsed
    // session time yet to credit from client-supplied watchedSeconds.
    [eventId, uid, now, now, existing && previousSeen >= now - ACTIVE_MS ? Math.min(requested, Math.max(0, Math.floor((now - previousSeen) / 1000))) : 0],
  );
  if (!existing) console.info("[live-attendance] join", { eventId, uid });
  else if (rejoined) console.info("[live-attendance] rejoin", { eventId, uid });
  return { eventId, active: true, joinedAt: Number(existing?.first_joined_at || now), lastSeenAt: now };
}

export async function getLiveAttendanceSummary(eventId, requesterUid) {
  await ensureTable();
  const event = await getHubEvent(eventId);
  if (!event || event.eventType !== "live") throw liveError("Live session not found", 404);
  if (!requesterUid) throw liveError("Authentication required", 401);
  // Owning a school event does not retain roster access after losing that school.\n  if (String(event.createdBy || "") === String(requesterUid || "") && event.schoolId) {\n    const role = await getLiveSchoolRole(requesterUid, event.schoolId);\n    if (role !== "SuperAdmin" && !(await canViewHubEvent(event, requesterUid))) {\n      throw liveError("School access has been revoked", 403);\n    }\n  }\n  if (String(event.createdBy || "") !== String(requesterUid || "")) {
    const role = await getLiveSchoolRole(requesterUid, event.schoolId);
    const platformAdmin = role === "SuperAdmin";
    const schoolAdmin = role === "SchoolAdmin"
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
  const event = await requireLiveEvent(eventId, uid);
  const cutoff = Date.now() - ACTIVE_MS;
  // This endpoint is polled every five seconds by every viewer. Counting
  // presences in SQLite is much cheaper than loading personal identities and
  // watch totals only to discard them before responding.
  const presence = await dbGet(
    `SELECT COUNT(*) AS viewers FROM live_attendance
     WHERE event_id = ? AND last_seen_at >= ?`,
    [eventId, cutoff],
  );
  const query = liveChatPageQuery(eventId);
  const chatRows = await dbAll(query.sql, query.params);
  const page = formatChatPage(chatRows, query.limit);
  return {
    eventId,
    liveStatus: event.liveStatus,
    liveAvailability: event.liveAvailability,
    // This is returned only after canViewHubEvent authorization, never
    // through the unauthenticated public index.
    meetingLink: event.meetingLink || "",
    concurrentViewers: event.liveStatus === "live" ? Number(presence?.viewers || 0) : 0,
    // Public live-state is readable by attendees; identifiable attendance is host-only.
    // Hosts retrieve names and watch durations from the authorized attendance endpoint.
    attendees: [],
    chat: page.messages,
    chatHasMore: page.hasMore,
    chatNextCursor: page.nextCursor,
  };
}

/**
 * Fetch older chat pages for a viewer or host. Authorization is checked on
 * every request, so losing school membership also revokes history access.
 */
export async function getLiveChatHistory(eventId, uid, query = {}) {
  await ensureTable();
  await requireLiveEvent(eventId, uid);
  const { before = null, limit = 50 } = query;
  const pageQuery = liveChatPageQuery(eventId, { before, limit });
  const rows = await dbAll(pageQuery.sql, pageQuery.params);
  const page = formatChatPage(rows, pageQuery.limit);
  return { eventId, ...page };
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
  const inserted = await dbRun(
    LIVE_CHAT_INSERT_GUARDED_SQL,
    chatInsertParams({
      id, eventId, uid, message, now,
      cooldownMs: CHAT_COOLDOWN_MS,
    }),
  );
  if (Number(inserted?.changes ?? inserted?.rowCount ?? 0) === 0) {
    throw liveError("Slow down before sending another message", 429);
  }
  return { id, eventId, uid, message, createdAt: now };
}
