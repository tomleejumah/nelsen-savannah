import { useCallback, useEffect, useState, type FormEvent } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  onAuthStateChanged,
  updateProfile,
  type User,
} from "firebase/auth";
import {
  BookOpen,
  Building2,
  CreditCard,
  LogIn,
  LogOut,
  Mail,
  UserRound,
} from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import { bumpAuthGeneration, getAuthGeneration, signOutFully } from "@/lib/lmsAuth";
import {
  fetchLmsMe,
  fetchMyEnrollments,
  type EnrollmentDto,
  type MeDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/profile")({
  head: () => ({
    meta: [
      { title: "Profile — Nelsen Savannah" },
      {
        name: "description",
        content: "Your name, email, enrollments, schools, and payment stub.",
      },
    ],
  }),
  component: ProfilePage,
});

function ProfilePage() {
  const [user, setUser] = useState<User | null>(null);
  const [me, setMe] = useState<MeDto | null>(null);
  const [enrollments, setEnrollments] = useState<EnrollmentDto[]>([]);
  const [authReady, setAuthReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nameDraft, setNameDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [savedNote, setSavedNote] = useState<string | null>(null);

  const load = useCallback(async (u: User, gen: number) => {
    setLoading(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      if (gen !== getAuthGeneration()) return;
      const [meEnv, enrollEnv] = await Promise.all([
        fetchLmsMe(token),
        fetchMyEnrollments(token),
      ]);
      if (gen !== getAuthGeneration()) return;
      if (!meEnv.ok || !meEnv.data) {
        setMe(null);
        setError(meEnv.error || "Could not load profile");
      } else {
        setMe(meEnv.data);
        setNameDraft(meEnv.data.displayName || u.displayName || "");
      }
      setEnrollments(enrollEnv.data?.enrollments || []);
    } catch (err) {
      if (gen !== getAuthGeneration()) return;
      setError(err instanceof Error ? err.message : "Network error");
      setMe(null);
      setEnrollments([]);
    } finally {
      if (gen === getAuthGeneration()) setLoading(false);
    }
  }, []);

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      const gen = bumpAuthGeneration();
      setUser(next);
      setAuthReady(true);
      if (next) void load(next, gen);
      else {
        setMe(null);
        setEnrollments([]);
        setNameDraft("");
      }
    });
  }, [load]);

  async function onSaveName(e: FormEvent) {
    e.preventDefault();
    if (!user) return;
    const nextName = nameDraft.trim();
    if (!nextName) {
      setError("Name cannot be empty");
      return;
    }
    setSaving(true);
    setError(null);
    setSavedNote(null);
    try {
      await updateProfile(user, { displayName: nextName });
      await user.getIdToken(true);
      const gen = getAuthGeneration();
      await load(user, gen);
      setSavedNote("Name updated");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save name");
    } finally {
      setSaving(false);
    }
  }

  function onLogout() {
    setMe(null);
    setUser(null);
    setEnrollments([]);
    void signOutFully();
  }

  if (!authReady) {
    return (
      <div className="pb-24 pt-40">
        <p className="text-center text-sm text-muted-foreground">Checking your session…</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="pb-24 pt-36 sm:pt-44">
        <div className="mx-auto max-w-lg px-5 text-center sm:px-8">
          <p className="eyebrow text-ember">Profile</p>
          <h1 className="mt-4 text-4xl font-bold">Sign in to view your profile</h1>
          <p className="mt-4 text-muted-foreground">
            Name, email, enrollments, schools, and payments live on your account.
          </p>
          <Link
            to="/login"
            className="mt-8 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow"
          >
            <LogIn className="h-4 w-4" /> Sign in
          </Link>
        </div>
      </div>
    );
  }

  const schools = (me?.memberships || []).filter((m) => m.status === "active");

  return (
    <div className="pb-24 pt-36 sm:pt-44">
      <div className="mx-auto max-w-3xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Profile</p>
        <h1 className="mt-3 text-3xl font-bold sm:text-5xl">Your account</h1>
        <p className="mt-3 text-muted-foreground">
          Manage how you show up in school wings and learning.
        </p>

        {error ? (
          <p className="mt-6 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </p>
        ) : null}
        {savedNote ? (
          <p className="mt-6 rounded-xl bg-brand/10 px-4 py-3 text-sm text-brand-soft">
            {savedNote}
          </p>
        ) : null}

        <section className="mt-10 rounded-3xl border border-border/70 bg-card p-6 sm:p-8">
          <div className="flex items-start gap-4">
            {user.photoURL || me?.photoUrl ? (
              <img
                src={user.photoURL || me?.photoUrl}
                alt=""
                className="h-14 w-14 rounded-full object-cover"
              />
            ) : (
              <span className="icon-chip-lg">
                <UserRound className="h-5 w-5" />
              </span>
            )}
            <div className="min-w-0 flex-1">
              <p className="font-display text-lg font-semibold">
                {me?.displayName || user.displayName || "Learner"}
              </p>
              <p className="mt-0.5 text-sm text-muted-foreground">
                {me?.userRole || "Mentee"}
                {loading ? " · Refreshing…" : ""}
              </p>
            </div>
          </div>

          <form onSubmit={(e) => void onSaveName(e)} className="mt-8 space-y-4">
            <label className="block text-sm">
              <span className="font-medium text-foreground">Display name</span>
              <input
                value={nameDraft}
                onChange={(e) => setNameDraft(e.target.value)}
                className="mt-1.5 h-11 w-full rounded-xl border border-input bg-background px-3 text-sm"
                autoComplete="name"
              />
            </label>
            <label className="block text-sm">
              <span className="inline-flex items-center gap-1.5 font-medium text-foreground">
                <Mail className="h-3.5 w-3.5 text-ember" /> Email
              </span>
              <input
                value={me?.email || user.email || ""}
                readOnly
                className="mt-1.5 h-11 w-full rounded-xl border border-input bg-muted/40 px-3 text-sm text-muted-foreground"
              />
              <span className="mt-1 block text-xs text-muted-foreground">
                Email comes from your sign-in provider and can’t be changed here.
              </span>
            </label>
            <button
              type="submit"
              disabled={saving}
              className="rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow disabled:opacity-60"
            >
              {saving ? "Saving…" : "Save name"}
            </button>
          </form>
        </section>

        <section className="mt-8 rounded-3xl border border-border/70 bg-card p-6 sm:p-8">
          <h2 className="flex items-center gap-2 font-display text-lg font-semibold">
            <BookOpen className="h-5 w-5 text-ember" /> Enrolled courses
          </h2>
          {enrollments.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">
              No enrollments yet.{" "}
              <Link to="/learning" className="font-medium text-maroon hover:underline">
                Browse tracks
              </Link>
            </p>
          ) : (
            <ul className="mt-5 space-y-3">
              {enrollments.map((e) => (
                <li key={e.trackId}>
                  <Link
                    to="/learning/$trackId"
                    params={{ trackId: e.trackId }}
                    className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 px-4 py-3 transition-colors hover:bg-accent/50"
                  >
                    <span>
                      <span className="block font-medium">
                        {e.courseTitle || e.trackId}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {e.status} · {e.trackPercent}% complete
                      </span>
                    </span>
                    <span className="text-xs font-semibold text-ember">Open</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mt-8 rounded-3xl border border-border/70 bg-card p-6 sm:p-8">
          <h2 className="flex items-center gap-2 font-display text-lg font-semibold">
            <Building2 className="h-5 w-5 text-ember" /> Schools
          </h2>
          {schools.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">
              {me?.unaffiliated
                ? "You’re in the open marketplace — enroll in a track to join that school’s wing."
                : `Active school: ${me?.schoolName || me?.activeSchoolId || "Nelsen Digital"}`}
            </p>
          ) : (
            <ul className="mt-5 space-y-3">
              {schools.map((s) => (
                <li
                  key={s.id || s.schoolId}
                  className="rounded-2xl border border-border/60 px-4 py-3"
                >
                  <p className="font-medium">{s.schoolName || s.schoolId}</p>
                  <p className="text-xs text-muted-foreground">
                    {s.role}
                    {s.schoolId === (me?.activeSchoolId || me?.schoolId)
                      ? " · active"
                      : ""}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mt-8 rounded-3xl border border-border/70 bg-secondary/40 p-6 sm:p-8">
          <h2 className="flex items-center gap-2 font-display text-lg font-semibold">
            <CreditCard className="h-5 w-5 text-ember" /> Payments
          </h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Payment rails are not live yet — this is a placeholder for school fees and tutor
            payouts.
          </p>
          <div className="mt-5 space-y-3">
            <div className="rounded-2xl border border-dashed border-border bg-card/60 px-4 py-3">
              <p className="text-sm font-medium">Balance</p>
              <p className="mt-1 font-display text-2xl font-semibold">KES 0.00</p>
              <p className="mt-1 text-xs text-muted-foreground">Dummy — no charges yet</p>
            </div>
            <div className="rounded-2xl border border-dashed border-border bg-card/60 px-4 py-3">
              <p className="text-sm font-medium">Recent activity</p>
              <p className="mt-2 text-sm text-muted-foreground">No transactions</p>
            </div>
          </div>
        </section>

        <div className="mt-10 flex flex-wrap gap-3">
          <Link
            to="/learning"
            className="rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow"
          >
            Go to learning
          </Link>
          <button
            type="button"
            onClick={onLogout}
            className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-5 py-2.5 font-display text-sm font-semibold text-foreground hover:bg-accent"
          >
            <LogOut className="h-4 w-4" /> Log out
          </button>
        </div>
      </div>
    </div>
  );
}
