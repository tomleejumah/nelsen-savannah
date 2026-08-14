import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { Award, LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import { fetchMyCertificates, type CertificateDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/learning/certificates")({
  head: () => ({
    meta: [
      { title: "Certificates — Nelsen Savannah" },
      {
        name: "description",
        content: "Certificates earned when you complete LMS tracks.",
      },
    ],
  }),
  component: CertificatesPage,
});

function CertificatesPage() {
  const [user, setUser] = useState<User | null>(null);
  const [items, setItems] = useState<CertificateDto[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (u: User) => {
    setBusy(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      const envelope = await fetchMyCertificates(token);
      if (!envelope.ok || !envelope.data) {
        setItems([]);
        setError(envelope.error || "Could not load certificates");
        return;
      }
      setItems(envelope.data.certificates || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
      setItems([]);
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      setUser(next);
      if (next) void load(next);
      else {
        setItems([]);
        setBusy(false);
      }
    });
  }, [load]);

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-3xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Student</p>
        <h1 className="mt-4 font-display text-4xl font-bold">Certificates</h1>
        <p className="mt-3 text-muted-foreground">
          Issued when a track hits the pass threshold and required assignments are marked.
        </p>
        <div className="mt-4 flex flex-wrap gap-3 text-sm">
          <Link to="/learning" className="font-medium text-maroon hover:underline">
            ← Catalog
          </Link>
          <Link
            to="/learning/coursework"
            className="font-medium text-maroon hover:underline"
          >
            Coursework
          </Link>
        </div>

        {!user ? (
          <div className="mt-10">
            <Link
              to="/login"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground"
            >
              <LogIn className="h-4 w-4" /> Sign in
            </Link>
          </div>
        ) : busy ? (
          <p className="mt-10 text-sm text-muted-foreground">Loading…</p>
        ) : error ? (
          <p className="mt-10 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </p>
        ) : items.length === 0 ? (
          <p className="mt-10 text-sm text-muted-foreground">
            No certificates yet. Finish a track (and any required assignments) to earn one.
          </p>
        ) : (
          <ul className="mt-10 space-y-3">
            {items.map((c) => (
              <li
                key={`${c.trackId}-${c.issuedAt}`}
                className="flex gap-4 rounded-2xl border border-border/70 bg-card px-5 py-4"
              >
                <span className="icon-chip">
                  <Award className="h-5 w-5" />
                </span>
                <div className="min-w-0">
                  <p className="font-display font-semibold">{c.courseTitle}</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {new Date(c.issuedAt).toLocaleDateString()} · {c.trackPercent}%
                  </p>
                  {c.verifyUrl ? (
                    <a
                      href={c.verifyUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="mt-2 inline-flex text-sm font-medium text-maroon hover:underline"
                    >
                      Verify link
                    </a>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
