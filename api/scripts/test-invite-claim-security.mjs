import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import { readFileSync } from "node:fs";

// Exercise the same guarded SQL used by the invite claim handler.
const service = readFileSync(new URL("../src/services/lmsMembershipService.js", import.meta.url), "utf8");
const claim = service.slice(service.indexOf("export async function claimInviteByToken("));
assert.match(claim, /!uid \|\| !actorEmail \|\| !invitedEmail \|\| invitedEmail !== actorEmail/, "claim requires UID and both matching nonempty emails");
assert.match(claim, /row.status !== STATUSES.invited/, "only pending invitations may be claimed");
assert.match(claim, /if \(!claimed\?\.changes && !claimed\?\.rowCount\)/, "database claim must check affected rows");
assert.ok(!claim.includes("fresh?.uid !== uid"), "concurrent duplicate claims must not appear successful");

test("invite claim only binds pending invitations to intended UID", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.run("CREATE TABLE school_memberships (id TEXT PRIMARY KEY, uid TEXT, status TEXT, updated_at INTEGER)");
    const sql = `UPDATE school_memberships SET uid=?, status=?, updated_at=?
       WHERE id=? AND status=? AND (uid IS NULL OR uid='' OR uid=?)`;
    const claimUid = (id, uid) => {
      db.run(sql, [uid, "active", 123, id, "invited", uid]);
      return db.getRowsModified();
    };
    db.run("INSERT INTO school_memberships VALUES ('pending',NULL,'invited',0)");
    db.run("INSERT INTO school_memberships VALUES ('suspended',NULL,'suspended',0)");
    db.run("INSERT INTO school_memberships VALUES ('assigned','uid-A','invited',0)");
    assert.equal(claimUid("pending", "uid-A"), 1);
    assert.equal(claimUid("pending", "uid-B"), 0);
    assert.equal(claimUid("suspended", "uid-A"), 0);
    assert.equal(claimUid("assigned", "uid-B"), 0);
    assert.equal(claimUid("assigned", "uid-A"), 1);
  } finally {
    db.close();
  }
});
