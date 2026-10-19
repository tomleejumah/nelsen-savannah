import assert from "node:assert/strict";
import test from "node:test";
import {
  canonicalCourseLiveSchool,
  assertLiveHostScope,
  effectiveLiveAudienceScope,
  assertHubEventMutationScope,
  effectiveSchoolStaffRole,
  selectedActiveSchool,
} from "../src/services/lmsLiveScopePolicy.js";

test("live creation rejects cross-school course ownership even for multi-school users", () => {
  assert.equal(canonicalCourseLiveSchool("", "school-a", "school-a"), "school-a");
  assert.equal(canonicalCourseLiveSchool("school-a", "school-a", "school-a"), "school-a");
  assert.equal(canonicalCourseLiveSchool("", "", "school-a"), "school-a");
  assert.throws(
    () => canonicalCourseLiveSchool("school-b", "school-a", "school-b"),
    (error) => error.status === 400 && /does not belong/.test(error.message),
  );
  assert.throws(
    () => canonicalCourseLiveSchool("", "", ""),
    (error) => error.status === 400,
  );
});

test("mentor can host assigned course scope only when active school membership matches", () => {
  const allowed = { role: "Mentor", scope: "course", schoolId: "school-a",
    activeSchoolId: "school-a", memberActive: true };
  assert.doesNotThrow(() => assertLiveHostScope(allowed));
  assert.throws(
    () => assertLiveHostScope({ ...allowed, scope: "school" }),
    (error) => error.status === 403 && /school administrator/.test(error.message),
  );
  assert.throws(
    () => assertLiveHostScope({ ...allowed, activeSchoolId: "school-b" }),
    (error) => error.status === 403,
  );
  assert.throws(
    () => assertLiveHostScope({ ...allowed, memberActive: false }),
    (error) => error.status === 403,
  );
  assert.throws(
    () => assertLiveHostScope({ ...allowed, role: "Mentee" }),
    (error) => error.status === 403,
  );
});

test("school admin hosts in active school; platform admin retains platform broadcasts", () => {
  assert.doesNotThrow(() => assertLiveHostScope({
    role: "SchoolAdmin", scope: "school", schoolId: "school-a",
    activeSchoolId: "school-a", memberActive: true,
  }));
  assert.throws(() => assertLiveHostScope({
    role: "SchoolAdmin", scope: "course", schoolId: "school-b",
    activeSchoolId: "school-a", memberActive: true,
  }), (error) => error.status === 403);
  assert.doesNotThrow(() => assertLiveHostScope({
    role: "SuperAdmin", scope: "platform", schoolId: "",
    activeSchoolId: "", memberActive: false,
  }));
});

test("legacy live events do not silently become platform-wide broadcasts", () => {
  assert.equal(effectiveLiveAudienceScope({
    eventType: "live", audienceScope: null, schoolId: "school-a", trackId: "",
  }), "school");
  assert.equal(effectiveLiveAudienceScope({
    eventType: "live", audienceScope: null, schoolId: "school-a", trackId: "course-a",
  }), "course");
  assert.equal(effectiveLiveAudienceScope({
    eventType: "live", audienceScope: null, schoolId: "", trackId: "",
  }), null);
  assert.equal(effectiveLiveAudienceScope({
    eventType: "live", audienceScope: "platform", schoolId: "",
  }), "platform");
  assert.equal(effectiveLiveAudienceScope({
    eventType: "event", audienceScope: "school", schoolId: "school-a",
  }), null);
});

test("only the event owner, platform admin or current-school administrator may mutate events", () => {
  const event = {
    actorUid: "mentor-b",
    createdBy: "mentor-a",
    schoolId: "school-a",
    activeSchoolId: "school-a",
    memberActive: true,
  };
  assert.throws(
    () => assertHubEventMutationScope({ ...event, role: "Mentor" }),
    (error) => error.status === 403,
  );
  assert.throws(
    () => assertHubEventMutationScope({ ...event, role: "SchoolAdmin", activeSchoolId: "school-b" }),
    (error) => error.status === 403,
  );
  assert.throws(
    () => assertHubEventMutationScope({ ...event, role: "SchoolAdmin", memberActive: false }),
    (error) => error.status === 403,
  );
  assert.doesNotThrow(
    () => assertHubEventMutationScope({ ...event, role: "SchoolAdmin" }),
  );
  assert.doesNotThrow(
    () => assertHubEventMutationScope({ ...event, role: "Mentor", actorUid: "mentor-a" }),
  );
  assert.doesNotThrow(
    () => assertHubEventMutationScope({ ...event, role: "Admin", activeSchoolId: "" }),
  );
  assert.throws(
    () => assertHubEventMutationScope({ ...event, role: "SchoolAdmin", schoolId: "" }),
    (error) => error.status === 403,
  );
  assert.throws(
    () => assertHubEventMutationScope({ ...event, role: "SchoolAdmin", actorUid: "" }),
    (error) => error.status === 401,
  );
});

test("staff privileges follow the active school's membership, not a global role", () => {
  const local = (role, status = "active") => ({ role, status });

  assert.equal(effectiveSchoolStaffRole("SchoolAdmin", local("Mentee")), "Mentee",
    "SchoolAdmin from school A is a Mentee in school B");
  assert.equal(effectiveSchoolStaffRole("Mentor", local("SchoolAdmin")), "SchoolAdmin",
    "explicit school membership may grant SchoolAdmin even when global role is Mentor");
  assert.equal(effectiveSchoolStaffRole("Mentee", local("Mentor")), "Mentor",
    "assigned local Mentor is respected even with global Mentee");
  assert.equal(effectiveSchoolStaffRole("Mentor", local(null)), "Mentee",
    "missing membership role defaults to Mentee");
  assert.equal(effectiveSchoolStaffRole("Mentor", local("Mentor", "applied")), "Mentee",
    "pending memberships never confer teaching rights");
  assert.equal(effectiveSchoolStaffRole("SchoolAdmin", null), "Mentee",
    "global SchoolAdmin without school membership has no local rights");
  assert.equal(effectiveSchoolStaffRole("Mentee", local("SuperAdmin")), "SchoolAdmin",
    "a school membership alone must never grant platform super-admin");
  assert.equal(effectiveSchoolStaffRole("SuperAdmin", null), "SuperAdmin",
    "actual platform administrators retain cross-school privileges");
  assert.equal(selectedActiveSchool("school-b", ["school-a", "school-b"]), "school-b");
  assert.equal(selectedActiveSchool("school-b", ["school-a"]), "",
    "explicit selection of a non-member school never falls back to school A");
  assert.equal(selectedActiveSchool("", ["school-a"]), "school-a");
});
