import assert from "node:assert/strict";
import test from "node:test";
import initSqlJs from "sql.js";
import {
  SYNC_SELECTED_SCHOOL_ROLE_SQL,
  selectedSchoolMembershipRole,
} from "../src/services/lmsSchoolRoleWrite.js";

test("explicit role changes update one active school membership, never another school's role", async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  try {
    db.exec(`CREATE TABLE school_memberships (
      uid TEXT, school_id TEXT, role TEXT, status TEXT, updated_at BIGINT
    )`);
    for (const row of [
      ["user-1", "school-a", "Mentee", "active", 0],
      ["user-1", "school-b", "Mentee", "active", 0],
      ["user-1", "school-c", "Mentee", "applied", 0],
    ]) db.run("INSERT INTO school_memberships VALUES (?,?,?,?,?)", row);

    const apply = (uid, school, role) => {
      db.run(SYNC_SELECTED_SCHOOL_ROLE_SQL, [role, 1, uid, school]);
      return db.getRowsModified();
    };
    assert.equal(apply("user-1", "school-a", "Mentor"), 1);
    const rows = db.exec("SELECT school_id, role FROM school_memberships WHERE uid='user-1' ORDER BY school_id")[0].values;
    assert.deepEqual(rows, [
      ["school-a", "Mentor"],
      ["school-b", "Mentee"],
      ["school-c", "Mentee"],
    ]);
    assert.equal(apply("user-1", "school-c", "SchoolAdmin"), 0,
      "pending membership is never promoted through global-role updates");
    assert.equal(apply("unknown", "school-a", "Mentor"), 0,
      "global role changes must never create a new membership");
  } finally {
    db.close();
  }
});

test("platform SuperAdmin promotions do not masquerade as school-specific grants", () => {
  assert.equal(selectedSchoolMembershipRole("SuperAdmin"), null);
  assert.equal(selectedSchoolMembershipRole("Admin"), null);
  assert.equal(selectedSchoolMembershipRole("SchoolAdmin"), "SchoolAdmin");
  assert.equal(selectedSchoolMembershipRole("Mentor"), "Mentor");
  assert.equal(selectedSchoolMembershipRole("Mentee"), "Mentee");
});
