/**
 * Insert live chat only if the sender has not sent anything in the cooldown
 * window, and has not sent the same text in the duplicate window.
 * One SQL statement performs both checks atomically: parallel client retries
 * cannot bypass the previous SELECT + INSERT race.
 */
export const LIVE_CHAT_INSERT_GUARDED_SQL = `
  INSERT INTO live_chat_messages (id, event_id, uid, message, created_at)
  SELECT ?, ?, ?, ?, ?
  WHERE NOT EXISTS (
    SELECT 1 FROM live_chat_messages
    WHERE event_id = ? AND uid = ?
      AND (created_at > ? OR (message = ? AND created_at > ?))
  )
`;

export function chatInsertParams({
  id, eventId, uid, message, now, cooldownMs = 1500, duplicateMs = 10000,
}) {
  return [
    id, eventId, uid, message, now,
    eventId, uid, now - cooldownMs, message, now - duplicateMs,
  ];
}
