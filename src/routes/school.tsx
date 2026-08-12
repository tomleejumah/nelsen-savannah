import { createFileRoute } from "@tanstack/react-router";

import { RoleShellPage } from "@/components/lms/RoleShellPage";

export const Route = createFileRoute("/school")({
  head: () => ({
    meta: [
      { title: "School admin — Nelsen Savannah LMS" },
      {
        name: "description",
        content: "School admin workspace — mentors, mentees, and school roster.",
      },
    ],
  }),
  component: SchoolPage,
});

function SchoolPage() {
  return (
    <RoleShellPage
      shell="school"
      title="School admin"
      blurb="Your school’s wing. Register mentors, create mentees, escalate staff (L4)."
    >
      <ul className="space-y-2 text-sm text-muted-foreground">
        <li>· Register / invite mentors — L4</li>
        <li>· Create / invite mentees — L4</li>
        <li>· Escalate staff → Mentor — L4</li>
        <li>· School catalog & seats — L5–L7</li>
      </ul>
    </RoleShellPage>
  );
}
