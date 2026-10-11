import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { initLmsDb, dbAll, dbGet } from "../src/db/lmsDb.js";

test("new SQLite install creates invite_token before unique index", async () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), "nelsen-lms-migrations-"));
  const previousDataDir = process.env.LMS_DATA_DIR;
  const previousUrl = process.env.DATABASE_URL;
  process.env.LMS_DATA_DIR = folder;
  delete process.env.DATABASE_URL;
  try {
    assert.equal(await initLmsDb(), "sqlite");
    const columns = await dbAll("PRAGMA table_info(school_memberships)");
    assert.ok(columns.some((column) => column.name === "invite_token"),
      "school_memberships.invite_token must exist");
    const index = await dbGet(
      "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
      ["idx_school_memberships_token"],
    );
    assert.equal(index?.name, "idx_school_memberships_token");
  } finally {
    if (previousDataDir === undefined) delete process.env.LMS_DATA_DIR;
    else process.env.LMS_DATA_DIR = previousDataDir;
    if (previousUrl === undefined) delete process.env.DATABASE_URL;
    else process.env.DATABASE_URL = previousUrl;
    fs.rmSync(folder, { recursive: true, force: true });
  }
});
