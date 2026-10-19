/**
 * Legacy events may predate explicit audience_scope. Restrict such lives to
 * their recorded school/course instead of treating missing scope as public.
 * Without an owning school they remain host/admin-only.
 */
export function effectiveLiveAudienceScope(event) {
  if (!event || event.eventType !== "live") return null;
  if (["platform", "school", "course"].includes(event.audienceScope)) {
    return event.audienceScope;
  }
  if (event.trackId && event.schoolId) return "course";
  if (event.schoolId) return "school";
  return null;
}

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

/**
 * A global Mentor/SchoolAdmin role must never grant mutation rights over
 * another school's Hub events. The owner may manage their own event; a
 * SchoolAdmin may manage only events in their CURRENT active school and only
 * while holding active membership there.
 *
 * Legacy events without school_id are owner/platform-admin only.
 */
export function assertHubEventMutationScope({
  role, actorUid, createdBy, schoolId, activeSchoolId, memberActive,
}) {
  if (!actorUid) throw denied("Authenticated event editor required", 401);
  if (role === "Admin" || role === "SuperAdmin") return;
  if (String(createdBy || "") === String(actorUid)) return;
  const currentSchool = String(activeSchoolId || "");
  const eventSchool = String(schoolId || "");
  if (role === "SchoolAdmin" && eventSchool && currentSchool === eventSchool && memberActive) {
    return;
  }
  throw denied("Only the event host or active-school administrator can change this event", 403);
}
