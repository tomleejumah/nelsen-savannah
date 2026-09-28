import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { onAuthStateChanged } from "firebase/auth";

import { getFirebaseAuth } from "@/lib/firebase";
import { bumpAuthGeneration, getAuthGeneration } from "@/lib/lmsAuth";
import {
  acceptJoinInvite,
  fetchJoinInvite,
  fetchLmsMe,
  type JoinInviteDto,
} from "@/lib/lmsApi";
import { shellFromMe, shellHomePath } from "@/lib/lmsRoles";

export const Route = createFileRoute("/join/$token")({
  head: () => ({
    meta: [
      { title: "Join school — Nelsen Savannah LMS" },
      { name: "description", content: "Accept a school invite." },
    ],
  }),
  component: JoinPage,
});

function JoinPage() {
  const { token } = Route.useParams();
  const navigate = useNavigate();
  const [invite, setInvite] = useState<JoinInviteDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(true);
  const [signedIn, setSignedIn] = useState(false);

  useEffect(() => {
    let alive = true;
    void fetchJoinInvite(token).then((res) => {
      if (!alive) return;
      if (!res.ok || !res.data) {
        setError(res.error || "Invite not found");
        setInvite(null);
      } else {
        setInvite(res.data);
      }
      setBusy(false);
    });
    return () => {
      alive = false;
    };
  }, [token]);

  const claim = useCallback(
    async (idToken: string, gen: number) => {
      const result = await acceptJoinInvite(idToken, token);
      if (gen !== getAuthGeneration()) return;
      if (!result.ok) {
        setError(result.error || "Could not accept invite");
        return;
      }
      const me = await fetchLmsMe(idToken);
      if (gen !== getAuthGeneration()) return;
      const home = shellHomePath(shellFromMe(me.data));
      void navigate({ to: home });
    },
    [navigate, token],
  );

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (user) => {
      const gen = bumpAuthGeneration();
      setSignedIn(Boolean(user));
      if (!user) return;
      void user.getIdToken().then((t) => claim(t, gen));
    });
  }, [claim]);

  if (busy) {
    return (
      <div className="pb-24 pt-32 sm:pt-40">
        <p className="mx-auto max-w-lg px-5 text-sm text-muted-foreground">
          Loading invite…
        </p>
      </div>
    );
  }

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-lg px-5 sm:px-8">
        <p className="eyebrow text-ember">School invite</p>
        <h1 className="mt-4 font-display text-4xl font-bold">
          {invite ? `Join ${invite.schoolName}` : "Invite"}
        </h1>
        {error ? (
          <p className="mt-4 text-sm text-destructive">{error}</p>
        ) : (
          <p className="mt-4 text-muted-foreground">
            {invite?.email
              ? `Sign in with ${invite.email} to join as ${invite.role}.`
              : "Sign in to join this school."}
          </p>
        )}
        {!signedIn && invite && !error ? (
          <Link
            to="/login"
            search={{ next: `/join/${token}` }}
            className="mt-8 inline-flex cursor-pointer rounded-full bg-ember-gradient px-5 py-2.5 text-sm font-semibold text-maroon-foreground"
          >
            Sign in to accept
          </Link>
        ) : null}
      </div>
    </div>
  );
}
