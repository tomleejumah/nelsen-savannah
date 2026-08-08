import { useCallback, useEffect, useMemo, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import {
  ArrowRight,
  BookOpen,
  GraduationCap,
  Layers,
  LogIn,
  Sparkles,
} from "lucide-react";

import { LMS_FEATURES } from "@/data/site";
import { LMS_MILESTONES, LMS_TRACKS, modulesForTrack } from "@/data/lms-roadmap.js";
import { getFirebaseAuth } from "@/lib/firebase";
import { fetchLmsTracks, enrollInTrack, type TrackCardDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/learning")({
  head: () => ({
    meta: [
      { title: "Learning — Tracks & The Nelsen LMS | Nelsen Savannah" },
      {
        name: "description",
        content:
          "LMS tracks for Sela, Trailblazers, Scripture Safari, Codelab and Mentor Academy — same catalog Android will consume.",
      },
      { property: "og:title", content: "Learning | Nelsen Savannah" },
    ],
  }),
  component: LearningPage,
});

const toneByAudience = {
  Mentee: "brand",
  Mentor: "ember",
  Admin: "maroon",
} as const;

function trackTone(audience: string[]) {
  const key = (audience[0] ?? "Mentee") as keyof typeof toneByAudience;
  return toneByAudience[key] ?? "brand";
}

const toneClass = {
  brand: "bg-brand/10 text-brand-soft",
  ember: "bg-ember/10 text-ember",
  maroon: "bg-maroon/10 text-maroon",
} as const;

type DisplayTrack = {
  id: string;
  title: string;
  blurb: string;
  audience: string[];
  moduleCount: number;
  hours: string;
  lessons: string;
  trackPercent: number;
  enrolled: boolean;
};

