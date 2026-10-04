import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { SchoolAdminChrome } from "@/components/lms/schoolAdmin/SchoolAdminChrome";
import { YouTubeConnectionPanel } from "@/components/lms/YouTubeConnectionPanel";
import { useSchoolAdmin } from "@/components/lms/schoolAdmin/useSchoolAdmin";
import type { MeDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/school/settings")({
  head: () => ({
    meta: [{ title: "School settings — School admin" }],
  }),
  component: SettingsPage,
});

function SettingsPage() {
  return (
    <RoleShellPage
      shell="school"
      title="School settings"
      blurb="Branding and school-level appearance."
    >
      {({ user, me }) => <SettingsConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function SettingsConsole({ user, me }: { user: User; me: MeDto }) {
  const a = useSchoolAdmin(user, me);

  return (
    <SchoolAdminChrome
      title="School settings"
      blurb="Logo and accent color for this school's wing."
      msg={a.msg}
    >
      {a.error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {a.error}
        </p>
      ) : null}

      <YouTubeConnectionPanel
        user={user}
        schoolId={a.schoolId}
        title="School YouTube Live channel"
        blurb="Authorize this school's YouTube channel once. Mentors and school admins can then tap Go Live in Nelsen without creating or pasting a YouTube link."
      />

      <form onSubmit={(e) => void a.onBrand(e)} className="space-y-3">
        <h3 className="font-display text-lg font-semibold">Branding</h3>
        <input
          value={a.logoUrl}
          onChange={(e) => a.setLogoUrl(e.target.value)}
          placeholder="Logo URL"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input
          value={a.accent}
          onChange={(e) => a.setAccent(e.target.value)}
          placeholder="Accent color (#hex)"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <button
          type="submit"
          className="rounded-full border border-border px-5 py-2 text-sm"
        >
          Save branding
        </button>
      </form>
    </SchoolAdminChrome>
  );
}
