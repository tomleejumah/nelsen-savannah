import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link, Navigate } from "@tanstack/react-router";
import { onAuthStateChanged } from "firebase/auth";

import { getFirebaseAuth } from "@/lib/firebase";
import { getAuthGeneration, syncAuthGeneration } from "@/lib/lmsAuth";
import { fetchLmsMe, type MeDto } from "@/lib/lmsApi";
import { canAccessShell, shellFromMe, shellHomePath } from "@/lib/lmsRoles";

const ACCESS_TIMEOUT_MS = 12_000;

function withTimeout<T>(promise: Promise<T>, ms = ACCESS_TIMEOUT_MS): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) =>
      window.setTimeout(() => reject(new Error("Access check timed out")), ms),
    ),
  ]);
}

/** Learning UI is mentee-only — mentors/admins land on their workspace. */
export function RequireMentee({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<MeDto | null>(null);
  const [ready, setReady] = useState(false);
  const [signedIn, setSignedIn] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  const loadMe = useCallback(async (token: string, gen: number) => {
    try {
      const envelope = await withTimeout(fetchLmsMe(token));
      if (gen !== getAuthGeneration()) return;
      if (!envelope.ok || !envelope.data) {
        setMe(null);
        setError(envelope.error || "Could not verify LMS access.");
        return;
      }
      setMe(envelope.data);
      setError(null);
    } catch (err) {
      if (gen !== getAuthGeneration()) return;
      setMe(null);
      setError(err instanceof Error ? err.message : "Could not verify LMS access.");
    } finally {
      if (gen === getAuthGeneration()) setReady(true);
    }
  }, []);

  useEffect(() => {
    const auth = getFirebaseAuth();
    const current = auth.currentUser;

    async function resolveUser(user: NonNullable<typeof current>, gen: number) {
      try {
        const token = await withTimeout(user.getIdToken());
        if (gen !== getAuthGeneration()) return;
        await loadMe(token, gen);
      } catch (err) {
        if (gen !== getAuthGeneration()) return;
        setMe(null);
        setError(err instanceof Error ? err.message : "Could not verify your session.");
        setReady(true);
      }
    }

    const unsubscribe = onAuthStateChanged(auth, (user) => {
      const gen = syncAuthGeneration(user?.uid ?? null);
      setSignedIn(Boolean(user));
      setError(null);
      if (!user) {
        setMe(null);
        setReady(true);
        return;
      }
      setReady(false);
      void resolveUser(user, gen);
    });

    // Firebase may already have restored currentUser before this listener settles.
    // The listener remains authoritative, but this guarantees a bounded access check.
    if (current) {
      const gen = syncAuthGeneration(current.uid);
      setSignedIn(true);
      setReady(false);
      void resolveUser(current, gen);
    }

    return unsubscribe;
  }, [loadMe, retryKey]);

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
          <Link to="/login" className="mt-8 inline-flex rounded-full bg-ember-gradient px-5 py-2.5 text-sm font-semibold text-maroon-foreground">
            Sign in
          </Link>
        </div>
      </div>
    );
  }

  if (error || !me) {
    return (
      <div className="pb-24 pt-32 sm:pt-40">
        <div className="mx-auto max-w-3xl px-5 sm:px-8">
          <p className="eyebrow text-ember">LMS access</p>
          <h1 className="mt-4 font-display text-3xl font-bold">Could not verify access</h1>
          <p className="mt-4 text-sm text-muted-foreground">
            {error || "Your LMS profile could not be loaded."}
          </p>
          <button
            type="button"
            onClick={() => { setReady(false); setError(null); setRetryKey((v) => v + 1); }}
            className="mt-6 rounded-full bg-ember-gradient px-5 py-2.5 text-sm font-semibold text-maroon-foreground"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!canAccessShell(me, "student")) {
    return <Navigate to={shellHomePath(shellFromMe(me))} replace />;
  }

  return <>{children}</>;
}
