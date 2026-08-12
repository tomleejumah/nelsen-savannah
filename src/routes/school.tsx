import { useCallback, useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import {
  fetchSchoolMembers,
  patchSchoolMemberRole,
  registerSchoolMentee,
  registerSchoolMentor,
  type MeDto,
  type SchoolMemberDto,
} from "@/lib/lmsApi";

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
      blurb="Register mentors and mentees for your school. Escalate staff to Mentor."
    >
      {({ user, me }) => <SchoolConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function SchoolConsole({ user, me }: { user: User; me: MeDto }) {
  const schoolId = me.schoolId || "nelsen-digital";
  const [members, setMembers] = useState<SchoolMemberDto[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [mentorUid, setMentorUid] = useState("");
  const [mentorEmail, setMentorEmail] = useState("");
  const [mentorName, setMentorName] = useState("");
  const [menteeUid, setMenteeUid] = useState("");
  const [menteeEmail, setMenteeEmail] = useState("");
  const [menteeName, setMenteeName] = useState("");

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const envelope = await fetchSchoolMembers(token, schoolId);
      if (!envelope.ok) {
        setError(envelope.error || "Could not load roster");
        setMembers([]);
        return;
      }
      setMembers(envelope.data?.members || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, [user, schoolId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function addMentor(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await registerSchoolMentor(token, schoolId, {
      uid: mentorUid.trim(),
      email: mentorEmail.trim() || undefined,
      displayName: mentorName.trim() || undefined,
    });
    if (!result.ok) {
      setMsg(result.error || "Failed");
      return;
    }
    setMentorUid("");
    setMentorEmail("");
    setMentorName("");
    setMsg("Mentor registered.");
    await load();
  }

  async function addMentee(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await registerSchoolMentee(token, schoolId, {
      uid: menteeUid.trim(),
      email: menteeEmail.trim() || undefined,
      displayName: menteeName.trim() || undefined,
    });
    if (!result.ok) {
      setMsg(result.error || "Failed");
      return;
    }
    setMenteeUid("");
    setMenteeEmail("");
    setMenteeName("");
    setMsg("Mentee registered.");
    await load();
  }

  async function escalate(uid: string) {
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolMemberRole(token, schoolId, uid, "Mentor");
    if (!result.ok) {
      setMsg(result.error || "Role update failed");
      return;
    }
    setMsg("Escalated to Mentor.");
    await load();
  }

  return (
    <div className="space-y-10">
      <p className="text-sm text-muted-foreground">
        School id: <span className="font-mono text-foreground">{schoolId}</span>
      </p>
      {error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      {msg ? <p className="text-sm text-ember">{msg}</p> : null}

      <section className="grid gap-8 sm:grid-cols-2">
        <form onSubmit={(e) => void addMentor(e)} className="space-y-3">
          <h2 className="font-display text-lg font-semibold">Register mentor</h2>
          <input
            required
            value={mentorUid}
            onChange={(e) => setMentorUid(e.target.value)}
            placeholder="Firebase uid"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={mentorEmail}
            onChange={(e) => setMentorEmail(e.target.value)}
            placeholder="Email (optional)"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={mentorName}
            onChange={(e) => setMentorName(e.target.value)}
            placeholder="Display name"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <button
            type="submit"
            className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground"
          >
            Add mentor
          </button>
        </form>

        <form onSubmit={(e) => void addMentee(e)} className="space-y-3">
          <h2 className="font-display text-lg font-semibold">Create mentee</h2>
          <input
            required
            value={menteeUid}
            onChange={(e) => setMenteeUid(e.target.value)}
            placeholder="Firebase uid"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={menteeEmail}
            onChange={(e) => setMenteeEmail(e.target.value)}
            placeholder="Email (optional)"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={menteeName}
            onChange={(e) => setMenteeName(e.target.value)}
            placeholder="Display name"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <button
            type="submit"
            className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground"
          >
            Add mentee
          </button>
        </form>
      </section>

      <section>
        <h2 className="font-display text-xl font-semibold">Roster</h2>
        {busy ? (
          <p className="mt-3 text-sm text-muted-foreground">Loading…</p>
        ) : members.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">No members yet.</p>
        ) : (
          <ul className="mt-4 divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {members.map((m) => (
              <li
                key={m.uid}
                className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 text-sm"
              >
                <div>
                  <p className="font-medium">{m.displayName || m.email || m.uid}</p>
                  <p className="text-xs text-muted-foreground">{m.uid}</p>
                </div>
                <span className="text-ember">{m.userRole}</span>
                {m.userRole === "Mentee" ? (
                  <button
                    type="button"
                    onClick={() => void escalate(m.uid)}
                    className="rounded-full border border-border px-3 py-1 text-xs font-medium"
                  >
                    Escalate → Mentor
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
