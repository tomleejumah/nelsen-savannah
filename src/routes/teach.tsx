import { createFileRoute, Link } from "@tanstack/react-router";

import { RoleShellPage } from "@/components/lms/RoleShellPage";

export const Route = createFileRoute("/teach")({
  head: () => ({
    meta: [
      { title: "Teach — Nelsen Savannah LMS" },
      {
        name: "description",
        content: "Mentor workspace — students, marking, and assignments.",
      },
    ],
  }),
  component: TeachPage,
});

function TeachPage() {
  return (
    <RoleShellPage
      shell="mentor"
      title="Teach"
      blurb="Mentor home. See your students, mark submissions, and assign coursework (L2–L3)."
    >
      <ul className="space-y-2 text-sm text-muted-foreground">
        <li>· Students & progress — coming in L2</li>
        <li>· Marking queue — coming in L2</li>
        <li>· Assign work — coming in L3</li>
      </ul>
      <Link
        to="/learning"
        className="mt-6 inline-flex text-sm font-medium text-maroon hover:underline"
      >
        Also browse learning catalog →
      </Link>
    </RoleShellPage>
  );
}
