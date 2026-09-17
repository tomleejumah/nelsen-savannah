import { useCallback, useEffect, useMemo, useState } from "react";
import { createFileRoute, Link, Outlet, useChildMatches, useNavigate } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { ArrowRight, BookOpen, GraduationCap, Layers, LogIn, LogOut, Search } from "lucide-react";

import { CapabilitiesBoard } from "@/components/lms/CapabilitiesBoard";
import {
  EnrollPaywallModal,
  formatTrackPrice,
  useEnrollPaywall,
} from "@/components/lms/EnrollPaywall";
import { RequireMentee } from "@/components/lms/RequireMentee";
import { LMS_TRACKS, modulesForTrack } from "@/data/lms-roadmap.js";
import { LMS_FEATURES } from "@/data/site";
import { getFirebaseAuth } from "@/lib/firebase";
import { bumpAuthGeneration, getAuthGeneration, signOutFully } from "@/lib/lmsAuth";
import {
  fetchLmsMe,
  fetchLmsTracks,
  fetchSchoolsCatalog,
  setActiveSchool,
  type MeDto,
  type SchoolCatalogDto,
  type TrackCardDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/learning")({
  validateSearch: (search: Record<string, unknown>) => ({
    schoolId:
      typeof search.schoolId === "string" && search.schoolId.trim()
        ? search.schoolId.trim()
        : undefined,
  }),
  head: () => ({
    meta: [
      { title: "Learning — Tracks | Nelsen Savannah" },
      {
        name: "description",
        content:
          "Browse and enroll in Nelsen Savannah Innovation Hub learning tracks — Future Safari, Robotics, Data & AI, Creative, Software Engineering, Sauti, and Kijiji Hub.",
      },
      { property: "og:title", content: "Learning | Nelsen Savannah" },
    ],
  }),
  component: LearningLayout,
});

/** Parent of /learning/$trackId, /coursework, /certificates — must render Outlet. */
function LearningLayout() {
  const childMatches = useChildMatches();
  return (
    <RequireMentee>
      {childMatches.length > 0 ? <Outlet /> : <LearningPage />}
    </RequireMentee>
  );
}

function LearningLmsPitch() {
  return (
    <section className="mx-auto mt-24 max-w-7xl px-5 sm:px-8">
      <div className="rounded-3xl border border-border/70 bg-secondary/40 p-8 sm:p-12">
        <h2 className="max-w-3xl text-3xl font-bold sm:text-4xl">
          Schools, skills, and materials in one LMS.
        </h2>
        <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
          From Nelsen Digital to partner school wings — browse vast learning materials, build
          skills module by module, and keep progress with your signed-in account.
        </p>
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {LMS_FEATURES.map((feature) => (
            <div
              key={feature.title}
              className="rounded-2xl border border-border/60 bg-card p-6"
            >
              <GraduationCap className="h-5 w-5 text-ember" />
              <h3 className="mt-4 font-display text-base font-semibold">{feature.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                {feature.detail}
              </p>
            </div>
          ))}
        </div>
        <Link
          to="/contact"
          className="mt-10 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-foreground hover:text-ember"
        >
          Talk to us about your school <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
    </section>
  );
}

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
  price?: TrackCardDto["price"];
};

type CatalogFilter = "all" | "enrolled";

