import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link, Navigate } from "@tanstack/react-router";
import { onAuthStateChanged } from "firebase/auth";

import { getFirebaseAuth } from "@/lib/firebase";
import { bumpAuthGeneration, getAuthGeneration } from "@/lib/lmsAuth";
import { fetchLmsMe, type MeDto } from "@/lib/lmsApi";
import { canAccessShell, shellFromMe, shellHomePath } from "@/lib/lmsRoles";

/** Learning UI is mentee-only — mentors/admins land on their workspace. */
export function RequireMentee({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<MeDto | null>(null);
  const [ready, setReady] = useState(false);
  const [signedIn, setSignedIn] = useState(false);

  const loadMe = useCallback(async (token: string, gen: number) => {
    try {
      const envelope = await fetchLmsMe(token);
      if (gen !== getAuthGeneration()) return;
      setMe(envelope.ok && envelope.data ? envelope.data : null);
    } catch {
      if (gen !== getAuthGeneration()) return;
      setMe(null);
    } finally {
      if (gen === getAuthGeneration()) setReady(true);
    }
  }, []);

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (user) => {
      const gen = bumpAuthGeneration();
      setSignedIn(Boolean(user));
      if (!user) {
        setMe(null);
        setReady(true);
        return;
      }
      setReady(false);
      void user.getIdToken().then((token) => loadMe(token, gen));
    });
  }, [loadMe]);

  if (!ready) {
    return (
      <div className="pb-24 pt-32 sm:pt-40">
        <p className="mx-auto max-w-3xl px-5 text-sm text-muted-foreground sm:px-8">
          Checking access…
        </p>
      </div>
    );
  }

  if (!signedIn) {
    return (
      <div className="pb-24 pt-32 sm:pt-40">
        <div className="mx-auto max-w-3xl px-5 sm:px-8">
          <p className="eyebrow text-ember">LMS · mentee</p>
          <h1 className="mt-4 font-display text-4xl font-bold">Learning</h1>
          <p className="mt-4 text-muted-foreground">
            Sign in with a mentee account to browse tracks and coursework.
          </p>
          <Link
            to="/login"
            className="mt-8 inline-flex rounded-full bg-ember-gradient px-5 py-2.5 text-sm font-semibold text-maroon-foreground"
          >
            Sign in
          </Link>
        </div>
      </div>
    );
  }

  if (!canAccessShell(me, "student")) {
    return <Navigate to={shellHomePath(shellFromMe(me))} replace />;
  }

  return <>{children}</>;
}
