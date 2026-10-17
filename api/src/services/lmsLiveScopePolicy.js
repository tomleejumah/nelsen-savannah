/**
 * Pure tenant/role rules used by the course/school live-creation API.
 * Keeping these free of Firebase/DB state allows regression testing without
 * requiring credentials in PR CI.
 */
function denied(message, status = 403) {
  const error = new Error(message);
  error.status = status;
  return error;
}

export function canonicalCourseLiveSchool(requestedSchoolId, courseSchoolId, activeSchoolId) {
  const requested = String(requestedSchoolId || "").trim();
  const canonical = String(courseSchoolId || "").trim();
  const active = String(activeSchoolId || "").trim();
  if (canonical && requested && canonical !== requested) {
    throw denied("Course does not belong to the requested school", 400);
  }
  const selected = canonical || requested || active;
  if (!selected) {
    throw denied("Course must have a school before going live", 400);
  }
  return selected;
}

export function assertLiveHostScope({ role, scope, schoolId, activeSchoolId, memberActive }) {
  const actorRole = String(role || "").trim();
  if (actorRole === "Admin" || actorRole === "SuperAdmin") return;

  if (actorRole !== "Mentor" && actorRole !== "SchoolAdmin") {
    throw denied("Only an authorized mentor or school administrator can host live");
  }
  if (!schoolId || !activeSchoolId || schoolId !== activeSchoolId || !memberActive) {
    throw denied("Switch to an active school membership before hosting its live session");
  }
  if (actorRole === "Mentor" && scope === "school") {
    throw denied("Only a school administrator can host a school-wide live session");
  }
}
