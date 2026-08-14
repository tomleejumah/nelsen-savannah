import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  updateProfile,
  type User,
} from "firebase/auth";
import { LogIn, LogOut, Shield } from "lucide-react";
import { toast } from "sonner";

import { getFirebaseAuth, googleProvider } from "@/lib/firebase";
import { bumpAuthGeneration, getAuthGeneration, signOutFully } from "@/lib/lmsAuth";
import { workspacesForMe } from "@/lib/lmsCapabilities";
import { fetchLmsMe, type MeDto } from "@/lib/lmsApi";
import { shellFromMe, shellHomePath } from "@/lib/lmsRoles";

export const Route = createFileRoute("/login")({
  head: () => ({
    meta: [
      { title: "Sign in — Nelsen Savannah" },
      {
        name: "description",
        content:
          "Sign in with Google or email to access learning tracks and your progress.",
      },
    ],
  }),
  component: LoginPage,
});

function authErrorMessage(err: unknown): string {
  const code =
    err && typeof err === "object" && "code" in err
      ? String((err as { code: string }).code)
      : "";
  switch (code) {
    case "auth/email-already-in-use":
      return "That email already has an account — sign in instead.";
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
      return "Wrong email or password.";
    case "auth/weak-password":
      return "Password must be at least 6 characters.";
    case "auth/invalid-email":
      return "Enter a valid email address.";
    case "auth/operation-not-allowed":
      return "Email/password sign-in is not enabled in Firebase yet.";
    default:
      return err instanceof Error ? err.message : "Sign-in failed";
  }
}

function LoginPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<User | null>(null);
  const [me, setMe] = useState<MeDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [redirecting, setRedirecting] = useState(false);

  const loadMe = useCallback(async (u: User, gen: number) => {
    setBusy(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      if (gen !== getAuthGeneration()) return;
      const envelope = await fetchLmsMe(token);
      if (gen !== getAuthGeneration()) return;
      if (!envelope.ok || !envelope.data) {
        setMe(null);
        setError(envelope.error || "Could not load your profile");
        return;
      }
      setMe(envelope.data);
    } catch (err) {
      if (gen !== getAuthGeneration()) return;
      setError(err instanceof Error ? err.message : "Network error");
      setMe(null);
    } finally {
      if (gen === getAuthGeneration()) setBusy(false);
    }
  }, []);

  useEffect(() => {
    const auth = getFirebaseAuth();
    return onAuthStateChanged(auth, (next) => {
      const gen = bumpAuthGeneration();
      setUser(next);
      if (next) {
        void loadMe(next, gen);
      } else {
        setMe(null);
        setBusy(false);
      }
    });
  }, [loadMe]);

  // After sign-in: send them to their role home (no manual URLs)
  useEffect(() => {
    if (!user || !me || busy || redirecting) return;
    const home = shellHomePath(shellFromMe(me));
    setRedirecting(true);
    const t = window.setTimeout(() => {
      void navigate({ to: home });
    }, 600);
    return () => window.clearTimeout(t);
  }, [user, me, busy, redirecting, navigate]);

  async function onGoogleSignIn() {
    setBusy(true);
    setError(null);
    try {
      await signInWithPopup(getFirebaseAuth(), googleProvider);
      toast.success("Signed in");
    } catch (err) {
      const msg = authErrorMessage(err);
      setError(msg);
      toast.error(msg);
      setBusy(false);
    }
  }

  async function onEmailSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const auth = getFirebaseAuth();
      if (mode === "signup") {
        const cred = await createUserWithEmailAndPassword(
          auth,
          email.trim(),
          password,
        );
        const name = displayName.trim();
        if (name) {
          await updateProfile(cred.user, { displayName: name });
        }
        toast.success("Account created");
      } else {
        await signInWithEmailAndPassword(auth, email.trim(), password);
        toast.success("Signed in");
      }
    } catch (err) {
      const msg = authErrorMessage(err);
      setError(msg);
      toast.error(msg);
      setBusy(false);
    }
  }

  async function onSignOut() {
    setRedirecting(false);
    setMe(null);
    setUser(null);
    await signOutFully();
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
          Sign in with Google or email through Firebase to enroll and keep your progress.
        </p>

        <div className="mt-10 space-y-4">
          {!user ? (
            <>
              <button
                type="button"
                disabled={busy}
                onClick={() => void onGoogleSignIn()}
                className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5 disabled:opacity-60"
              >
                <LogIn className="h-4 w-4" />
                Continue with Google
              </button>

              <div className="relative py-2 text-center text-xs font-medium uppercase tracking-wide text-muted-foreground">
                <span className="absolute inset-x-0 top-1/2 border-t border-border/60" />
                <span className="relative bg-background px-3">or email</span>
              </div>

              <form onSubmit={(e) => void onEmailSubmit(e)} className="space-y-3">
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setMode("signin")}
                    className={
                      mode === "signin"
                        ? "rounded-full bg-maroon/15 px-3 py-1.5 text-xs font-semibold text-maroon"
                        : "rounded-full px-3 py-1.5 text-xs font-medium text-muted-foreground"
                    }
                  >
                    Sign in
                  </button>
                  <button
                    type="button"
                    onClick={() => setMode("signup")}
                    className={
                      mode === "signup"
                        ? "rounded-full bg-maroon/15 px-3 py-1.5 text-xs font-semibold text-maroon"
                        : "rounded-full px-3 py-1.5 text-xs font-medium text-muted-foreground"
                    }
                  >
                    Create account
                  </button>
                </div>
                {mode === "signup" ? (
                  <input
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="Display name"
                    className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm"
                  />
                ) : null}
                <input
                  required
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Email"
                  className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm"
                />
                <input
                  required
                  type="password"
                  autoComplete={
                    mode === "signup" ? "new-password" : "current-password"
                  }
                  minLength={6}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Password (min 6)"
                  className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm"
                />
                {error ? (
                  <p className="rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
                    {error}
                  </p>
                ) : null}
                <button
                  type="submit"
                  disabled={busy}
                  className="inline-flex w-full items-center justify-center rounded-full border border-border bg-card px-6 py-3 font-display text-sm font-semibold text-foreground transition-colors hover:bg-accent disabled:opacity-60"
                >
                  {mode === "signup" ? "Create account" : "Sign in with email"}
                </button>
              </form>
            </>
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
                  <span className="icon-chip-lg">
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
                <div className="space-y-3 pt-2">
                  {redirecting ? (
                    <p className="text-sm text-muted-foreground">
                      Opening your {shellFromMe(me)} workspace…
                    </p>
                  ) : null}
                  <Link
                    to={shellHomePath(shellFromMe(me))}
                    className="inline-flex w-full items-center justify-center rounded-full bg-ember-gradient px-4 py-3 text-sm font-semibold text-maroon-foreground"
                  >
                    Continue to {workspacesForMe(me).find((w) => w.to === shellHomePath(shellFromMe(me)))?.label || "workspace"}
                  </Link>
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    Or pick a workspace
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {workspacesForMe(me).map((w) => (
                      <Link
                        key={w.shell}
                        to={w.to}
                        className={
                          w.to === shellHomePath(shellFromMe(me))
                            ? "rounded-full bg-maroon/15 px-4 py-2 text-sm font-medium text-maroon"
                            : "rounded-full border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-accent"
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
