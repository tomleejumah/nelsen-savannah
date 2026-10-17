import assert from "node:assert/strict";
import test from "node:test";
import {
  canonicalCourseLiveSchool,
  assertLiveHostScope,
  effectiveLiveAudienceScope,
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
