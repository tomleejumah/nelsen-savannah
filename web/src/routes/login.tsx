import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  onAuthStateChanged,
  signInWithPopup,
  signOut,
  type User,
} from "firebase/auth";
import { LogIn, LogOut, Shield } from "lucide-react";
import { toast } from "sonner";

import { getFirebaseAuth, googleProvider } from "@/lib/firebase";
import { workspacesForMe } from "@/lib/lmsCapabilities";
import { fetchLmsMe, type MeDto } from "@/lib/lmsApi";
import { shellFromMe, shellHomePath } from "@/lib/lmsRoles";

export const Route = createFileRoute("/login")({
  head: () => ({
    meta: [
      { title: "Sign in — Nelsen Savannah" },
      {
        name: "description",
        content: "Sign in to Nelsen Savannah to access learning tracks and your progress.",
      },
    ],
  }),
  component: LoginPage,
});

function LoginPage() {
  const [user, setUser] = useState<User | null>(null);
  const [me, setMe] = useState<MeDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadMe = useCallback(async (u: User) => {
    setBusy(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      const envelope = await fetchLmsMe(token);
      if (!envelope.ok || !envelope.data) {
        setMe(null);
        setError(envelope.error || "Could not load your profile");
        return;
      }
      setMe(envelope.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
      setMe(null);
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    const auth = getFirebaseAuth();
    return onAuthStateChanged(auth, (next) => {
      setUser(next);
      if (next) {
        void loadMe(next);
      } else {
        setMe(null);
      }
    });
  }, [loadMe]);

  async function onGoogleSignIn() {
    setBusy(true);
    setError(null);
    try {
      await signInWithPopup(getFirebaseAuth(), googleProvider);
      toast.success("Signed in");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Sign-in failed";
      setError(msg);
      toast.error(msg);
      setBusy(false);
    }
  }

  async function onSignOut() {
    await signOut(getFirebaseAuth());
    toast.message("Signed out");
  }

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="relative mx-auto max-w-lg px-5 sm:px-8">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-0 -top-20 h-64 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.12),transparent_60%)]"
        />
        <p className="eyebrow text-ember">Account</p>
        <h1 className="mt-4 font-display text-4xl font-bold text-foreground sm:text-5xl">
          Sign in
        </h1>
        <p className="mt-4 text-base leading-relaxed text-muted-foreground">
          Sign in with Google to enroll in tracks and keep your learning progress.
        </p>

        <div className="mt-10 space-y-4">
          {!user ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => void onGoogleSignIn()}
              className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5 disabled:opacity-60"
            >
              <LogIn className="h-4 w-4" />
              Continue with Google
            </button>
          ) : (
            <div className="space-y-4 rounded-2xl border border-border/60 bg-card/40 p-5">
              <div className="flex items-start gap-3">
                {user.photoURL ? (
                  <img
                    src={user.photoURL}
                    alt=""
                    className="h-12 w-12 rounded-full object-cover"
                  />
                ) : (
                  <span className="grid h-12 w-12 place-items-center rounded-full bg-maroon/15 text-maroon">
                    <Shield className="h-5 w-5" />
                  </span>
                )}
                <div className="min-w-0 flex-1">
                  <p className="font-display font-semibold text-foreground">
                    {me?.displayName || user.displayName || "Signed in"}
                  </p>
                  <p className="truncate text-sm text-muted-foreground">{user.email}</p>
                  {me?.userRole && (
                    <p className="mt-1 text-xs font-medium text-ember">{me.userRole}</p>
                  )}
                </div>
              </div>

              {busy && (
                <p className="text-sm text-muted-foreground">Loading your profile…</p>
              )}
              {error && (
                <p className="rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {error}
                </p>
              )}

              {me ? (
                <div className="space-y-2 pt-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    Your workspaces
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {workspacesForMe(me).map((w) => (
                      <Link
                        key={w.shell}
                        to={w.to}
                        className={
                          w.to === shellHomePath(shellFromMe(me))
                            ? "rounded-full bg-ember-gradient px-4 py-2 text-sm font-medium text-maroon-foreground"
                            : "rounded-full bg-maroon/10 px-4 py-2 text-sm font-medium text-maroon hover:bg-maroon/20"
                        }
                      >
                        {w.label}
                      </Link>
                    ))}
                  </div>
                </div>
              ) : null}
              <div className="flex flex-wrap gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => void onSignOut()}
                  className="inline-flex items-center gap-1.5 rounded-full border border-border px-4 py-2 text-sm font-medium hover:bg-accent/60"
                >
                  <LogOut className="h-3.5 w-3.5" />
                  Sign out
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
