import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import { liveRecipientQuery } from "../src/services/lmsLiveRecipientQueries.js";

function recipients(db, event) {
  const query = liveRecipientQuery(event);
  if (!query) return [];
  const stmt = db.prepare(query.sql);
  try {
    stmt.bind(query.params);
    const users = [];
    while (stmt.step()) users.push(String(stmt.getAsObject().uid));
    return users.sort();
  } finally {
    stmt.free();
  }
}

test("school and course alerts enforce active membership, active school, enrollment and mentor assignment", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.exec(`
      CREATE TABLE school_memberships (uid TEXT, school_id TEXT, status TEXT);
      CREATE TABLE users_mirror (uid TEXT, active_school_id TEXT);
      CREATE TABLE enrollments (uid TEXT, track_id TEXT, status TEXT);
      CREATE TABLE track_mentors (uid TEXT, track_id TEXT);
    `);
    const memberships = [
      ["learner_a", "school-a", "active"],
      ["mentor_a", "school-a", "active"],
      ["inactive", "school-a", "applied"],
      ["suspended", "school-a", "active"],
      ["cross_school", "school-b", "active"],
      ["switched", "school-a", "active"],
      ["switched", "school-b", "active"],
      ["learner_without_enrollment", "school-a", "active"],
    ];
    for (const row of memberships) db.run("INSERT INTO school_memberships VALUES (?,?,?)", row);
    for (const row of [
      ["learner_a", "school-a"], ["mentor_a", "school-a"],
      ["inactive", "school-a"], ["suspended", "school-a"],
      ["cross_school", "school-b"], ["switched", "school-b"],
      ["learner_without_enrollment", "school-a"],
    ]) db.run("INSERT INTO users_mirror VALUES (?,?)", row);
    for (const row of [
      ["learner_a", "course-a", "in_progress"],
      ["inactive", "course-a", "in_progress"],
      ["cross_school", "course-a", "in_progress"],
      ["switched", "course-a", "in_progress"],
      ["suspended", "course-a", "suspended"],
      ["learner_without_enrollment", "course-b", "in_progress"],
    ]) db.run("INSERT INTO enrollments VALUES (?,?,?)", row);
    db.run("INSERT INTO track_mentors VALUES (?,?)", ["mentor_a", "course-a"]);

    assert.deepEqual(recipients(db, { audienceScope: "course", schoolId: "school-a", trackId: "course-a" }), [
      "learner_a", "mentor_a",
    ]);
    assert.deepEqual(recipients(db, { audienceScope: "school", schoolId: "school-a" }), [
      "learner_a", "learner_without_enrollment", "mentor_a", "suspended",
    ]);
    assert.deepEqual(recipients(db, { audienceScope: "school", schoolId: "school-b" }), [
      "cross_school", "switched",
    ]);
    assert.equal(liveRecipientQuery({ audienceScope: "course", trackId: "course-a" }), null);
    assert.equal(liveRecipientQuery({ audienceScope: "unknown", schoolId: "school-a" }), null);
    assert.deepEqual(recipients(db, { audienceScope: "platform" }), [
      "cross_school", "inactive", "learner_a", "learner_without_enrollment",
      "mentor_a", "suspended", "switched",
    ]);
  } finally {
    db.close();
  }
});
