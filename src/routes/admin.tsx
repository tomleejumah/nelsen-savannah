import { createFileRoute } from "@tanstack/react-router";

import { RoleShellPage } from "@/components/lms/RoleShellPage";

export const Route = createFileRoute("/admin")({
  head: () => ({
    meta: [
      { title: "Super admin — Nelsen Savannah LMS" },
      {
        name: "description",
        content: "Platform super admin — schools, global catalog, billing.",
      },
    ],
  }),
  component: AdminPage,
});

function AdminPage() {
  return (
    <RoleShellPage
      shell="admin"
      title="Super admin"
      blurb="Nelsen owners only. Create schools, appoint school admins, platform billing (L4–L7)."
    >
      <ul className="space-y-2 text-sm text-muted-foreground">
        <li>· Schools list & create — L4 / L6</li>
        <li>· Appoint school admins — L4</li>
        <li>· Global catalog CMS — L5</li>
        <li>· Billing & seats — L7</li>
      </ul>
    </RoleShellPage>
  );
}
