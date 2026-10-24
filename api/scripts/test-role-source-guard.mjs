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
console.log("Role source guard passed");
