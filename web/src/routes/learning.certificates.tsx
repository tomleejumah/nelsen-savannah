import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { Award, Download, LogIn } from "lucide-react";

import nelsenLogo from "@/assets/nelsen-logo.png";
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
  const hasMeta = Boolean(issuedAt || certificateId || score != null);
  return (
    <div className="relative overflow-hidden rounded-2xl border border-hairline bg-cream px-8 pb-12 pt-14 text-center shadow-elevated sm:px-12">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.07]"
        style={{
          backgroundImage: `url("data:image/svg+xml,${encodeURIComponent(
            `<svg xmlns='http://www.w3.org/2000/svg' width='220' height='90'><text x='10' y='50' transform='rotate(-24 110 45)' font-family='Arial' font-weight='700' font-size='11' letter-spacing='3' fill='#A84A38'>NELSEN SAVANNAH</text></svg>`,
          )}")`,
        }}
      />
      <div aria-hidden className="pointer-events-none absolute inset-2 rounded-xl border-2 border-navy" />
      <div aria-hidden className="pointer-events-none absolute inset-3.5 rounded-lg border border-ochre" />
      <img
        src={nelsenLogo}
        alt="Nelsen Savannah"
        className="mx-auto h-12 w-auto mix-blend-multiply sm:h-14"
      />
      <p className="mt-8 text-[11px] font-semibold uppercase tracking-[0.22em] text-brick">
        Certificate of completion
      </p>
      <h2 className="mt-5 font-display text-3xl font-semibold text-navy sm:text-4xl">
        {learnerName || "Your name"}
      </h2>
      <p className="mt-4 text-sm text-muted-foreground">has successfully completed</p>
      <p className="mt-1 font-display text-xl font-semibold text-ink sm:text-2xl">
        {courseTitle || "Learning track"}
      </p>

      <div className="mt-10 flex items-center justify-center gap-6 sm:gap-10">
        <div className="hidden w-28 text-xs text-muted-foreground sm:block">
          {issuedAt ? (
            <>
              <p className="font-medium text-ink">
                {new Date(issuedAt).toLocaleDateString(undefined, {
                  day: "numeric",
                  month: "short",
                  year: "numeric",
                })}
              </p>
              <p>Date issued</p>
            </>
          ) : null}
        </div>
        <span className="flex h-16 w-16 items-center justify-center rounded-full bg-ochre text-ink outline outline-2 outline-offset-4 outline-ochre">
          <Award className="h-7 w-7" />
        </span>
        <div className="hidden w-28 text-xs text-muted-foreground sm:block">
          {certificateId ? (
            <>
              <p className="font-medium text-ink">{certificateId}</p>
              <p>Certificate ID</p>
            </>
          ) : null}
        </div>
      </div>
      {hasMeta && score != null ? (
        <p className="mt-6 text-xs text-muted-foreground">Overall result: {score}%</p>
      ) : null}
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
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
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
          Earn a certificate by averaging at least {PASS_THRESHOLD}% across all course quizzes
          and graded assignments. Every configured assessment must be completed first.
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
