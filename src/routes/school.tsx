import { useCallback, useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import { RoleShellPage } from "@/components/lms/RoleShellPage";
import {
  billingCheckout,
  billingWebhookComplete,
  fetchSchoolBilling,
  fetchSchoolDashboard,
  fetchSchoolMembers,
  importSchoolRoster,
  patchSchoolBranding,
  patchSchoolMemberRole,
  registerSchoolMentee,
  registerSchoolMentor,
  type BillingDto,
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
        content: "School admin — roster, catalog, dashboard, and seats.",
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
      blurb="People, catalog, dashboard, and seat licenses for your school wing."
    >
      {({ user, me }) => <SchoolConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function SchoolConsole({ user, me }: { user: User; me: MeDto }) {
  const schoolId = me.schoolId || "nelsen-digital";
  const [members, setMembers] = useState<SchoolMemberDto[]>([]);
  const [dash, setDash] = useState<SchoolDashboardDto | null>(null);
  const [billing, setBilling] = useState<BillingDto | null>(null);
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
  const [pendingPayId, setPendingPayId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [m, d, b] = await Promise.all([
        fetchSchoolMembers(token, schoolId),
        fetchSchoolDashboard(token, schoolId),
        fetchSchoolBilling(token, schoolId),
      ]);
      if (!m.ok) setError(m.error || "Could not load roster");
      setMembers(m.data?.members || []);
      setDash(d.data || null);
      setBilling(b.data || null);
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

  async function buySeats(method: "card" | "mpesa") {
    setMsg(null);
    const token = await user.getIdToken();
    const result = await billingCheckout(token, {
      schoolId,
      seats,
      method,
      phone: method === "mpesa" ? phone.trim() : undefined,
    });
    if (!result.ok) {
      setMsg(result.error || "Checkout failed");
      return;
    }
    if (result.data?.payment.status === "pending") {
      setPendingPayId(result.data.payment.id);
      setMsg(`M-Pesa pending ${result.data.payment.id} — confirm webhook when paid.`);
    } else {
      setMsg(`Paid — seats now ${result.data?.seatsTotal}`);
      setPendingPayId(null);
    }
    await load();
  }

  async function confirmMpesa() {
    if (!pendingPayId) return;
    const token = await user.getIdToken();
    const result = await billingWebhookComplete(token, pendingPayId);
    setMsg(result.ok ? "M-Pesa marked paid; seats added." : result.error || "Failed");
    setPendingPayId(null);
    await load();
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

      <section>
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
              Avg completion <span className="font-semibold text-ember">{dash.avgCompletion}%</span>{" "}
              · Seats {dash.seatsUsed}/{dash.seatsTotal}
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

      <CatalogCmsPanel user={user} schoolId={schoolId} />

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

      <form onSubmit={(e) => void onRoster(e)} className="space-y-3">
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

      <form onSubmit={(e) => void onBrand(e)} className="space-y-3">
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

      <section className="space-y-3">
        <h2 className="font-display text-xl font-semibold">Seats / billing</h2>
        <p className="text-sm text-muted-foreground">
          Available {billing?.seatsAvailable ?? "—"} · {billing?.seatPriceKes ?? 500} KES/seat
        </p>
        <div className="flex flex-wrap gap-2">
          <input
            type="number"
            min={1}
            value={seats}
            onChange={(e) => setSeats(Number(e.target.value) || 1)}
            className="w-24 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="M-Pesa phone 254…"
            className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <button
            type="button"
            onClick={() => void buySeats("card")}
            className="rounded-full bg-ember-gradient px-4 py-2 text-sm font-semibold text-maroon-foreground"
          >
            Pay card (demo)
          </button>
          <button
            type="button"
            onClick={() => void buySeats("mpesa")}
            className="rounded-full border border-border px-4 py-2 text-sm"
          >
            M-Pesa STK (demo)
          </button>
          {pendingPayId ? (
            <button
              type="button"
              onClick={() => void confirmMpesa()}
              className="rounded-full border border-border px-4 py-2 text-sm"
            >
              Confirm M-Pesa webhook
            </button>
          ) : null}
        </div>
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
