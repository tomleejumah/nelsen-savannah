import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import { LIVE_CHAT_INSERT_GUARDED_SQL, chatInsertParams } from "../src/services/lmsLiveChatSql.js";

test("live chat cooldown is enforced in SQL rather than a racy preflight", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.exec(`CREATE TABLE live_chat_messages (
      id TEXT PRIMARY KEY, event_id TEXT, uid TEXT,
      message TEXT, created_at BIGINT
    )`);
    const start = 1760000000000;
    const send = (id, eventId, uid, message, delay) => {
      db.run(LIVE_CHAT_INSERT_GUARDED_SQL, chatInsertParams({
        id, eventId, uid, message, now: start + delay,
      }));
      return db.getRowsModified();
    };
    assert.equal(send("1", "live-1", "alice", "Hello", 0), 1);
    assert.equal(send("2", "live-1", "alice", "Hello", 0), 0,
      "identical parallel/retry message must not be inserted");
    assert.equal(send("3", "live-1", "alice", "Different", 1000), 0,
      "cooldown must reject different text from same sender");
    assert.equal(send("4", "live-1", "alice", "Hello", 2000), 0,
      "same text is blocked within 10 seconds even after cooldown");
    assert.equal(send("5", "live-1", "alice", "Different", 2000), 1,
      "a different message can be sent after cooldown");
    assert.equal(send("6", "live-1", "bob", "Different", 2000), 1,
      "one learner does not block another learner");
    assert.equal(send("7", "live-2", "alice", "Different", 2000), 1,
      "a second event has an independent chat window");
    assert.equal(send("8", "live-1", "alice", "Hello", 12000), 1,
      "a repeated message is allowed after the duplicate window");
    const rows = db.exec("SELECT id FROM live_chat_messages");
    assert.equal(rows[0].values.length, 5);
  } finally {
    db.close();
  }
});
