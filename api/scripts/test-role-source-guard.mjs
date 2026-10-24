// Source-level regression guard for the legacy role reconciliation boundary.
// Integration/RTDB rules acceptance is tracked separately in the roadmap.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const source = readFileSync(new URL("../src/services/lmsMeService.js", import.meta.url), "utf8");
const getMe = source.slice(source.indexOf("export async function getMe("), source.indexOf("export async function listUploadedMaterials("));
assert.ok(getMe.includes("const role = normalizeRole(user?.role);"), "primary roles must determine global role");
assert.ok(!getMe.includes("setUserRole("), "GET /lms/me must not mutate global role based on RTDB");
assert.ok(!getMe.includes("readRoleFromRtdb("), "GET /lms/me must not use legacy RTDB as a role authority");
assert.match(getMe, /const role = ROLES\.Mentee;/, "RTDB-only fallback must be unprivileged");
const membership = readFileSync(new URL("../src/services/lmsMembershipService.js", import.meta.url), "utf8");
const access = membership.slice(membership.indexOf("export async function getLearningAccess("), membership.indexOf("export async function assertLearningAccess("));
assert.ok(access.includes("normalizeRole(membership.role || ROLES.Mentee)"), "missing school role must not inherit global staff title");
const schools = readFileSync(new URL("../src/services/lmsSchoolService.js", import.meta.url), "utf8");
const patchRole = schools.slice(schools.indexOf("export async function patchSchoolMemberRole("), schools.indexOf("export async function updateSchoolBranding("));
assert.match(patchRole, /UPDATE school_memberships SET role = \?/, "school role assignment must update membership role");
assert.match(patchRole, /setUserRole\(targetUid, nextRole\)/, "school role assignment must synchronize legacy global role");
assert.match(patchRole, /if \(!isSuperAdmin\(globalRole\)\)/, "school assignment must preserve platform SuperAdmin privilege");
assert.match(patchRole, /activeSchoolRole: nextRole/, "role assignment response must return school role");
assert.match(patchRole, /status = 'active' LIMIT 1/, "only active school memberships may be assigned");
console.log("Role source guard passed");
