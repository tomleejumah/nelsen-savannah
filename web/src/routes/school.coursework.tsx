import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import { CohortIntakesPanel } from "@/components/lms/CohortIntakesPanel";
import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { SchoolAdminChrome } from "@/components/lms/schoolAdmin/SchoolAdminChrome";
import { useSchoolAdmin } from "@/components/lms/schoolAdmin/useSchoolAdmin";
import type { MeDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/school/coursework")({
  head: () => ({
    meta: [{ title: "Courses — School admin" }],
  }),
  component: CourseworkPage,
});

function CourseworkPage() {
  return (
    <RoleShellPage
      shell="school"
      title="Courses"
      blurb="Catalog → course price → cohorts and cohort tools."
      wide
    >
      {({ user, me }) => <CourseworkConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function CourseworkConsole({ user, me }: { user: User; me: MeDto }) {
  const a = useSchoolAdmin(user, me);

  return (
    <SchoolAdminChrome
      title="Courses"
      blurb="Create courses, set price, then nest cohort tools under a selected course."
      msg={a.msg}
    >
      {a.error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {a.error}
        </p>
      ) : null}

      <section>
        <CatalogCmsPanel user={user} schoolId={a.schoolId} allowCreateTrack />
      </section>

      <CohortIntakesPanel
        user={user}
        schoolId={a.schoolId}
        tracks={a.tracks}
        mentees={a.mentees}
      />
    </SchoolAdminChrome>
  );
}
