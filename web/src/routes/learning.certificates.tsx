import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { Award, LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  fetchLmsMe,
  fetchMyCertificates,
  fetchMyEnrollments,
  type CertificateDto,
  type EnrollmentDto,
  type MeDto,
} from "@/lib/lmsApi";

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

const PASS_THRESHOLD = 80;

function BlankCertificatePreview({
  learnerName,
  courseTitle,
}: {
  learnerName: string;
  courseTitle?: string;
}) {
  return (
    <div className="relative overflow-hidden rounded-3xl border border-ember/30 bg-card p-8 shadow-elevated sm:p-10">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.08),transparent_55%)]"
      />
      <div className="relative text-center">
        <p className="eyebrow text-ember">Certificate of completion</p>
        <h2 className="mt-4 font-display text-2xl font-bold sm:text-3xl">
          Nelsen Savannah
        </h2>
        <p className="mt-6 text-sm text-muted-foreground">This certifies that</p>
        <p className="mt-2 font-display text-xl font-semibold text-foreground sm:text-2xl">
          {learnerName || "Your name"}
        </p>
        <p className="mt-6 text-sm text-muted-foreground">
          has completed the learning track
        </p>
        <p className="mt-2 font-display text-lg font-semibold text-maroon">
          {courseTitle || "— track title —"}
        </p>
        <div className="mx-auto mt-10 flex max-w-md items-center justify-between gap-4 border-t border-border/60 pt-6 text-xs text-muted-foreground">
          <span>Issued when you pass ({PASS_THRESHOLD}%+)</span>
          <span className="icon-chip">
            <Award className="h-4 w-4" />
          </span>
        </div>
      </div>
    </div>
  );
}

function CertificatesPage() {
  const [user, setUser] = useState<User | null>(null);
  const [me, setMe] = useState<MeDto | null>(null);
  const [items, setItems] = useState<CertificateDto[]>([]);
  const [enrollments, setEnrollments] = useState<EnrollmentDto[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<CertificateDto | null>(null);

  const load = useCallback(async (u: User) => {
    setBusy(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      const [certs, enrolls, meEnv] = await Promise.all([
        fetchMyCertificates(token),
        fetchMyEnrollments(token),
        fetchLmsMe(token),
      ]);
      if (!certs.ok || !certs.data) {
        setItems([]);
        setError(certs.error || "Could not load certificates");
      } else {
        setItems(certs.data.certificates || []);
      }
      setEnrollments(enrolls.data?.enrollments || []);
      setMe(meEnv.ok && meEnv.data ? meEnv.data : null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
      setItems([]);
      setEnrollments([]);
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
        setEnrollments([]);
        setMe(null);
        setBusy(false);
      }
    });
  }, [load]);

  const learnerName =
    me?.displayName || user?.displayName || me?.email || "Learner";
  const towardCert = enrollments.filter(
    (e) => e.status !== "certified" && (e.trackPercent ?? 0) < PASS_THRESHOLD,
  );
  const readySoon = enrollments.filter(
    (e) =>
      e.status !== "certified" && (e.trackPercent ?? 0) >= PASS_THRESHOLD,
  );

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-3xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Student</p>
        <h1 className="mt-4 font-display text-4xl font-bold">Certificates</h1>
        <p className="mt-3 text-muted-foreground">
          Issued when a track hits {PASS_THRESHOLD}% and required assignments are marked.
          Open a blank preview anytime — earned certificates appear below.
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
          <Link to="/profile" className="font-medium text-maroon hover:underline">
            Profile
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
        ) : (
          <div className="mt-10 space-y-10">
            <section>
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <h2 className="font-display text-lg font-semibold">
                  {preview ? "Certificate" : "Blank certificate"}
                </h2>
                {preview ? (
                  <button
                    type="button"
                    onClick={() => setPreview(null)}
                    className="text-sm font-medium text-maroon hover:underline"
                  >
                    Show blank preview
                  </button>
                ) : null}
              </div>
              <BlankCertificatePreview
                learnerName={learnerName}
                courseTitle={preview?.courseTitle}
              />
            </section>

            <section>
              <h2 className="font-display text-lg font-semibold">Earned</h2>
              {items.length === 0 ? (
                <p className="mt-3 text-sm text-muted-foreground">
                  None issued yet. Finish a track to unlock a filled certificate here.
                </p>
              ) : (
                <ul className="mt-4 space-y-3">
                  {items.map((c) => (
                    <li key={`${c.trackId}-${c.issuedAt}`}>
                      <button
                        type="button"
                        onClick={() => setPreview(c)}
                        className="flex w-full gap-4 rounded-2xl border border-border/70 bg-card px-5 py-4 text-left transition-colors hover:border-ember/40 hover:bg-accent/40"
                      >
                        <span className="icon-chip">
                          <Award className="h-5 w-5" />
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="font-display font-semibold">{c.courseTitle}</p>
                          <p className="mt-1 text-sm text-muted-foreground">
                            {new Date(c.issuedAt).toLocaleDateString()} ·{" "}
                            {c.trackPercent}%
                          </p>
                        </div>
                        <span className="shrink-0 self-center text-xs font-semibold text-ember">
                          Open
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {readySoon.length > 0 ? (
              <section>
                <h2 className="font-display text-lg font-semibold">Ready to certify</h2>
                <p className="mt-2 text-sm text-muted-foreground">
                  Track progress is at the pass mark — complete required mentor-marked
                  assignments if any are still open.
                </p>
                <ul className="mt-4 space-y-2">
                  {readySoon.map((e) => (
                    <li key={e.trackId}>
                      <Link
                        to="/learning/$trackId"
                        params={{ trackId: e.trackId }}
                        className="flex items-center justify-between rounded-2xl border border-border/60 px-4 py-3 text-sm hover:bg-accent/40"
                      >
                        <span>{e.courseTitle || e.trackId}</span>
                        <span className="text-ember">{e.trackPercent}%</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}

            {towardCert.length > 0 ? (
              <section>
                <h2 className="font-display text-lg font-semibold">In progress</h2>
                <ul className="mt-4 space-y-2">
                  {towardCert.map((e) => (
                    <li key={e.trackId}>
                      <Link
                        to="/learning/$trackId"
                        params={{ trackId: e.trackId }}
                        className="flex items-center justify-between rounded-2xl border border-border/60 px-4 py-3 text-sm hover:bg-accent/40"
                      >
                        <span>{e.courseTitle || e.trackId}</span>
                        <span className="text-muted-foreground">
                          {e.trackPercent}% / {PASS_THRESHOLD}%
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}
