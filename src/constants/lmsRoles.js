/** LMS roles — Android RTDB roles/{uid} + web shells */

export const ROLES = Object.freeze({
  Mentee: "Mentee",
  Mentor: "Mentor",
  SchoolAdmin: "SchoolAdmin",
  SuperAdmin: "SuperAdmin",
  /** @deprecated legacy alias — normalizeRole maps to SuperAdmin */
  Admin: "Admin",
});

/** Default Nelsen digital school tenant (L0 stub until L6 schools table). */
export const DEFAULT_SCHOOL_ID = "nelsen-digital";
export const DEFAULT_SCHOOL_NAME = "Nelsen Digital School";

export const SHELLS = Object.freeze({
  student: "student",
  mentor: "mentor",
  school: "school",
  admin: "admin",
});

const SUPER_ADMIN_CAPS = Object.freeze({
  browseCatalog: true,
  enroll: true,
  learn: true,
  submitAssignments: true,
  viewOwnProgress: true,
  viewOwnCertificates: true,
  chatWithMentor: true,
  createCourses: true,
  markAssignments: true,
  viewMenteeProgress: true,
  manageSchoolUsers: true,
  manageUsers: true,
  publishTracks: true,
  moderateContent: true,
  viewOrgDashboards: true,
  manageSchools: true,
  viewBilling: true,
});

export const ROLE_CAPABILITIES = Object.freeze({
  Mentee: {
    browseCatalog: true,
    enroll: true,
    learn: true,
    submitAssignments: true,
    viewOwnProgress: true,
    viewOwnCertificates: true,
    chatWithMentor: true,
    createCourses: false,
    markAssignments: false,
    viewMenteeProgress: false,
    manageSchoolUsers: false,
    manageUsers: false,
    publishTracks: false,
    manageSchools: false,
    viewBilling: false,
  },
  Mentor: {
    browseCatalog: true,
    enroll: true,
    learn: true,
    submitAssignments: true,
    viewOwnProgress: true,
    viewOwnCertificates: true,
    chatWithMentor: false,
    createCourses: true,
    markAssignments: true,
    viewMenteeProgress: true,
    manageSchoolUsers: false,
    manageUsers: false,
    publishTracks: false,
    manageSchools: false,
    viewBilling: false,
  },
  SchoolAdmin: {
    browseCatalog: true,
    enroll: true,
    learn: true,
    submitAssignments: true,
    viewOwnProgress: true,
    viewOwnCertificates: true,
    chatWithMentor: true,
    createCourses: true,
    markAssignments: true,
    viewMenteeProgress: true,
    manageSchoolUsers: true,
    manageUsers: false,
    publishTracks: true,
    manageSchools: false,
    viewBilling: true,
  },
  SuperAdmin: SUPER_ADMIN_CAPS,
  /** @deprecated legacy alias — same as SuperAdmin */
  Admin: SUPER_ADMIN_CAPS,
});

export function normalizeRole(role) {
  if (role === ROLES.Mentor) return ROLES.Mentor;
  if (role === ROLES.SchoolAdmin) return ROLES.SchoolAdmin;
  if (role === ROLES.SuperAdmin || role === ROLES.Admin) return ROLES.SuperAdmin;
  if (role === ROLES.Mentee) return ROLES.Mentee;
  return ROLES.Mentee;
}

export function capabilitiesFor(role) {
  return ROLE_CAPABILITIES[normalizeRole(role)] ?? ROLE_CAPABILITIES.Mentee;
}

/** Product shell for routing UIs. */
export function shellFor(role) {
  const r = normalizeRole(role);
  if (r === ROLES.SuperAdmin) return SHELLS.admin;
  if (r === ROLES.SchoolAdmin) return SHELLS.school;
  if (r === ROLES.Mentor) return SHELLS.mentor;
  return SHELLS.student;
}

export function isSuperAdmin(role) {
  return normalizeRole(role) === ROLES.SuperAdmin;
}

export function isSchoolAdmin(role) {
  return normalizeRole(role) === ROLES.SchoolAdmin;
}

export function canMarkAssignments(role) {
  const r = normalizeRole(role);
  return (
    r === ROLES.Mentor ||
    r === ROLES.SchoolAdmin ||
    r === ROLES.SuperAdmin
  );
}

export function canPublishTracks(role) {
  const r = normalizeRole(role);
  return (
    r === ROLES.SchoolAdmin ||
    r === ROLES.SuperAdmin ||
    r === ROLES.Mentor
  );
}

/**
 * Expand legacy "Admin" in allow-lists to SuperAdmin.
 * SchoolAdmin is not implied by Admin.
 */
export function expandAllowedRoles(allowed) {
  const set = new Set(allowed.map(normalizeRole));
  if (allowed.includes("Admin") || set.has(ROLES.SuperAdmin)) {
    set.add(ROLES.SuperAdmin);
  }
  return set;
}
