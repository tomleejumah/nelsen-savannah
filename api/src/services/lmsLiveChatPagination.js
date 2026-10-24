/**
 * Stable keyset pagination for live chat. A (timestamp, id) cursor ensures
 * messages with identical created_at values are not skipped or repeated.
 * Cursor content is validated and never interpolated into SQL.
 */
export const LIVE_CHAT_PAGE_SIZE = 50;

export function parseChatCursor(raw) {
  if (!raw) return null;
  if (typeof raw !== "string" || raw.length > 240 || !/^[A-Za-z0-9_-]+$/.test(raw)) {
    throw invalidCursor();
  }
  try {
    const payload = JSON.parse(Buffer.from(raw, "base64url").toString("utf8"));
    if (
      !payload || !Number.isSafeInteger(payload.t) || payload.t <= 0 ||
      typeof payload.id !== "string" || !/^lchat_[A-Za-z0-9_\-]{1,96}$/.test(payload.id)
    ) throw invalidCursor();
    return { t: payload.t, id: payload.id };
  } catch {
    throw invalidCursor();
  }
}

function invalidCursor() {
  const error = new Error("Invalid live chat cursor");
  error.status = 400;
  return error;
}

export function chatCursorFor(message) {
  return Buffer.from(JSON.stringify({
    t: Number(message.createdAt ?? message.created_at),
    id: String(message.id),
  })).toString("base64url");
}

export function liveChatPageQuery(eventId, { before = null, limit = LIVE_CHAT_PAGE_SIZE } = {}) {
  const rawLimit = Number(limit);
  if (!Number.isInteger(rawLimit) || rawLimit < 1 || rawLimit > 100) {
    const error = new Error("Live chat limit must be between 1 and 100");
    error.status = 400;
    throw error;
  }
  const cursor = parseChatCursor(before);
  const filter = cursor
    ? " AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))"
    : "";
  return {
    sql: `SELECT c.id, c.uid, c.message, c.created_at,
            COALESCE(u.display_name, u.email, c.uid) AS display_name
      FROM live_chat_messages c
      LEFT JOIN users_mirror u ON u.uid = c.uid
      WHERE c.event_id = ?${filter}
      ORDER BY c.created_at DESC, c.id DESC LIMIT ?`,
    params: cursor
      ? [eventId, cursor.t, cursor.t, cursor.id, rawLimit + 1]
      : [eventId, rawLimit + 1],
    limit: rawLimit,
  };
}

export function formatChatPage(rows, limit = LIVE_CHAT_PAGE_SIZE) {
  const hasMore = rows.length > limit;
  const included = rows.slice(0, limit);
  const oldest = included[included.length - 1];
  return {
    messages: included.reverse().map((row) => ({
      id: String(row.id),
      uid: String(row.uid),
      author: String(row.display_name || row.uid),
      message: String(row.message),
      createdAt: Number(row.created_at),
    })),
    hasMore,
    nextCursor: hasMore && oldest ? chatCursorFor(oldest) : null,
  };
}
