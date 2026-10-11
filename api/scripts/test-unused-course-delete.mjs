import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import {
  TRACK_DELETION_BLOCKERS,
  deleteUnusedTrackSql,
} from "../src/services/lmsUnusedCourseDeletion.js";

test("guarded course deletion preserves every class of linked LMS data", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.run("CREATE TABLE tracks (track_id TEXT PRIMARY KEY)");
    for (const table of TRACK_DELETION_BLOCKERS) {
      db.run(`CREATE TABLE "${table}" (track_id TEXT NOT NULL)`);
    }
    const safeId = "accidental-empty-duplicate";
    db.run("INSERT INTO tracks VALUES (?)", [safeId]);
    for (const table of TRACK_DELETION_BLOCKERS) {
      const trackId = `linked-${table}`;
      db.run("INSERT INTO tracks VALUES (?)", [trackId]);
      db.run(`INSERT INTO "${table}" (track_id) VALUES (?)`, [trackId]);
    }
    const guardedDelete = deleteUnusedTrackSql();
    for (const table of TRACK_DELETION_BLOCKERS) {
      const trackId = `linked-${table}`;
      db.run(guardedDelete, [trackId]);
      assert.equal(
        db.exec("SELECT COUNT(*) FROM tracks WHERE track_id = '" + trackId + "'")[0]?.values[0][0],
        1,
        `Track with ${table} data must never be deleted`,
      );
    }
    db.run(guardedDelete, [safeId]);
    assert.equal(db.exec("SELECT COUNT(*) FROM tracks WHERE track_id = 'accidental-empty-duplicate'")[0]?.values[0][0], 0);
  } finally {
    db.close();
  }
});
