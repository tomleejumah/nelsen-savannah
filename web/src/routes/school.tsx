import { createFileRoute, Link, Outlet, useChildMatches } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { useSchoolAdmin } from "@/components/lms/schoolAdmin/useSchoolAdmin";
import type { MeDto } from "@/lib/lmsApi";

const AREAS = [
  {
    to: "/school/mentors" as const,
    label: "Mentors",
    blurb: "Invite, applications, escalate mentees, assign courses.",
  },
  {
    to: "/school/coursework" as const,
    label: "Courses",
    blurb: "Catalog → price → cohorts (tools live inside the cohort).",
  },
  {
    to: "/school/students" as const,
    label: "Students",
    blurb: "Invite, roster, CSV import, progress / at-risk.",
  },
  {
    to: "/school/finances" as const,
    label: "Billing",
    blurb: "Seats checkout (M-Pesa; card coming soon) and payouts.",
  },
  {
    to: "/school/settings" as const,
    label: "School settings",
    blurb: "Logo and accent branding.",
  },
];

export const Route = createFileRoute("/school")({
  head: () => ({
    meta: [
      { title: "School admin — Nelsen Savannah LMS" },
      {
        name: "description",
        content: "School admin — mentors, courses, cohorts, students, billing.",
      },
    ],
  }),
  component: SchoolLayout,
});

function SchoolLayout() {
  const childMatches = useChildMatches();
  if (childMatches.length > 0) return <Outlet />;
  return (
    <RoleShellPage
      shell="school"
      title="School admin"
      blurb="Mentors → Courses → Cohorts, plus students, billing, and settings."
    >
      {({ user, me }) => <SchoolHub user={user} me={me} />}
    </RoleShellPage>
  );
}

function SchoolHub({ user, me }: { user: User; me: MeDto }) {
  const { schoolId, dash, busy, error } = useSchoolAdmin(user, me);
  return (
    <div className="space-y-8">
      <p className="text-sm text-muted-foreground">
        School id: <span className="font-mono text-foreground">{schoolId}</span>
      </p>
      {error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-2xl border border-border/70 bg-card/50 px-4 py-4">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            Students
          </p>
          <p className="mt-1 font-display text-3xl font-semibold">
            {busy && !dash ? "…" : (dash?.mentees ?? 0)}
          </p>
        </div>
        <div className="rounded-2xl border border-border/70 bg-card/50 px-4 py-4">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            Mentors
          </p>
          <p className="mt-1 font-display text-3xl font-semibold">
            {busy && !dash ? "…" : (dash?.mentors ?? 0)}
          </p>
        </div>
        <div className="rounded-2xl border border-border/70 bg-card/50 px-4 py-4">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            Courses
          </p>
          <p className="mt-1 font-display text-3xl font-semibold">
            {busy && !dash ? "…" : (dash?.byCourse?.length ?? 0)}
          </p>
        </div>
      </div>
      <ul className="grid gap-3 sm:grid-cols-2">
        {AREAS.map((area) => (
          <li key={area.to}>
            <Link
              to={area.to}
              className="block rounded-2xl border border-border/70 bg-card/40 px-5 py-5 transition hover:border-ember/40 hover:bg-card"
            >
              <p className="font-display text-lg font-semibold text-foreground">
                {area.label}
              </p>
              <p className="mt-1 text-sm text-muted-foreground">{area.blurb}</p>
              <p className="mt-3 text-xs font-medium text-ember">Open →</p>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
