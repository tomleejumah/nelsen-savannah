import { useCallback, useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import { RoleShellPage } from "@/components/lms/RoleShellPage";
import {
  appointRoleByEmail,
  createSchool,
  fetchSchools,
  patchSchoolAdmins,
  type MeDto,
  type SchoolDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/admin")({
  head: () => ({
    meta: [
      { title: "Super admin — Nelsen Savannah LMS" },
      {
        name: "description",
        content: "Platform super admin — schools, catalog CMS, stats.",
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
      blurb="Create schools, appoint school admins, and publish the global catalog."
    >
      {({ user, me }) => <AdminConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function AdminConsole({ user }: { user: User; me: MeDto }) {
  const [schools, setSchools] = useState<SchoolDto[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [schoolId, setSchoolId] = useState("");
  const [adminUid, setAdminUid] = useState("");
  const [appointSchoolId, setAppointSchoolId] = useState("");
  const [appointUid, setAppointUid] = useState("");
  const [superEmail, setSuperEmail] = useState("");

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const envelope = await fetchSchools(token);
      if (!envelope.ok) {
        setError(envelope.error || "Could not load schools");
        setSchools([]);
        return;
      }
      setSchools(envelope.data?.schools || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, [user]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await createSchool(token, {
      name: name.trim(),
      schoolId: schoolId.trim() || undefined,
      adminUid: adminUid.trim() || undefined,
    });
    if (!result.ok) {
      setMsg(result.error || "Create failed");
      return;
    }
    setName("");
    setSchoolId("");
    setAdminUid("");
    setMsg(`Created ${result.data?.school.schoolId}`);
    await load();
  }

  async function onAppoint(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolAdmins(token, appointSchoolId.trim(), {
      adminUid: appointUid.trim(),
    });
    if (!result.ok) {
      setMsg(result.error || "Appoint failed");
      return;
    }
    setAppointUid("");
    setMsg("School admin appointed.");
    await load();
  }

  async function onSuperByEmail(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await appointRoleByEmail(token, {
      email: superEmail.trim(),
      userRole: "SuperAdmin",
    });
    if (!result.ok) {
      setMsg(result.error || "Could not appoint SuperAdmin");
      return;
    }
    setSuperEmail("");
    setMsg(
      `SuperAdmin set for ${result.data?.email || superEmail} (${result.data?.uid})`,
    );
  }

  return (
    <div className="space-y-10">
      {error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      {msg ? <p className="text-sm text-ember">{msg}</p> : null}

      <section id="cms">
        <CatalogCmsPanel user={user} />
      </section>

      <section id="schools">
        <h2 className="font-display text-xl font-semibold">Schools</h2>
        {busy ? (
          <p className="mt-3 text-sm text-muted-foreground">Loading…</p>
        ) : (
          <ul className="mt-4 divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {schools.map((s) => (
              <li
                key={s.schoolId}
                className="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-sm"
              >
                <span className="font-medium">{s.name}</span>
                <span className="font-mono text-xs text-muted-foreground">
                  {s.schoolId}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <form id="create-school" onSubmit={(e) => void onCreate(e)} className="space-y-3">
        <h2 className="font-display text-lg font-semibold">Create school</h2>
        <input
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="School name"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input
          value={schoolId}
          onChange={(e) => setSchoolId(e.target.value)}
          placeholder="schoolId (optional slug)"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input
          value={adminUid}
          onChange={(e) => setAdminUid(e.target.value)}
          placeholder="Initial school admin uid (optional)"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <button
          type="submit"
          className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground"
        >
          Create school
        </button>
      </form>

      <form id="appoint" onSubmit={(e) => void onAppoint(e)} className="space-y-3">
        <h2 className="font-display text-lg font-semibold">Appoint school admin</h2>
        <input
          required
          value={appointSchoolId}
          onChange={(e) => setAppointSchoolId(e.target.value)}
          placeholder="schoolId"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input
          required
          value={appointUid}
          onChange={(e) => setAppointUid(e.target.value)}
          placeholder="Firebase uid"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <button
          type="submit"
          className="rounded-full border border-border px-5 py-2 text-sm font-semibold"
        >
          Appoint
        </button>
      </form>

      <form id="super-admin" onSubmit={(e) => void onSuperByEmail(e)} className="space-y-3">
        <h2 className="font-display text-lg font-semibold">Appoint SuperAdmin by email</h2>
        <p className="text-sm text-muted-foreground">
          They must have signed in with Google at least once so Firebase knows the account.
        </p>
        <input
          required
          type="email"
          value={superEmail}
          onChange={(e) => setSuperEmail(e.target.value)}
          placeholder="name@example.com"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <button
          type="submit"
          className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground"
        >
          Make SuperAdmin
        </button>
      </form>
    </div>
  );
}