function LearningPage() {
  const navigate = useNavigate({ from: "/learning" });
  const { schoolId: exploreSchoolId } = Route.useSearch();
  const [user, setUser] = useState<User | null>(null);
  const [me, setMe] = useState<MeDto | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [apiTracks, setApiTracks] = useState<TrackCardDto[]>([]);
  const [schools, setSchools] = useState<SchoolCatalogDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<CatalogFilter>("all");

  const previewTracks = useMemo<DisplayTrack[]>(
    () =>
      LMS_TRACKS.map((track) => {
        const modules = modulesForTrack(track.id);
        const minutes = modules.reduce((a: number, m: { estimatedMinutes: number }) => a + m.estimatedMinutes, 0);
        const lessons = modules.reduce((a: number, m: { lessons: unknown[] }) => a + m.lessons.length, 0);
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

  const loadTracks = useCallback(async (u: User, schoolId?: string | null) => {
    setLoading(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      const envelope = await fetchLmsTracks(token, schoolId || undefined);
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

  const paywall = useEnrollPaywall(user, async () => {
    if (user) await loadTracks(user, exploreSchoolId);
  });
  const busyTrack = paywall.busy ? paywall.paywall?.trackId || "busy" : null;

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      const gen = bumpAuthGeneration();
      setUser(next);
      setAuthReady(true);
      if (next) {
        void loadTracks(next, exploreSchoolId);
        void (async () => {
          try {
            const token = await next.getIdToken();
            if (gen !== getAuthGeneration()) return;
            const envelope = await fetchLmsMe(token);
            if (gen !== getAuthGeneration()) return;
            const nextMe = envelope.ok && envelope.data ? envelope.data : null;
            setMe(nextMe);
            if (nextMe?.needsSchoolPick || nextMe?.unaffiliated) {
              const catalog = await fetchSchoolsCatalog(token);
              if (gen !== getAuthGeneration()) return;
              if (catalog.ok && catalog.data?.schools) {
                setSchools(catalog.data.schools);
              }
            }
          } catch {
            if (gen !== getAuthGeneration()) return;
            setMe(null);
          }
        })();
      } else {
        setApiTracks([]);
        setMe(null);
        setSchools([]);
        setFilter("all");
      }
    });
  }, [loadTracks, exploreSchoolId]);

  useEffect(() => {
    if (user && exploreSchoolId) void loadTracks(user, exploreSchoolId);
  }, [exploreSchoolId, loadTracks, user]);

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
    price: t.price,
  }));

  async function onEnroll(trackId: string, title: string) {
    setError(null);
    await paywall.enroll(trackId, title);
    if (paywall.error) setError(paywall.error);
  }

  const catalog = liveTracks.length > 0 ? liveTracks : previewTracks;
  const enrolledTracks = catalog.filter((t) => t.enrolled);
  const showingLive = Boolean(user && liveTracks.length > 0 && !error);

  const filteredTracks = useMemo(() => {
    const q = query.trim().toLowerCase();
    return catalog.filter((track) => {
      if (filter === "enrolled" && !track.enrolled) return false;
      if (!q) return true;
      return (
        track.title.toLowerCase().includes(q) ||
        track.blurb.toLowerCase().includes(q) ||
        track.audience.some((a) => a.toLowerCase().includes(q))
      );
    });
  }, [catalog, filter, query]);

  function onLogout() {
    setMe(null);
    setUser(null);
    setApiTracks([]);
    void signOutFully();
  }

  if (!authReady) {
    return (
      <div className="pb-24 pt-40">
        <p className="text-center text-sm text-muted-foreground">Checking your session…</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="pb-24">
        <section className="relative overflow-hidden bg-background px-5 pb-16 pt-36 sm:px-8 sm:pt-44">
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.08),transparent_55%)]"
          />
          <div className="relative mx-auto max-w-2xl text-center">
            <p className="eyebrow text-ember">Learning</p>
            <h1 className="mt-4 text-4xl font-bold text-foreground sm:text-5xl">
              Sign in to your school wing
            </h1>
            <p className="mx-auto mt-5 max-w-xl text-base leading-relaxed text-muted-foreground">
              Browse tracks across partner schools, build skills with real learning materials, and
              keep enrollments on one account. Sign in to open the catalog.
            </p>
            <Link
              to="/login"
              className="mt-10 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              <LogIn className="h-4 w-4" /> Sign in
            </Link>
          </div>
        </section>
        <LearningLmsPitch />
      </div>
    );
  }

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
            Learning you keep.
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
            {me
              ? me.needsSchoolPick || me.unaffiliated
                ? "Pick a school to browse its tracks — enroll to join that wing. Or explore via a school link."
                : `Learning at ${me.schoolName || "your school"}. Switch schools anytime if you belong to more than one.`
              : "Browse the tracks, enroll, and pick up where you left off."}
          </p>
          {me ? (
            <div className="mx-auto mt-6 max-w-lg rounded-2xl border border-border/70 bg-card/60 px-5 py-4 text-left">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {me.needsSchoolPick || me.unaffiliated
                  ? exploreSchoolId
                    ? "Exploring"
                    : "Choose a school"
                  : "Your school"}
              </p>
              <p className="mt-1 font-display text-lg font-semibold">
                {exploreSchoolId
                  ? schools.find((s) => s.schoolId === exploreSchoolId)?.name ||
                    exploreSchoolId
                  : me.schoolName || me.activeSchoolId || "No school yet"}
              </p>
              {(me.needsSchoolPick || me.unaffiliated) && schools.length > 0 ? (
                <label className="mt-3 block text-sm text-muted-foreground">
                  Schools catalog
                  <select
                    className="mt-1.5 h-10 w-full rounded-xl border border-input bg-background px-3 text-sm text-foreground"
                    value={exploreSchoolId || ""}
                    onChange={(e) => {
                      const id = e.target.value;
                      void navigate({
                        to: "/learning",
                        search: id ? { schoolId: id } : {},
                      });
                    }}
                  >
                    <option value="">All marketplace tracks</option>
                    {schools.map((s) => (
                      <option key={s.schoolId} value={s.schoolId}>
                        {s.name || s.schoolId}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
              {(me.memberships || []).filter((m) => m.status === "active").length > 1 ? (
                <label className="mt-3 block text-sm text-muted-foreground">
                  Switch school
                  <select
                    className="mt-1.5 h-10 w-full rounded-xl border border-input bg-background px-3 text-sm text-foreground"
                    value={me.activeSchoolId || me.schoolId || ""}
                    onChange={(e) => {
                      void (async () => {
                        const token = await user.getIdToken();
                        const result = await setActiveSchool(token, e.target.value);
                        if (result.ok) {
                          const refreshed = await fetchLmsMe(token);
                          if (refreshed.ok && refreshed.data) setMe(refreshed.data);
                          await loadTracks(user, exploreSchoolId);
                        }
                      })();
                    }}
                  >
                    {(me.memberships || [])
                      .filter((m) => m.status === "active")
                      .map((m) => (
                        <option key={m.schoolId} value={m.schoolId}>
                          {m.schoolName || m.schoolId}
                        </option>
                      ))}
                  </select>
                </label>
              ) : null}
            </div>
          ) : null}
          <div className="mx-auto mt-9 max-w-xl space-y-6">
            {me ? (
              <CapabilitiesBoard
                me={me}
                activeShell="student"
                variant="compact"
              />
            ) : null}
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                Account
              </p>
              <div className="mt-2 flex flex-wrap items-center justify-center gap-2">
                <Link
                  to="/profile"
                  className="rounded-full border border-border/70 bg-card px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
                >
                  Profile
                </Link>
                <button
                  type="button"
                  onClick={onLogout}
                  className="inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-card px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
                >
                  <LogOut className="h-3.5 w-3.5" /> Log out
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 sm:px-8">
        <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="eyebrow text-ember">Tracks</p>
            <h2 className="mt-3 text-3xl font-bold sm:text-4xl">
              {loading
                ? "Loading tracks…"
                : filter === "enrolled"
                  ? `${filteredTracks.length} enrolled`
                  : `${filteredTracks.length} learning tracks`}
            </h2>
          </div>

          <div className="flex w-full flex-col gap-3 sm:max-w-md">
            <label className="relative block">
              <span className="sr-only">Search tracks</span>
              <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search tracks…"
                className="w-full rounded-full border border-border bg-card py-2.5 pl-10 pr-4 text-sm text-foreground outline-none ring-ember/40 placeholder:text-muted-foreground focus:ring-2"
              />
            </label>
            {showingLive ? (
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setFilter("all")}
                  className={`rounded-full px-4 py-1.5 text-xs font-semibold transition-colors ${
                    filter === "all"
                      ? "bg-maroon text-maroon-foreground"
                      : "border border-border bg-card text-muted-foreground hover:bg-accent"
                  }`}
                >
                  All
                </button>
                <button
                  type="button"
                  onClick={() => setFilter("enrolled")}
                  className={`rounded-full px-4 py-1.5 text-xs font-semibold transition-colors ${
                    filter === "enrolled"
                      ? "bg-maroon text-maroon-foreground"
                      : "border border-border bg-card text-muted-foreground hover:bg-accent"
                  }`}
                >
                  Enrolled ({enrolledTracks.length})
                </button>
              </div>
            ) : null}
          </div>
        </div>

        {error ? (
          <p className="mt-6 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </p>
        ) : null}

        {showingLive && filter === "enrolled" && enrolledTracks.length === 0 && !query ? (
          <p className="mt-10 text-sm text-muted-foreground">
            You haven’t enrolled in any tracks yet. Switch to All and pick one to start.
          </p>
        ) : null}

        <div className="mt-10 grid gap-6 lg:grid-cols-3">
          {filteredTracks.map((track) => (
              <article
                key={track.id}
                className="relative flex flex-col rounded-3xl border border-border/70 bg-card p-7 transition-shadow hover:shadow-elevated"
              >
                <h3 className="text-xl font-bold leading-snug">{track.title}</h3>
                <p className="mt-3 flex-1 text-sm leading-relaxed text-muted-foreground">
                  {track.blurb}
                </p>
                {(Number(track.moduleCount) > 0 || Number(track.lessons) > 0) ? (
                <div className="mt-6 flex flex-wrap items-center gap-5 border-t border-border/60 pt-4 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    <Layers className="h-3.5 w-3.5 text-ember" /> {track.moduleCount} modules
                  </span>
                  <span className="flex items-center gap-1.5">
                    <BookOpen className="h-3.5 w-3.5 text-ember" /> {track.lessons} lessons
                    {Number(track.hours) > 0 ? ` · ~${track.hours} hrs` : ""}
                  </span>
                </div>
                ) : (
                <p className="mt-6 border-t border-border/60 pt-4 text-xs text-muted-foreground">
                  Content coming soon — enroll to get started.
                </p>
                )}
                {showingLive ? (
                  <div className="mt-4 flex items-center justify-between gap-3">
                    <span className="text-xs font-medium text-foreground">
                      {track.enrolled
                        ? `${track.trackPercent}% complete`
                        : formatTrackPrice(track.price)}
                    </span>
                    {track.enrolled ? (
                      <Link
                        to="/learning/$trackId"
                        params={{ trackId: track.id }}
                        className="rounded-full bg-ember-gradient px-4 py-1.5 text-xs font-semibold text-maroon-foreground shadow-ember-glow"
                      >
                        Continue
                      </Link>
                    ) : (
                      <div className="flex items-center gap-2">
                        <Link
                          to="/learning/$trackId"
                          params={{ trackId: track.id }}
                          className="rounded-full border border-border px-3 py-1.5 text-xs font-semibold text-muted-foreground hover:bg-accent"
                        >
                          View
                        </Link>
                        <button
                          type="button"
                          disabled={busyTrack === track.id}
                          onClick={() => void onEnroll(track.id, track.title)}
                          className="rounded-full bg-maroon/10 px-3 py-1.5 text-xs font-semibold text-maroon hover:bg-maroon/20 disabled:opacity-50"
                        >
                          {busyTrack === track.id ? "…" : "Enroll"}
                        </button>
                      </div>
                    )}
                  </div>
                ) : null}
              </article>
          ))}
        </div>
      </section>

      <LearningLmsPitch />
      {paywall.paywall ? (
        <EnrollPaywallModal
          paywall={paywall.paywall}
          busy={paywall.busy}
          error={paywall.error}
          onClose={() => paywall.setPaywall(null)}
          onPay={() => void paywall.payAndEnroll()}
        />
      ) : null}
    </div>
  );
}
