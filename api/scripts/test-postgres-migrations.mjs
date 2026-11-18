import assert from "node:assert/strict";
import test from "node:test";
import { initLmsDb, dbAll } from "../src/db/lmsDb.js";

test("PostgreSQL creates document attachment columns on assignments and submissions", async () => {
  assert.ok(process.env.DATABASE_URL, "DATABASE_URL must point to the disposable CI PostgreSQL service");
  assert.equal(await initLmsDb(), "postgres");

  const rows = await dbAll(
    `SELECT table_name, column_name
     FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND table_name IN ('assignments', 'submissions')`,
  );
  const columns = new Set(rows.map((row) => `${row.table_name}.${row.column_name}`));
  assert.ok(columns.has("assignments.attachments_json"));
  assert.ok(columns.has("submissions.attachments_json"));
});
