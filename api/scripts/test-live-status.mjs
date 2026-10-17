import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import {
  LIVE_STATUS_UPDATE_SQL,
  LIVE_NOTIFY_CLAIM_SQL,
} from "../src/services/lmsLiveStatusSql.js";

test("live status cannot regress or resurrect after end, including raced requests", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.exec(`CREATE TABLE hub_events (
      event_id TEXT PRIMARY KEY,
      event_type TEXT NOT NULL,
      status INTEGER NOT NULL DEFAULT 0,
      meeting_link TEXT,
      live_notified_at BIGINT,
      updated_at BIGINT
    )`);
    db.run("INSERT INTO hub_events (event_id, event_type, status, meeting_link) VALUES (?, 'live', 0, ?)", [
      "live-1", "https://youtube.com/watch?v=abc",
    ]);

    const update = (status) => {
      db.run(LIVE_STATUS_UPDATE_SQL, [status, "https://youtube.com/watch?v=abc", Date.now(), "live-1", status]);
      return db.getRowsModified();
    };
    const claim = () => {
      db.run(LIVE_NOTIFY_CLAIM_SQL, [12345, 12345, "live-1"]);
      return db.getRowsModified();
    };
    const current = () => db.exec("SELECT status, live_notified_at FROM hub_events WHERE event_id='live-1'")[0]?.values?.[0];
    assert.equal(claim(), 0, "cannot alert while only scheduled");
    assert.equal(update(1), 1, "scheduled to live");
    assert.equal(claim(), 1, "first live recipient dispatch is claimed");
    assert.equal(claim(), 0, "repeated callbacks do not duplicate the dispatch");
    assert.equal(update(0), 0, "delayed scheduled provider read must not downgrade live");
    assert.equal(update(2), 1, "live to ended");
    assert.equal(update(1), 0, "stale RTMPS reconnection cannot resurrect ended event");
    assert.equal(update(0), 0, "stale provider poll cannot resurrect ended event");
    assert.equal(claim(), 0, "ended event cannot re-claim live notification");
    assert.deepEqual(current(), [2, 12345]);
    assert.equal(update(2), 1, "repeated end remains idempotent");
  } finally {
    db.close();
  }
});
