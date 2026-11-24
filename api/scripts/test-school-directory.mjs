import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import { SCHOOL_DIRECTORY_SQL, directoryParams, mapDirectoryRow, canonRole }
  from "../src/services/lmsSchoolDirectory.js";

async function seed() {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  db.exec(`CREATE TABLE school_memberships (id TEXT, school_id TEXT, uid TEXT, email TEXT, role TEXT, status TEXT, display_name TEXT);
           CREATE TABLE users_mirror (uid TEXT, email TEXT, display_name TEXT, first_name TEXT, last_name TEXT, photo_url TEXT);`);
  const m = (id, school, uid, role, status, dn) =>
    db.run("INSERT INTO school_memberships VALUES (?,?,?,?,?,?,?)", [id, school, uid, `${uid}@x.io`, role, status, dn]);
  m(1, "A", "me", "Mentee", "active", null);
  m(2, "A", "ann", "Mentor", "active", null);
  m(3, "A", "bob", "school_admin", "active", null);
  m(4, "A", "sus", "Mentee", "suspended", "Suspended Sam");
  m(5, "A", "pend", "Mentee", "invited", "Pending Pat");
  m(6, "B", "other", "Mentee", "active", "Other School Person");   // I'm not in B
  m(7, "A", null, "Mentee", "active", "No Uid");
  m(8, "C", "me", "Mentee", "pending", null);                       // my own membership not active
  m(9, "C", "carl", "Mentee", "active", "Carl");
  m(10, "A", "dup", "Mentee", "active", "Dana_100%");
  db.run("INSERT INTO users_mirror VALUES ('ann','a@x','Ann Mentor',null,null,'http://p/ann.png')");
  db.run("INSERT INTO users_mirror VALUES ('bob','b@x','',  'Bob','Admin',null)");
  return db;
}
const run = (db, opts) => {
  const r = db.exec(SCHOOL_DIRECTORY_SQL.replace(/\?/g, "?"), directoryParams("me", opts));
  if (!r.length) return [];
  const [{ columns, values }] = r;
  return values.map((v) => mapDirectoryRow(Object.fromEntries(columns.map((c, i) => [c, v[i]]))));
};

test("only active members of schools where caller is active; no self, no emails", async () => {
  const db = await seed();
  const out = run(db, {});
  assert.deepEqual(out.map((x) => x.uid), ["ann", "bob", "dup"]);
  assert.ok(out.every((x) => x.schoolId === "A"));            // not B (not a member), not C (caller not active)
  assert.ok(out.every((x) => !("email" in x)));
  assert.equal(out[0].avatarUrl, "http://p/ann.png");
  assert.equal(out[1].name, "Bob Admin");                      // first+last fallback
  assert.equal(out[1].role, "SchoolAdmin");                    // legacy spelling normalised
});

test("search is case-insensitive and treats % and _ literally", async () => {
  const db = await seed();
  assert.deepEqual(run(db, { q: "ANN" }).map((x) => x.uid), ["ann"]);
  assert.deepEqual(run(db, { q: "100%" }).map((x) => x.uid), ["dup"]);
  assert.deepEqual(run(db, { q: "%" }).map((x) => x.uid), ["dup"]);
  assert.deepEqual(run(db, { q: "zzz" }), []);
});

test("schoolId filter cannot widen scope; limit is clamped", async () => {
  const db = await seed();
  assert.deepEqual(run(db, { schoolId: "B" }), []);
  assert.equal(run(db, { schoolId: "A", limit: 1 }).length, 1);
  assert.equal(directoryParams("me", { limit: 99999 }).at(-1), 200);
  assert.equal(directoryParams("me", { limit: "abc" }).at(-1), 50);
  assert.equal(canonRole("weird"), "Mentee");
});
