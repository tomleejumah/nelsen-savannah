import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import {
  LIVE_CHAT_PAGE_SIZE,
  parseChatCursor,
  chatCursorFor,
  liveChatPageQuery,
  formatChatPage,
} from "../src/services/lmsLiveChatPagination.js";

test("live chat pagination has no omissions or duplicates with equal timestamps", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.exec(`CREATE TABLE users_mirror (uid TEXT PRIMARY KEY, display_name TEXT, email TEXT);
      CREATE TABLE live_chat_messages (
        id TEXT PRIMARY KEY, event_id TEXT, uid TEXT, message TEXT, created_at BIGINT
      )`);
    db.run("INSERT INTO users_mirror VALUES (?,?,?)", ["user-1", "Learner", null]);
    for (let i = 0; i < 126; i += 1) {
      // Deliberately create many messages at the same millisecond.
      const id = `lchat_${String(i).padStart(4, "0")}`;
      db.run("INSERT INTO live_chat_messages VALUES (?,?,?,?,?)", [
        id, "event-a", "user-1", `message-${i}`, 1760000000000 + Math.floor(i / 19),
      ]);
    }
    db.run("INSERT INTO live_chat_messages VALUES (?,?,?,?,?)",
      ["lchat_other", "event-b", "user-1", "must be hidden", 1760000009999]);

    let before = null;
    const allIds = [];
    let pages = 0;
    while (true) {
      const query = liveChatPageQuery("event-a", { before, limit: LIVE_CHAT_PAGE_SIZE });
      const stmt = db.prepare(query.sql);
      const rows = [];
      try {
        stmt.bind(query.params);
        while (stmt.step()) rows.push(stmt.getAsObject());
      } finally {
        stmt.free();
      }
      const page = formatChatPage(rows, query.limit);
      assert.ok(page.messages.length <= LIVE_CHAT_PAGE_SIZE);
      allIds.push(...page.messages.map((m) => m.id));
      pages++;
      if (!page.hasMore) {
        assert.equal(page.nextCursor, null);
        break;
      }
      assert.ok(page.nextCursor);
      assert.equal(parseChatCursor(page.nextCursor).id, page.messages[0].id);
      before = page.nextCursor;
      assert.ok(pages <= 4, "cursor must always advance");
    }

    assert.equal(pages, 3);
    assert.equal(allIds.length, 126);
    assert.equal(new Set(allIds).size, 126);
    assert.deepEqual([...allIds].sort(), Array.from(
      { length: 126 }, (_, i) => `lchat_${String(i).padStart(4, "0")}`));
  } finally {
    db.close();
  }
});

test("live chat cursors validate boundaries and refuse malicious input", () => {
  assert.equal(parseChatCursor(null), null);
  const cursor = chatCursorFor({ createdAt: 1760000000000, id: "lchat_abcdef" });
  assert.deepEqual(parseChatCursor(cursor), { t: 1760000000000, id: "lchat_abcdef" });
  assert.throws(() => parseChatCursor("not-valid!"), { status: 400 });
  assert.throws(() => parseChatCursor(Buffer.from('{"t":0,"id":"lchat_x"}').toString("base64url")), { status: 400 });
  assert.throws(() => parseChatCursor(Buffer.from('{"t":1760000000000,"id":"fake"}').toString("base64url")), { status: 400 });
  assert.throws(() => liveChatPageQuery("event-a", { limit: 101 }), { status: 400 });
  assert.throws(() => liveChatPageQuery("event-a", { limit: -1 }), { status: 400 });
});
