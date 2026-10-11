import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { initLmsDb } from "../src/db/lmsDb.js";

test("configured PostgreSQL outage never silently creates an empty SQLite LMS", async () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), "nelsen-db-outage-"));
  const oldUrl = process.env.DATABASE_URL;
  const oldDir = process.env.LMS_DATA_DIR;
  process.env.LMS_DATA_DIR = folder;
  process.env.DATABASE_URL = "postgres://user:invalid@127.0.0.1:1/nelsen";
  try {
    await assert.rejects(initLmsDb(), /refusing SQLite fallback/);
    assert.equal(fs.existsSync(path.join(folder, "lms.sqlite")), false);
  } finally {
    if (oldUrl === undefined) delete process.env.DATABASE_URL;
    else process.env.DATABASE_URL = oldUrl;
    if (oldDir === undefined) delete process.env.LMS_DATA_DIR;
    else process.env.LMS_DATA_DIR = oldDir;
    fs.rmSync(folder, { recursive: true, force: true });
  }
});

test("missing SQLite with existing backups refuses empty DB creation", async () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), "nelsen-db-missing-"));
  const backups = path.join(folder, "backups");
  fs.mkdirSync(backups);
  fs.writeFileSync(path.join(backups, "lms-2026-10-11-01-00-00.sqlite"), "previous");
  const oldUrl = process.env.DATABASE_URL;
  const oldDir = process.env.LMS_DATA_DIR;
  const oldRequire = process.env.LMS_REQUIRE_EXISTING_DB;
  delete process.env.DATABASE_URL;
  delete process.env.LMS_REQUIRE_EXISTING_DB;
  process.env.LMS_DATA_DIR = folder;
  try {
    await assert.rejects(initLmsDb(), /refusing to create a new database/);
    assert.equal(fs.existsSync(path.join(folder, "lms.sqlite")), false);
  } finally {
    if (oldUrl === undefined) delete process.env.DATABASE_URL;
    else process.env.DATABASE_URL = oldUrl;
    if (oldDir === undefined) delete process.env.LMS_DATA_DIR;
    else process.env.LMS_DATA_DIR = oldDir;
    if (oldRequire === undefined) delete process.env.LMS_REQUIRE_EXISTING_DB;
    else process.env.LMS_REQUIRE_EXISTING_DB = oldRequire;
    fs.rmSync(folder, { recursive: true, force: true });
  }
});
