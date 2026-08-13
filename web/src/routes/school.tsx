import { useCallback, useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import { RoleShellPage } from "@/components/lms/RoleShellPage";
import {
  fetchSchoolDashboard,
  fetchSchoolMembers,
  importSchoolRoster,
  patchSchoolBranding,
  patchSchoolMemberRole,
  registerSchoolMentee,
  registerSchoolMentor,
  type MeDto,
  type SchoolDashboardDto,
  type SchoolMemberDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/school")({
  head: () => ({
    meta: [
      { title: "School admin — Nelsen Savannah LMS" },
      {
        name: "description",
        content: "School admin — roster, catalog, dashboard, and payments UI.",
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
      blurb="People, catalog, dashboard, and seat payments for your school wing."
    >
      {({ user, me }) => <SchoolConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function SchoolConsole({ user, me }: { user: User; me: MeDto }) {
  const schoolId = me.schoolId || "nelsen-digital";
  const [members, setMembers] = useState<SchoolMemberDto[]>([]);
  const [dash, setDash] = useState<SchoolDashboardDto | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [mentorUid, setMentorUid] = useState("");
  const [mentorEmail, setMentorEmail] = useState("");
  const [mentorName, setMentorName] = useState("");
  const [menteeUid, setMenteeUid] = useState("");
  const [menteeEmail, setMenteeEmail] = useState("");
  const [menteeName, setMenteeName] = useState("");
  const [csv, setCsv] = useState("uid,email,displayName,role\n");
  const [accent, setAccent] = useState("");
  const [logoUrl, setLogoUrl] = useState("");
  const [seats, setSeats] = useState(10);
  const [phone, setPhone] = useState("");
  const [payMethod, setPayMethod] = useState<"card" | "mpesa">("card");
  const [payMsg, setPayMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [m, d] = await Promise.all([
        fetchSchoolMembers(token, schoolId),
        fetchSchoolDashboard(token, schoolId),
      ]);
      if (!m.ok) setError(m.error || "Could not load roster");
      setMembers(m.data?.members || []);
      setDash(d.data || null);
      if (d.data?.accentColor) setAccent(d.data.accentColor);
      if (d.data?.logoUrl) setLogoUrl(d.data.logoUrl);
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
    setMsg(result.ok ? "Mentor registered." : result.error || "Failed");
    if (result.ok) {
      setMentorUid("");
      setMentorEmail("");
      setMentorName("");
      await load();
    }
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
    setMsg(result.ok ? "Mentee registered." : result.error || "Failed");
    if (result.ok) {
      setMenteeUid("");
      setMenteeEmail("");
      setMenteeName("");
      await load();
    }
  }

  async function escalate(uid: string) {
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolMemberRole(token, schoolId, uid, "Mentor");
    setMsg(result.ok ? "Escalated to Mentor." : result.error || "Failed");
    if (result.ok) await load();
  }

  async function onRoster(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await importSchoolRoster(token, schoolId, csv);
    setMsg(
      result.ok
        ? `Imported ${result.data?.imported ?? 0} members`
        : result.error || "Import failed",
    );
    if (result.ok) await load();
  }

  async function onBrand(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolBranding(token, schoolId, {
      accentColor: accent.trim() || undefined,
      logoUrl: logoUrl.trim() || undefined,
    });
    setMsg(result.ok ? "Branding saved." : result.error || "Failed");
  }

  return (
    <div className="space-y-12">
      <p className="text-sm text-muted-foreground">
        School id: <span className="font-mono text-foreground">{schoolId}</span>
      </p>
      {error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      {msg ? <p className="text-sm text-ember">{msg}</p> : null}

      <section id="dashboard">
        <h2 className="font-display text-xl font-semibold">Dashboard</h2>
        {busy && !dash ? (
          <p className="mt-3 text-sm text-muted-foreground">Loading…</p>
        ) : dash ? (
          <div className="mt-4 space-y-3 text-sm">
            <p>
              Roster {dash.rosterCount} · Mentors {dash.mentors} · Mentees{" "}
              {dash.mentees} · Enrollments {dash.enrollments}
            </p>
            <p>
              Avg completion{" "}
              <span className="font-semibold text-ember">{dash.avgCompletion}%</span>
            </p>
            {dash.atRisk.length > 0 ? (
              <ul className="rounded-2xl border border-border/70 bg-card px-4 py-3">
                <li className="mb-2 font-medium">At risk (&lt;40%, inactive 7d)</li>
                {dash.atRisk.slice(0, 8).map((a) => (
                  <li key={`${a.uid}-${a.trackId}`} className="text-muted-foreground">
                    {a.displayName || a.uid} · {a.trackId} · {a.trackPercent}%
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-muted-foreground">No at-risk students.</p>
            )}
          </div>
        ) : null}
      </section>

      <section id="cms">
        <CatalogCmsPanel user={user} schoolId={schoolId} />
      </section>

      <section id="people" className="grid gap-8 sm:grid-cols-2">
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

      <form id="roster" onSubmit={(e) => void onRoster(e)} className="space-y-3">
        <h2 className="font-display text-lg font-semibold">Roster CSV import</h2>
        <textarea
          value={csv}
          onChange={(e) => setCsv(e.target.value)}
          rows={4}
          className="w-full rounded-xl border border-border bg-background px-3 py-2 font-mono text-xs"
        />
        <button type="submit" className="rounded-full border border-border px-5 py-2 text-sm">
          Import
        </button>
      </form>

      <form id="branding" onSubmit={(e) => void onBrand(e)} className="space-y-3">
        <h2 className="font-display text-lg font-semibold">Branding</h2>
        <input
          value={logoUrl}
          onChange={(e) => setLogoUrl(e.target.value)}
          placeholder="Logo URL"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input
          value={accent}
          onChange={(e) => setAccent(e.target.value)}
          placeholder="Accent color (#hex)"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <button type="submit" className="rounded-full border border-border px-5 py-2 text-sm">
          Save branding
        </button>
      </form>

      <section id="payments" className="space-y-3">
        <h2 className="font-display text-xl font-semibold">Seats / payments</h2>
        <p className="text-sm text-muted-foreground">
          Buy seat licenses for your school. Checkout stays off until you pick a
          payment rail — UI is ready.
        </p>
        <div className="flex flex-wrap gap-2">
          <label className="text-xs text-muted-foreground">
            Seats
            <input
              type="number"
              min={1}
              max={500}
              value={seats}
              onChange={(e) => setSeats(Number(e.target.value) || 1)}
              className="ml-2 w-24 rounded-xl border border-border bg-background px-3 py-2 text-sm text-foreground"
            />
          </label>
          <input
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="M-Pesa phone 254…"
            className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => {
              setPayMethod("card");
              setPayMsg(
                `Card checkout UI ready (${seats} seats). Provider not wired yet — tell us which card rail to use.`,
              );
            }}
            className="rounded-full bg-ember-gradient px-4 py-2 text-sm font-semibold text-maroon-foreground"
          >
            Pay with card
          </button>
          <button
            type="button"
            onClick={() => {
              setPayMethod("mpesa");
              if (!phone.trim()) {
                setPayMsg("Enter an M-Pesa phone (254…) first.");
                return;
              }
              setPayMsg(
                `M-Pesa STK UI ready for ${phone.trim()} · ${seats} seats. Provider not wired yet.`,
              );
            }}
            className="rounded-full border border-border px-4 py-2 text-sm font-medium"
          >
            Pay with M-Pesa
          </button>
        </div>
        {payMsg ? (
          <p className="rounded-xl border border-border/60 bg-card/40 px-4 py-3 text-sm text-muted-foreground">
            <span className="font-medium text-ember">{payMethod === "mpesa" ? "M-Pesa" : "Card"}</span>
            {" — "}
            {payMsg}
          </p>
        ) : null}
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
