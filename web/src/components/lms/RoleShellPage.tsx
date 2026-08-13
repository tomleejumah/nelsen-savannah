import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";

import { CapabilitiesBoard } from "@/components/lms/CapabilitiesBoard";
import { getFirebaseAuth } from "@/lib/firebase";
import { fetchLmsMe, type MeDto } from "@/lib/lmsApi";
import {
  canAccessShell,
  shellFromMe,
  shellHomePath,
  type LmsShell,
} from "@/lib/lmsRoles";

type Props = {
  shell: LmsShell;
  title: string;
  blurb: string;
  children?: ReactNode | ((ctx: { user: User; me: MeDto }) => ReactNode);
};

export function RoleShellPage({ shell, title, blurb, children }: Props) {
  const [user, setUser] = useState<User | null>(null);
  const [me, setMe] = useState<MeDto | null>(null);
  const [busy, setBusy] = useState(true);
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
      setMe(null);
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    const auth = getFirebaseAuth();
    return onAuthStateChanged(auth, (next) => {
      setUser(next);
      if (next) void loadMe(next);
      else {
        setMe(null);
        setBusy(false);
      }
    });
  }, [loadMe]);

  const allowed = canAccessShell(me, shell);
  const home = shellHomePath(shellFromMe(me));

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-3xl px-5 sm:px-8">
        <p className="eyebrow text-ember">LMS · {shell}</p>
        <h1 className="mt-4 font-display text-4xl font-bold text-foreground sm:text-5xl">
          {title}
        </h1>
        <p className="mt-4 text-base leading-relaxed text-muted-foreground">{blurb}</p>

        {!user && (
          <div className="mt-10 rounded-2xl border border-border/60 bg-card/40 p-5">
            <p className="text-sm text-muted-foreground">Sign in to open this workspace.</p>
            <Link
              to="/login"
              className="mt-4 inline-flex rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground"
            >
              Sign in
            </Link>
          </div>
        )}

        {user && busy && (
          <p className="mt-8 text-sm text-muted-foreground">Checking your role…</p>
        )}

        {user && error && (
          <p className="mt-8 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </p>
        )}

        {user && me && !allowed && (
          <div className="mt-10 rounded-2xl border border-border/60 bg-card/40 p-5">
            <p className="font-display font-semibold text-foreground">Wrong workspace</p>
            <p className="mt-2 text-sm text-muted-foreground">
              You’re signed in as <span className="text-ember">{me.userRole}</span>
              {me.schoolName ? ` · ${me.schoolName}` : ""}. This area needs a different role.
            </p>
            <Link
              to={home}
              className="mt-4 inline-flex rounded-full bg-maroon/10 px-4 py-2 text-sm font-medium text-maroon hover:bg-maroon/20"
            >
              Go to your home
            </Link>
          </div>
        )}

        {user && me && allowed && (
          <div className="mt-10 space-y-8">
            <div className="rounded-2xl border border-border/60 bg-card/40 p-5">
              <p className="text-sm text-muted-foreground">
                Signed in as{" "}
                <span className="font-medium text-foreground">
                  {me.displayName || me.email}
                </span>
              </p>
              <p className="mt-1 text-xs font-medium text-ember">
                {me.userRole}
                {me.schoolName ? ` · ${me.schoolName}` : ""}
              </p>
            </div>
            <CapabilitiesBoard me={me} activeShell={shell} />
            {typeof children === "function" ? children({ user, me }) : children}
          </div>
        )}
      </div>
    </div>
  );
}
