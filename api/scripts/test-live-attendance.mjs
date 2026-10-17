import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import {
  LIVE_ATTENDANCE_HEARTBEAT_SQL,
  LIVE_ATTENDANCE_LEAVE_SQL,
} from "../src/services/lmsLiveAttendanceSql.js";

test("live attendance rejects duplicate/replayed seconds and limits credit to elapsed time", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.exec(`CREATE TABLE live_attendance (
      event_id TEXT NOT NULL,
      uid TEXT NOT NULL,
      first_joined_at BIGINT NOT NULL,
      last_seen_at BIGINT NOT NULL,
      watch_seconds INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (event_id, uid)
    )`);
    const start = 1760000000000;
    const heartbeat = (uid, offsetMs, claimedSeconds) => {
      const timestamp = start + offsetMs;
      db.run(LIVE_ATTENDANCE_HEARTBEAT_SQL,
        ["live-one", uid, start, timestamp, claimedSeconds]);
    };
    const read = (uid) => {
      const stmt = db.prepare("SELECT watch_seconds, last_seen_at FROM live_attendance WHERE uid = ?");
      try {
        stmt.bind([uid]);
        return stmt.step() ? stmt.getAsObject() : null;
      } finally {
        stmt.free();
      }
    };

    heartbeat("viewer-a", 0, 0);
    heartbeat("viewer-a", 15_000, 15);
    assert.equal(read("viewer-a").watch_seconds, 15);
    heartbeat("viewer-a", 15_000, 15);
    heartbeat("viewer-a", 15_005, 15);
    heartbeat("viewer-a", 14_999, 15);
    assert.equal(read("viewer-a").watch_seconds, 15,
      "duplicate or reordered heartbeat must not inflate watch time");
    heartbeat("viewer-a", 17_000, 30);
    assert.equal(read("viewer-a").watch_seconds, 16,
      "client cannot credit more than two elapsed seconds");
    heartbeat("viewer-a", 32_000, 15);
    assert.equal(read("viewer-a").watch_seconds, 31);

    // A separate member gets independent accounting, even in the same event.
    heartbeat("viewer-b", 0, 0);
    heartbeat("viewer-b", 1_000, 30);
    assert.equal(read("viewer-b").watch_seconds, 1);

    // A leave request invalidates the active-presence cutoff without adding
    // time. New heartbeat can rejoin, starting from its own server timestamp.
    const leaveTime = start + 32_001;
    db.run(LIVE_ATTENDANCE_LEAVE_SQL,
      [leaveTime - 30_001, "live-one", "viewer-a", leaveTime - 30_000]);
    assert.equal(read("viewer-a").watch_seconds, 31);
    heartbeat("viewer-a", 45_000, 0);
    assert.equal(read("viewer-a").watch_seconds, 31);
    heartbeat("viewer-a", 60_000, 15);
    assert.equal(read("viewer-a").watch_seconds, 46);

  } finally {
    db.close();
  }
});
