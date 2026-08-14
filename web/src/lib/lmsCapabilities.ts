import type { MeDto } from "@/lib/lmsApi";
import {
  canAccessShell,
  shellFromMe,
  shellHomePath,
  type LmsShell,
} from "@/lib/lmsRoles";

export type LmsTool = {
  id: string;
  label: string;
  blurb: string;
  /** Route path (TanStack to) */
  to: string;
  /** In-page hash without # */
  hash?: string;
  shells: LmsShell[];
};

export type LmsWorkspace = {
  shell: LmsShell;
  label: string;
  blurb: string;
  to: string;
};

export const LMS_WORKSPACES: LmsWorkspace[] = [
  {
    shell: "student",
    label: "Learning",
    blurb: "Catalog, coursework, certificates",
    to: "/learning",
  },
  {
    shell: "mentor",
    label: "Teach",
    blurb: "Queue, students, assign work",
    to: "/teach",
  },
  {
    shell: "school",
    label: "School",
    blurb: "Roster, CMS, dashboard",
    to: "/school",
  },
  {
    shell: "admin",
    label: "Admin",
    blurb: "Schools and platform catalog",
    to: "/admin",
  },
];

export const LMS_TOOLS: LmsTool[] = [
  {
    id: "catalog",
    label: "Catalog",
    blurb: "Browse and enroll in tracks",
    to: "/learning",
    shells: ["student", "mentor", "school", "admin"],
  },
  {
    id: "coursework",
    label: "Coursework",
    blurb: "Assigned work and submissions",
    to: "/learning/coursework",
    shells: ["student", "mentor", "school", "admin"],
  },
  {
    id: "certificates",
    label: "Certificates",
    blurb: "Tracks you’ve completed",
    to: "/learning/certificates",
    shells: ["student", "mentor", "school", "admin"],
  },
  {
    id: "profile",
    label: "Profile",
    blurb: "Name, email, enrollments, schools, payments",
    to: "/profile",
    shells: ["student", "mentor", "school", "admin"],
  },
  {
    id: "queue",
    label: "Marking queue",
    blurb: "Pass or fail pending submissions",
    to: "/teach",
    hash: "queue",
    shells: ["mentor", "school", "admin"],
  },
  {
    id: "students",
    label: "Students",
    blurb: "Mentee progress by track",
    to: "/teach",
    hash: "students",
    shells: ["mentor", "school", "admin"],
  },
  {
    id: "assign",
    label: "Assign work",
    blurb: "Send coursework to a track or uid",
    to: "/teach",
    hash: "assign",
    shells: ["mentor", "school", "admin"],
  },
  {
    id: "dashboard",
    label: "Dashboard",
    blurb: "Roster health and at-risk students",
    to: "/school",
    hash: "dashboard",
    shells: ["school", "admin"],
  },
  {
    id: "cms",
    label: "Catalog CMS",
    blurb: "Publish tracks, modules, lessons",
    to: "/school",
    hash: "cms",
    shells: ["school", "admin"],
  },
  {
    id: "people",
    label: "Mentors & mentees",
    blurb: "Register people for your school",
    to: "/school",
    hash: "people",
    shells: ["school", "admin"],
  },
  {
    id: "roster-import",
    label: "Roster CSV",
    blurb: "Bulk import members",
    to: "/school",
    hash: "roster",
    shells: ["school", "admin"],
  },
  {
    id: "branding",
    label: "Branding",
    blurb: "Logo and accent for your wing",
    to: "/school",
    hash: "branding",
    shells: ["school", "admin"],
  },
  {
    id: "payments",
    label: "Payments UI",
    blurb: "Seat checkout (provider TBD)",
    to: "/school",
    hash: "payments",
    shells: ["school", "admin"],
  },
  {
    id: "admin-cms",
    label: "Global CMS",
    blurb: "Platform catalog + stats",
    to: "/admin",
    hash: "cms",
    shells: ["admin"],
  },
  {
    id: "schools",
    label: "Schools",
    blurb: "List all school tenants",
    to: "/admin",
    hash: "schools",
    shells: ["admin"],
  },
  {
    id: "create-school",
    label: "Create school",
    blurb: "Spin up a new tenant",
    to: "/admin",
    hash: "create-school",
    shells: ["admin"],
  },
  {
    id: "appoint",
    label: "Appoint school admin",
    blurb: "Assign a SchoolAdmin uid",
    to: "/admin",
    hash: "appoint",
    shells: ["admin"],
  },
];

/** Workspaces the signed-in user may open. */
export function workspacesForMe(me: MeDto | null | undefined): LmsWorkspace[] {
  return LMS_WORKSPACES.filter((w) => canAccessShell(me, w.shell));
}

/**
 * Tools for the current page shell (or primary shell if none).
 * Prefer tools whose shells include the active shell so school sees school CMS, not only admin CMS.
 */
export function toolsForShell(
  me: MeDto | null | undefined,
  activeShell?: LmsShell,
): LmsTool[] {
  const primary = shellFromMe(me);
  const focus = activeShell ?? primary;
  return LMS_TOOLS.filter((t) => {
    if (!t.shells.includes(focus)) return false;
    // Admin on /admin: include admin-* tools; on /school they already get school tools via focus
    return t.shells.some((s) => canAccessShell(me, s));
  });
}

export function primaryWorkspacePath(me: MeDto | null | undefined): string {
  return shellHomePath(shellFromMe(me));
}
