import { createFileRoute, Link } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { SchoolAdminChrome } from "@/components/lms/schoolAdmin/SchoolAdminChrome";
import { SchoolPctBar } from "@/components/lms/schoolAdmin/SchoolPctBar";
import { useSchoolAdmin } from "@/components/lms/schoolAdmin/useSchoolAdmin";
import type { MeDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/school/mentors")({
  head: () => ({
    meta: [{ title: "Mentors — School admin" }],
  }),
  component: MentorsPage,
});

function MentorsPage() {
  return (
    <RoleShellPage
      shell="school"
      title="Mentors"
      blurb="Invite trainers, review applications, assign courses."
    >
      {({ user, me }) => <MentorsConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function MentorsConsole({ user, me }: { user: User; me: MeDto }) {
  const a = useSchoolAdmin(user, me);

  return (
    <SchoolAdminChrome
      title="Mentors"
      blurb="Invite mentors, approve apps, and assign them to courses."
      msg={a.msg}
      lastInviteUrl={a.lastInviteUrl}
      onCopyInvite={(url) => void a.copyInvite(url)}
    >
      {a.error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {a.error}
        </p>
      ) : null}

      <form
        id="invite-mentor"
        onSubmit={(e) => void a.addMentor(e)}
        className="space-y-3"
      >
        <h3 className="font-display text-lg font-semibold">Invite mentor</h3>
        <p className="text-xs text-muted-foreground">
          Add their email — when they sign in, they join as Mentor.
        </p>
        <input
          required
          type="email"
          value={a.mentorEmail}
          onChange={(e) => a.setMentorEmail(e.target.value)}
          placeholder="teacher@email.com"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input
          value={a.mentorName}
          onChange={(e) => a.setMentorName(e.target.value)}
          placeholder="Display name"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input
          value={a.mentorUid}
          onChange={(e) => a.setMentorUid(e.target.value)}
          placeholder="Firebase uid (optional, legacy)"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <button
          type="submit"
          className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground"
        >
          Invite mentor
        </button>
      </form>

      <section className="space-y-3">
        <h3 className="font-display text-lg font-semibold">
          Mentor applications
        </h3>
        <p className="text-sm text-muted-foreground">
          People who applied from the Android app to teach at this school.
        </p>
        {a.applications.length === 0 ? (
          <p className="text-sm text-muted-foreground">No pending applications.</p>
        ) : (
          <ul className="divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {a.applications.map((app) => (
              <li key={app.id} className="space-y-2 px-5 py-4 text-sm">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-display font-semibold">
                    {app.displayName || app.email || app.uid}
                  </p>
                  <span className="text-xs text-muted-foreground">{app.email}</span>
                </div>
                {app.answers?.motivation ? (
                  <p className="line-clamp-3 text-muted-foreground">
                    {app.answers.motivation}
                  </p>
                ) : null}
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => void a.decideApp(app.id, "approved")}
                    className="cursor-pointer rounded-full bg-ember-gradient px-3 py-1.5 text-xs font-semibold text-maroon-foreground"
                  >
                    Approve
                  </button>
                  <button
                    type="button"
                    onClick={() => void a.decideApp(app.id, "rejected")}
                    className="cursor-pointer rounded-full border border-border px-3 py-1.5 text-xs font-medium"
                  >
                    Reject
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <h3 className="font-display text-lg font-semibold">Assign to courses</h3>
        {a.busy && !a.dash ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : (a.dash?.byCourse?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground">
            No courses yet — create one under{" "}
            <Link
              to="/school/coursework"
              className="text-ember underline-offset-2 hover:underline"
            >
              Coursework
            </Link>
            .
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {a.dash!.byCourse.map((c) => (
              <li
                key={c.trackId}
                className="rounded-2xl border border-border/70 bg-card/40 px-4 py-4 text-sm"
              >
                <p className="font-display font-semibold">{c.title}</p>
                <p className="text-xs text-muted-foreground">
                  {c.enrolled} enrolled
                </p>
                <SchoolPctBar label="Completion" pct={c.avgPercent} />
                <p className="mt-3 text-xs text-muted-foreground">
                  {(c.mentors?.length ?? 0) === 0
                    ? "No trainers assigned"
                    : c.mentors
                        ?.map((m) => m.displayName || m.uid)
                        .join(", ")}
                </p>
                <button
                  type="button"
                  onClick={() => {
                    a.setAssignMsg(null);
                    a.setAssignTrackId(
                      a.assignTrackId === c.trackId ? null : c.trackId,
                    );
                    a.setAssignUids(
                      (c.mentors || []).map((m) => m.uid).filter(Boolean),
                    );
                  }}
                  className="mt-3 cursor-pointer rounded-full border border-border px-3 py-1.5 text-xs font-medium hover:bg-secondary"
                >
                  {a.assignTrackId === c.trackId ? "Cancel" : "Assign trainers"}
                </button>
                {a.assignTrackId === c.trackId ? (
                  <div className="mt-3 space-y-2 rounded-xl border border-border/60 bg-background/60 p-3">
                    {a.schoolMentors.length === 0 ? (
                      <p className="text-xs text-muted-foreground">
                        No mentors linked yet. Use Invite mentor above — they
                        must sign in once, then they show up here.
                      </p>
                    ) : (
                      a.schoolMentors.map((m) => (
                        <label
                          key={m.uid}
                          className="flex cursor-pointer items-center gap-2 text-xs"
                        >
                          <input
                            type="checkbox"
                            checked={a.assignUids.includes(m.uid)}
                            onChange={(e) => {
                              a.setAssignUids((prev) =>
                                e.target.checked
                                  ? [...prev, m.uid]
                                  : prev.filter((id) => id !== m.uid),
                              );
                            }}
                          />
                          {m.displayName || m.email || m.uid}
                        </label>
                      ))
                    )}
                    {a.assignMsg ? (
                      <p className="text-xs text-destructive">{a.assignMsg}</p>
                    ) : null}
                    {a.schoolMentors.length > 0 ? (
                      <button
                        type="button"
                        onClick={() => void a.saveTrackMentors(c.trackId)}
                        className="cursor-pointer rounded-full bg-ember-gradient px-3 py-1.5 text-xs font-semibold text-maroon-foreground"
                      >
                        Save trainers
                      </button>
                    ) : null}
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <h3 className="font-display text-lg font-semibold">
          Escalate mentee → Mentor
        </h3>
        <p className="text-sm text-muted-foreground">
          Promote an active student on the roster to Mentor.
        </p>
        {a.busy ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : a.studentMembers.filter(
            (m) => m.userRole === "Mentee" && m.uid && m.status !== "suspended",
          ).length === 0 ? (
          <p className="text-sm text-muted-foreground">No mentees to promote.</p>
        ) : (
          <ul className="divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {a.studentMembers
              .filter(
                (m) =>
                  m.userRole === "Mentee" && m.uid && m.status !== "suspended",
              )
              .map((m) => (
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
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => void a.escalate(m.uid)}
                    className="cursor-pointer rounded-full border border-border px-3 py-1 text-xs font-medium"
                  >
                    Escalate → Mentor
                  </button>
                </li>
              ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="font-display text-lg font-semibold">Mentor roster</h3>
        {a.busy ? (
          <p className="mt-3 text-sm text-muted-foreground">Loading…</p>
        ) : a.schoolMentors.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">No mentors yet.</p>
        ) : (
          <ul className="mt-4 divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {a.members
              .filter((m) => m.userRole === "Mentor")
              .map((m) => (
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
                      {m.status === "suspended" ? " · disabled" : ""}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {m.uid && m.status !== "suspended" ? (
                      <button
                        type="button"
                        onClick={() => void a.setMentorEnabled(m.uid, false)}
                        className="cursor-pointer rounded-full border border-border px-3 py-1 text-xs font-medium"
                      >
                        Disable
                      </button>
                    ) : null}
                    {m.uid && m.status === "suspended" ? (
                      <button
                        type="button"
                        onClick={() => void a.setMentorEnabled(m.uid, true)}
                        className="cursor-pointer rounded-full border border-border px-3 py-1 text-xs font-medium"
                      >
                        Re-enable
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
