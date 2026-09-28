import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";
import { useEffect } from "react";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { SchoolAdminChrome } from "@/components/lms/schoolAdmin/SchoolAdminChrome";
import { SchoolInviteForm } from "@/components/lms/schoolAdmin/SchoolInviteForm";
import { SchoolPctBar } from "@/components/lms/schoolAdmin/SchoolPctBar";
import { useSchoolAdmin } from "@/components/lms/schoolAdmin/useSchoolAdmin";
import type { MeDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/school/students")({
  head: () => ({
    meta: [{ title: "Manage students — School admin" }],
  }),
  component: StudentsPage,
});

function StudentsPage() {
  return (
    <RoleShellPage
      shell="school"
      title="Manage students"
      blurb="Invite students and track progress in one place."
    >
      {({ user, me }) => <StudentsConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function StudentsConsole({ user, me }: { user: User; me: MeDto }) {
  const a = useSchoolAdmin(user, me);
  useEffect(() => {
    a.setInviteRole("Mentee");
    // eslint-disable-next-line react-hooks/exhaustive-deps -- default once per page
  }, []);

  return (
    <SchoolAdminChrome
      title="Manage students"
      blurb="Invite by email, approve joins, and watch progress."
      msg={a.msg}
      lastInviteUrl={a.lastInviteUrl}
      onCopyInvite={(url) => void a.copyInvite(url)}
    >
      {a.error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {a.error}
        </p>
      ) : null}

      <SchoolInviteForm
        emails={a.inviteEmails}
        onEmailsChange={a.setInviteEmails}
        role={a.inviteRole}
        onRoleChange={a.setInviteRole}
        onSubmit={(e) => void a.invitePeople(e)}
      />

      <section className="space-y-3">
        <h3 className="font-display text-lg font-semibold">Progress snapshot</h3>
        {a.busy && !a.dash ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : a.dash ? (
          <>
            <p className="text-sm text-muted-foreground">
              {a.dash.enrollments} enrollments · avg {a.dash.avgCompletion}%
              complete
            </p>
            {(a.dash.byCourse?.length ?? 0) > 0 ? (
              <ul className="grid gap-3 sm:grid-cols-2">
                {a.dash.byCourse.map((c) => (
                  <li
                    key={c.trackId}
                    className="rounded-2xl border border-border/70 bg-card/40 px-4 py-4 text-sm"
                  >
                    <p className="font-display font-semibold">{c.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {c.enrolled} enrolled
                    </p>
                    <SchoolPctBar label="Completion" pct={c.avgPercent} />
                    <SchoolPctBar
                      label="Assignments"
                      pct={c.avgAssignment ?? 0}
                    />
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">No courses yet.</p>
            )}
            {a.dash.atRisk.length > 0 ? (
              <ul className="rounded-2xl border border-border/70 bg-card px-4 py-3 text-sm">
                <li className="mb-2 font-medium">
                  At risk (&lt;40%, inactive 7d)
                </li>
                {a.dash.atRisk.slice(0, 12).map((row) => (
                  <li
                    key={`${row.uid}-${row.trackId}`}
                    className="py-1.5 text-muted-foreground"
                  >
                    <p className="font-medium text-foreground">
                      {row.displayName || row.uid}
                    </p>
                    {row.email ? (
                      <a
                        href={`mailto:${row.email}`}
                        className="block text-xs text-ember underline-offset-2 hover:underline"
                      >
                        {row.email}
                      </a>
                    ) : null}
                    <p className="text-xs">
                      {row.trackId} · {row.trackPercent}%
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">No at-risk students.</p>
            )}
          </>
        ) : null}
      </section>

      <section className="space-y-3">
        <h3 className="font-display text-lg font-semibold">Invites sent</h3>
        <p className="text-xs text-muted-foreground">
          Saved on the school roster until they join. Copy the link anytime —
          email delivery is best-effort for now.
        </p>
        {a.busy ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : a.pendingInvites.length === 0 ? (
          <p className="text-sm text-muted-foreground">No pending invites.</p>
        ) : (
          <ul className="divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {a.pendingInvites.map((m) => (
              <li
                key={m.uid || m.email || m.inviteUrl}
                className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 text-sm"
              >
                <div>
                  <p className="font-medium">
                    {m.displayName || m.email || "Invite"}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {m.email || "—"} · {m.userRole}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void a.copyInvite(m.inviteUrl as string)}
                  className="cursor-pointer rounded-full border border-border px-3 py-1 text-xs font-medium"
                >
                  Copy invite link
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="font-display text-lg font-semibold">Student roster</h3>
        {a.busy ? (
          <p className="mt-3 text-sm text-muted-foreground">Loading…</p>
        ) : a.studentMembers.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">No students yet.</p>
        ) : (
          <ul className="mt-4 divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {a.studentMembers.map((m) => (
              <li
                key={m.uid || m.email}
                className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 text-sm"
              >
                <div>
                  <p className="font-medium">
                    {m.displayName || m.email || m.uid}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {m.email || m.uid}
                    {m.status === "invited" ? " · pending invite" : ""}
                    {m.status === "applied" ? " · join request" : ""}
                    {m.status === "suspended" ? " · disabled" : ""}
                  </p>
                </div>
                <span className="text-ember">{m.userRole}</span>
                <div className="flex flex-wrap gap-2">
                  {m.status === "applied" && m.uid ? (
                    <button
                      type="button"
                      onClick={() => void a.approveJoin(m.uid)}
                      className="cursor-pointer rounded-full border border-ember px-3 py-1 text-xs font-medium text-ember"
                    >
                      Approve join
                    </button>
                  ) : null}
                  {m.inviteUrl ? (
                    <button
                      type="button"
                      onClick={() => void a.copyInvite(m.inviteUrl as string)}
                      className="cursor-pointer rounded-full border border-border px-3 py-1 text-xs font-medium"
                    >
                      Copy invite link
                    </button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </SchoolAdminChrome>
  );
}
