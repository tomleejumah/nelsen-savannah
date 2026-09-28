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
    blurb: "Mentors, courses, cohorts, students, billing",
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
    shells: ["student"],
  },
  {
    id: "coursework",
    label: "Coursework",
    blurb: "Assigned work and submissions",
    to: "/learning/coursework",
    shells: ["student"],
  },
  {
    id: "certificates",
    label: "Certificates",
    blurb: "Tracks you’ve completed",
    to: "/learning/certificates",
    shells: ["student"],
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
    id: "courses",
    label: "Your courses",
    blurb: "Open a course for students and content",
    to: "/teach",
    hash: "courses",
    shells: ["mentor", "school", "admin"],
  },
  {
    id: "payouts",
    label: "Your payouts",
    blurb: "School ledger payouts",
    to: "/teach",
    hash: "payouts",
    shells: ["mentor"],
  },
  {
    id: "dashboard",
    label: "School hub",
    blurb: "Mentors, courses, cohorts, students, billing",
    to: "/school",
    shells: ["school", "admin"],
  },
  {
    id: "mentors",
    label: "Mentors",
    blurb: "Invite, applications, escalate, assign courses",
    to: "/school/mentors",
    shells: ["school", "admin"],
  },
  {
    id: "intakes",
    label: "Courses & cohorts",
    blurb: "Catalog, price, cohorts nested under a course",
    to: "/school/coursework",
    shells: ["school", "admin"],
  },
  {
    id: "cms",
    label: "Catalog CMS",
    blurb: "Publish tracks, modules, lessons",
    to: "/school/coursework",
    shells: ["school", "admin"],
  },
  {
    id: "students",
    label: "Students",
    blurb: "Invite, roster, CSV, progress, at-risk",
    to: "/school/students",
    shells: ["school", "admin"],
  },
  {
    id: "roster-import",
    label: "Roster CSV",
    blurb: "Bulk import on students screen",
    to: "/school/students",
    shells: ["school", "admin"],
  },
  {
    id: "branding",
    label: "School settings",
    blurb: "Logo and accent branding",
    to: "/school/settings",
    shells: ["school", "admin"],
  },
  {
    id: "payments",
    label: "Billing",
    blurb: "Seats (card coming soon), balance, payouts",
    to: "/school/finances",
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