function LearningPage() {
  const [user, setUser] = useState<User | null>(null);
  const [apiTracks, setApiTracks] = useState<TrackCardDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const previewTracks = useMemo<DisplayTrack[]>(
    () =>
      LMS_TRACKS.map((track) => {
        const modules = modulesForTrack(track.id);
        const minutes = modules.reduce((a, m) => a + m.estimatedMinutes, 0);
        const lessons = modules.reduce((a, m) => a + m.lessons.length, 0);
        return {
          id: track.id,
          title: track.title,
          blurb: track.blurb,
          audience: track.audience,
          moduleCount: modules.length,
          hours: String(Math.max(1, Math.round(minutes / 60))),
          lessons: String(lessons),
          trackPercent: 0,
          enrolled: false,
        };
      }),
    [],
  );

  const loadTracks = useCallback(async (u: User) => {
    setLoading(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      const envelope = await fetchLmsTracks(token);
      if (!envelope.ok || !envelope.data?.tracks) {
        setApiTracks([]);
        setError(envelope.error || "Could not load tracks");
        return;
      }
      setApiTracks(envelope.data.tracks);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
      setApiTracks([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      setUser(next);
      if (next) void loadTracks(next);
      else setApiTracks([]);
    });
  }, [loadTracks]);

  const [busyTrack, setBusyTrack] = useState<string | null>(null);

  const liveTracks: DisplayTrack[] = apiTracks.map((t) => ({
    id: t.trackId,
    title: t.courseTitle,
    blurb: t.does,
    audience: t.audience,
    moduleCount: t.moduleCount,
    hours: t.duration,
    lessons: t.lessons,
    trackPercent: t.trackPercent,
    enrolled: t.enrolled,
  }));

  async function onEnroll(trackId: string) {
    if (!user) return;
    setBusyTrack(trackId);
    try {
      const token = await user.getIdToken();
      const result = await enrollInTrack(token, trackId);
      if (!result.ok) {
        setError(result.error || "Enroll failed");
        return;
      }
      await loadTracks(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enroll failed");
    } finally {
      setBusyTrack(null);
    }
  }

  const showingLive = Boolean(user && liveTracks.length > 0 && !error);
  const tracks = showingLive ? liveTracks : previewTracks;
  const nextMilestone = LMS_MILESTONES.find((m) => m.status === "planned");

  return (
    <div className="pb-24">
      <section className="relative overflow-hidden bg-background px-5 pb-16 pt-36 sm:px-8 sm:pt-44">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.08),transparent_55%)]"
        />
        <div className="relative mx-auto max-w-4xl text-center">
          <p className="eyebrow text-ember">Learning</p>
          <h1 className="mt-4 text-4xl font-bold text-foreground sm:text-6xl">
            Mentorship you attend. Learning you keep.
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Browse the tracks below. Sign in with the{" "}
            <span className="text-foreground">same Google email as the Android app</span> so your
            progress % stays in sync.
          </p>
          <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
            {!user ? (
              <Link
                to="/login"
                className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
              >
                <LogIn className="h-4 w-4" /> Sign in
              </Link>
            ) : (
              <button
                type="button"
                onClick={() => void loadTracks(user)}
                className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
              >
                Refresh catalog <ArrowRight className="h-4 w-4" />
              </button>
            )}
            <Link
              to="/programs"
              className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-6 py-3 font-display text-sm font-semibold text-foreground transition-colors hover:bg-accent"
            >
              See the programs
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 sm:px-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow text-ember">Tracks</p>
            <h2 className="mt-3 text-3xl font-bold sm:text-4xl">
              {loading ? "Loading tracks…" : `${tracks.length} learning tracks`}
            </h2>
          </div>
          {!user && (
            <p className="max-w-md text-sm leading-relaxed text-muted-foreground">
              Preview of the catalog. Sign in with your app email to enroll and track progress.
            </p>
          )}
          {user && showingLive && (
            <p className="max-w-md text-sm leading-relaxed text-muted-foreground">
              Live catalog for {user.email}
            </p>
          )}
        </div>

        {error && user && (
          <p className="mt-6 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error} — showing catalog preview until the API responds.
          </p>
        )}

        <div className="mt-10 grid gap-6 lg:grid-cols-3">
          {tracks.map((track) => {
            const tone = trackTone(track.audience);
            return (
              <article
                key={track.id}
                className="flex flex-col rounded-3xl border border-border/70 bg-card p-7 transition-shadow hover:shadow-elevated"
              >
                <span
                  className={`self-start rounded-full px-3 py-1 text-xs font-semibold ${toneClass[tone]}`}
                >
                  {track.audience.join(" · ")}
                </span>
                <h3 className="mt-5 text-xl font-bold leading-snug">{track.title}</h3>
                <p className="mt-3 flex-1 text-sm leading-relaxed text-muted-foreground">
                  {track.blurb}
                </p>
                <div className="mt-6 flex items-center gap-5 border-t border-border/60 pt-4 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    <Layers className="h-3.5 w-3.5 text-ember" /> {track.moduleCount} modules
                  </span>
                  <span className="flex items-center gap-1.5">
                    <BookOpen className="h-3.5 w-3.5 text-ember" /> ~{track.hours} hrs ·{" "}
                    {track.lessons} lessons
                  </span>
                </div>
                {showingLive && (
                  <div className="mt-4 flex items-center justify-between gap-3">
                    <span className="text-xs font-medium text-foreground">
                      {track.enrolled ? `${track.trackPercent}% complete` : "Not enrolled"}
                    </span>
                    {track.enrolled ? (
                      <span className="rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold text-brand-soft">
                        Enrolled
                      </span>
                    ) : (
                      <button
                        type="button"
                        disabled={busyTrack === track.id}
                        onClick={() => void onEnroll(track.id)}
                        className="rounded-full bg-maroon/10 px-3 py-1.5 text-xs font-semibold text-maroon hover:bg-maroon/20 disabled:opacity-50"
                      >
                        {busyTrack === track.id ? "…" : "Enroll"}
                      </button>
                    )}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      </section>

      <section className="mx-auto mt-24 max-w-7xl px-5 sm:px-8">
        <div className="rounded-3xl border border-border/70 bg-secondary/40 p-8 sm:p-12">
          <div className="flex flex-wrap items-center gap-3">
            <span className="inline-flex items-center gap-2 rounded-full bg-maroon/10 px-3 py-1 text-xs font-semibold text-maroon">
              <Sparkles className="h-3.5 w-3.5" /> Building
            </span>
            <span className="text-xs text-muted-foreground">
              Next: {nextMilestone?.phase} — {nextMilestone?.title}
            </span>
          </div>
          <h2 className="mt-5 max-w-3xl text-3xl font-bold sm:text-4xl">
            One account. Progress on app and web.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Use the same Google email in the Android app and on this site. Enrollments, lesson %, and
            certificates stay with that account across both.
          </p>

          <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {LMS_FEATURES.map((f) => (
              <div key={f.title} className="rounded-2xl border border-border/60 bg-card p-6">
                <GraduationCap className="h-5 w-5 text-ember" />
                <h3 className="mt-4 font-display text-base font-semibold">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{f.detail}</p>
              </div>
            ))}
          </div>

          <Link
            to="/contact"
            className="mt-10 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-foreground hover:text-ember"
          >
            Talk to us about a track <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </section>
    </div>
  );
}
