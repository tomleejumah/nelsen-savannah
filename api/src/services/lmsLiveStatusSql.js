/**
 * Atomic SQL guards shared by the live-status API and its regression tests.
 * Status progression is strictly 0 (scheduled) -> 1 (live) -> 2 (ended).
 * Re-sending the same state is idempotent; a lower state cannot overwrite a
 * later one, even when Android and the provider poll race each other.
 */
export const LIVE_STATUS_UPDATE_SQL = `
  UPDATE hub_events SET status = ?, meeting_link = ?, updated_at = ?
  WHERE event_id = ? AND event_type = 'live' AND status <= ?
`;

export const LIVE_NOTIFY_CLAIM_SQL = `
  UPDATE hub_events SET live_notified_at = ?, updated_at = ?
  WHERE event_id = ? AND live_notified_at IS NULL AND status = 1
`;
