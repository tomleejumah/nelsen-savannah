import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import initSqlJs from "sql.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const backupScript = path.join(here, "backup-lms.mjs");
const checkScript = path.join(here, "check-lms-db.mjs");
const TABLES = ["schools", "tracks", "modules", "lessons", "enrollments"];

function run(script, folder, env = {}) {
  return spawnSync(process.execPath, [script, folder], {
    encoding: "utf8",
    env: { ...process.env, DATABASE_URL: "", LMS_DATA_DIR: path.join(folder, "data"), ...env },
  });
}

test("deployment snapshots SQLite, detects lost rows and refuses corrupt data", async () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), "nelsen-db-"));
  const data = path.join(folder, "data");
  const source = path.join(data, "lms.sqlite");
  fs.mkdirSync(data);
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    for (const table of TABLES) {
      db.exec(`CREATE TABLE "${table}" (id TEXT PRIMARY KEY)`);
    }
    db.exec("INSERT INTO schools (id) VALUES ('nelsen')");
    db.exec("INSERT INTO tracks (id) VALUES ('course')");
    fs.writeFileSync(source, Buffer.from(db.export()));
    const before = run(backupScript, folder);
    assert.equal(before.status, 0, before.stderr);
    const snapshots = fs.readdirSync(path.join(data, "backups"));
    assert.equal(snapshots.length, 1);
    assert.equal(run(checkScript, folder).status, 0);

    db.exec("DELETE FROM tracks");
    fs.writeFileSync(source, Buffer.from(db.export()));
    const regression = run(checkScript, folder);
    assert.notEqual(regression.status, 0);
    assert.match(regression.stderr, /Possible data loss/);
    assert.equal(run(checkScript, folder, { LMS_ALLOW_DATA_DECREASE: "1" }).status, 0);

    fs.writeFileSync(source, "corrupted");
    assert.notEqual(run(backupScript, folder).status, 0);
    assert.notEqual(run(checkScript, folder).status, 0);
  } finally {
    db.close();
    fs.rmSync(folder, { recursive: true, force: true });
  }
});
