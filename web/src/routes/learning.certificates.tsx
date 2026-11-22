import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { Award, Download, LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  fetchLmsMe,
  downloadMyCertificatePdf,
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
  score,
  issuedAt,
  certificateId,
}: {
  learnerName: string;
  courseTitle?: string;
  score?: number;
  issuedAt?: number;
  certificateId?: string;
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
        {score != null ? (
          <p className="mt-4 text-sm font-semibold text-foreground">
            Overall course result: {score}%
          </p>
        ) : null}
        {issuedAt ? (
          <p className="mt-2 text-xs text-muted-foreground">
            Issued {new Date(issuedAt).toLocaleDateString()}
          </p>
        ) : null}
        {certificateId ? (
          <p className="mt-2 text-xs text-muted-foreground">
            Certificate ID: {certificateId}
          </p>
        ) : null}
        <div className="mx-auto mt-10 flex max-w-md items-center justify-between gap-4 border-t border-border/60 pt-6 text-xs text-muted-foreground">
          <span>{score != null ? "Verified course completion" : `Issued at ${PASS_THRESHOLD}% or higher`}</span>
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
  const [downloadingTrackId, setDownloadingTrackId] = useState<string | null>(null);

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

  const downloadCertificate = async (certificate: CertificateDto) => {
    if (!user) return;
    setDownloadingTrackId(certificate.trackId);
    try {
      const token = await user.getIdToken();
      const blob = await downloadMyCertificatePdf(token, certificate.trackId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `nelsen-certificate-${certificate.certificateId}.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not download certificate");
    } finally {
      setDownloadingTrackId(null);
    }
  };
  const earnedTrackIds = new Set(items.map((certificate) => certificate.trackId));
  const awaitingCertificates = enrollments.filter(
    (enrollment) => !earnedTrackIds.has(enrollment.trackId),
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
                learnerName={preview?.learnerName || learnerName}
                courseTitle={preview?.courseTitle}
                score={preview?.overallScore}
                issuedAt={preview?.issuedAt}
                certificateId={preview?.certificateId}
              />
            </section>

            <section>
              <h2 className="font-display text-lg font-semibold">Earned</h2>
              {items.length === 0 ? (
                <p className="mt-3 text-sm text-muted-foreground">
                  None issued yet. Your certificate appears here once your overall course assessment score reaches at least 80%.
                </p>
              ) : (
                <ul className="mt-4 space-y-3">
                  {items.map((c) => (
                    <li
                      key={`${c.trackId}-${c.issuedAt}`}
                      className="flex flex-wrap items-center gap-3 rounded-2xl border border-border/70 bg-card px-5 py-4"
                    >
                      <span className="icon-chip">
                        <Award className="h-5 w-5" />
                      </span>
                      <button
                        type="button"
                        onClick={() => setPreview(c)}
                        className="min-w-0 flex-1 text-left"
                      >
                        <p className="font-display font-semibold">{c.courseTitle}</p>
                        <p className="mt-1 text-sm text-muted-foreground">
                          {c.learnerName} · {new Date(c.issuedAt).toLocaleDateString()} ·{" "}
                          {c.overallScore}%
                        </p>
                        <p className="mt-1 text-xs text-muted-foreground">{c.certificateId}</p>
                      </button>
                      <button
                        type="button"
                        onClick={() => void downloadCertificate(c)}
                        disabled={downloadingTrackId === c.trackId}
                        className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-4 py-2 text-xs font-semibold text-maroon-foreground disabled:opacity-60"
                      >
                        <Download className="h-4 w-4" />
                        {downloadingTrackId === c.trackId ? "Preparing…" : "Download PDF"}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {awaitingCertificates.length > 0 ? (
              <section>
                <h2 className="font-display text-lg font-semibold">Certificates still locked</h2>
                <p className="mt-2 rounded-xl border border-ember/20 bg-ember/5 px-4 py-3 text-sm text-muted-foreground">
                  To unlock a certificate, obtain at least {PASS_THRESHOLD}% overall across the course assessments.
                  Complete every course quiz and required assignment so your final score can be calculated.
                </p>
                <ul className="mt-4 space-y-2">
                  {awaitingCertificates.map((enrollment) => (
                    <li key={enrollment.trackId}>
                      <Link
                        to="/learning/$trackId"
                        params={{ trackId: enrollment.trackId }}
                        className="flex items-center justify-between rounded-2xl border border-border/60 px-4 py-3 text-sm hover:bg-accent/40"
                      >
                        <span>{enrollment.courseTitle || enrollment.trackId}</span>
                        <span className="text-muted-foreground">Certificate locked</span>
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
