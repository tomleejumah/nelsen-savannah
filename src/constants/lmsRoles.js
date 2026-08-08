/** Mirror Android roles/{uid} + nelsen-savanna ROLE_CAPABILITIES */

export const ROLES = Object.freeze({
  Mentee: "Mentee",
  Mentor: "Mentor",
  Admin: "Admin",
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
    manageUsers: false,
    publishTracks: false,
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
    manageUsers: false,
    publishTracks: false,
  },
  Admin: {
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
    manageUsers: true,
    publishTracks: true,
    moderateContent: true,
    viewOrgDashboards: true,
  },
});

export function normalizeRole(role) {
  if (role === ROLES.Mentor || role === ROLES.Admin || role === ROLES.Mentee) {
    return role;
  }
  return ROLES.Mentee;
}

export function capabilitiesFor(role) {
  return ROLE_CAPABILITIES[normalizeRole(role)] ?? ROLE_CAPABILITIES.Mentee;
}
