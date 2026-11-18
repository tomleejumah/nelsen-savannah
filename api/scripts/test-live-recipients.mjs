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

test("school/course notifications follow active membership and course roles", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.exec(`
      CREATE TABLE school_memberships (
        uid TEXT, school_id TEXT, status TEXT, role TEXT, updated_at BIGINT
      );
      CREATE TABLE users_mirror (uid TEXT, active_school_id TEXT);
      CREATE TABLE enrollments (uid TEXT, track_id TEXT, status TEXT);
      CREATE TABLE track_mentors (uid TEXT, track_id TEXT);
    `);
    const memberships = [
      ["learner_a", "school-a", "active", "Mentee", 1],
      ["mentor_a", "school-a", "active", "Mentor", 1],
      ["inactive", "school-a", "applied", "Mentee", 1],
      ["suspended", "school-a", "active", "Mentee", 1],
      ["cross_school", "school-b", "active", "Mentee", 1],
      ["switched", "school-a", "active", "Mentee", 1],
      ["switched", "school-b", "active", "Mentee", 2],
      ["learner_without_enrollment", "school-a", "active", "Mentee", 1],
      ["admin_a", "school-a", "active", "SchoolAdmin", 1],
      ["former_mentor", "school-a", "active", "Mentee", 1],
      ["dual_legacy", "school-a", "active", "Mentee", 1],
      ["dual_legacy", "school-b", "active", "Mentee", 2],
    ];
    for (const row of memberships) {
      db.run("INSERT INTO school_memberships VALUES (?,?,?,?,?)", row);
    }
    for (const row of [
      ["learner_a", "school-a"], ["mentor_a", "school-a"],
      ["inactive", "school-a"], ["suspended", "school-a"],
      ["cross_school", "school-b"], ["switched", "school-b"],
      ["learner_without_enrollment", "school-a"],
      ["admin_a", "school-a"], ["former_mentor", "school-a"],
      ["dual_legacy", null],
    ]) db.run("INSERT INTO users_mirror VALUES (?,?)", row);
    for (const row of [
      ["learner_a", "course-a", "in_progress"],
      ["inactive", "course-a", "in_progress"],
      ["cross_school", "course-a", "in_progress"],
      ["switched", "course-a", "in_progress"],
      ["suspended", "course-a", "suspended"],
      ["dual_legacy", "course-a", "in_progress"],
      ["learner_without_enrollment", "course-b", "in_progress"],
    ]) db.run("INSERT INTO enrollments VALUES (?,?,?)", row);
    db.run("INSERT INTO track_mentors VALUES (?,?)", ["mentor_a", "course-a"]);
    db.run("INSERT INTO track_mentors VALUES (?,?)", ["former_mentor", "course-a"]);

    assert.deepEqual(recipients(db, {
      audienceScope: "course", schoolId: "school-a", trackId: "course-a",
    }), ["admin_a", "dual_legacy", "learner_a", "mentor_a", "switched"],
    "all actively affiliated enrolled learners, current mentors and school admins get course notification");
    assert.deepEqual(recipients(db, {
      audienceScope: "school", schoolId: "school-a",
    }), [
      "admin_a", "dual_legacy", "former_mentor", "learner_a",
      "learner_without_enrollment", "mentor_a", "suspended", "switched",
    ]);
    assert.deepEqual(recipients(db, {
      audienceScope: "school", schoolId: "school-b",
    }), ["cross_school", "dual_legacy", "switched"],
    "all active members receive school alerts even when another school is selected");

    // Assigned mentor who was downgraded to Mentee cannot receive a
    // course alert solely because an old track_mentors row still exists.
    assert.ok(!recipients(db, {
      audienceScope: "course", schoolId: "school-a", trackId: "course-a",
    }).includes("former_mentor"));

    assert.equal(liveRecipientQuery({ audienceScope: "course", trackId: "course-a" }), null);
    assert.equal(liveRecipientQuery({ audienceScope: "unknown", schoolId: "school-a" }), null);
    assert.deepEqual(recipients(db, { audienceScope: "platform" }), [
      "admin_a", "cross_school", "dual_legacy", "former_mentor", "inactive",
      "learner_a", "learner_without_enrollment", "mentor_a", "suspended", "switched",
    ]);
  } finally {
    db.close();
  }
});
