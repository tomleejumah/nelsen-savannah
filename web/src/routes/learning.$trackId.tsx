import { useCallback, useEffect, useMemo, useState } from "react";
import {
  createFileRoute,
  Link,
  Outlet,
  useChildMatches,
} from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { ArrowLeft, ArrowRight, BookOpen, Layers, LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  EnrollPaywallModal,
  formatTrackPrice,
  useEnrollPaywall,
} from "@/components/lms/EnrollPaywall";
import {
  fetchLmsModule,
  fetchLmsTrack,
  fetchMyProgress,
  type LessonDto,
  type TrackDetailDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/learning/$trackId")({
  head: ({ params }) => ({
    meta: [
      {
        title: `${params.trackId} — Learning | Nelsen Savannah`,
      },
    ],
  }),
  component: TrackLayout,
});

/** Parent of /learning/$trackId/lesson/$lessonId — must render Outlet for lessons. */
function TrackLayout() {
  const childMatches = useChildMatches();
  if (childMatches.length > 0) return <Outlet />;
  return <TrackDetailPage />;
}

type ModuleWithLessons = {
  moduleId: string;
  title: string;
  does: string;
  lessonCount: number;
  modulePercent?: number;
  releaseAt?: number | null;
  dueAt?: number | null;
  lessons: LessonDto[];
};

function TrackDetailPage() {
  const { trackId } = Route.useParams();
  const [user, setUser] = useState<User | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [detail, setDetail] = useState<TrackDetailDto | null>(null);
  const [modules, setModules] = useState<ModuleWithLessons[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (u: User) => {
      setLoading(true);
      setError(null);
      try {
        const token = await u.getIdToken();
        const [trackEnv, progressEnv] = await Promise.all([
          fetchLmsTrack(token, trackId),
          fetchMyProgress(token, trackId),
        ]);
        if (!trackEnv.ok || !trackEnv.data) {
          setDetail(null);
          setModules([]);
          setError(trackEnv.error || "Could not load this track");
          return;
        }
        setDetail(trackEnv.data);

        const byLesson = progressEnv.data?.byLessonId ?? {};
        const loaded = await Promise.all(
          trackEnv.data.modules.map(async (mod) => {
            const modEnv = await fetchLmsModule(token, mod.moduleId);
            const lessons = (modEnv.data?.lessons ?? []).map((lesson) => {
              const prog = byLesson[lesson.lessonId];
              return {
                ...lesson,
                lessonPercent: prog?.lessonPercent ?? lesson.lessonPercent ?? 0,
                status: prog?.status ?? lesson.status ?? "available",
              };
            });
            return {
              moduleId: mod.moduleId,
              title: mod.title,
              does: mod.does,
              lessonCount: lessons.length || mod.lessonCount,
              modulePercent: mod.modulePercent,
              releaseAt: mod.releaseAt ?? null,
              dueAt: mod.dueAt ?? null,
              lessons,
            };
          }),
        );
        setModules(loaded);
      } catch (err) {
        setDetail(null);
        setModules([]);
        setError(err instanceof Error ? err.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [trackId],
  );

  const paywall = useEnrollPaywall(user, async () => {
    if (user) await load(user);
  });

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      setUser(next);
      setAuthReady(true);
      if (next) void load(next);
      else {
        setDetail(null);
        setModules([]);
      }
    });
  }, [load]);

  const track = detail?.track;
  const enrolled = Boolean(detail?.enrollment || track?.enrolled);
  const percent =
    detail?.enrollment?.trackPercent ?? track?.trackPercent ?? 0;
  const lessonTotal = modules.reduce((n, m) => n + m.lessons.length, 0);
  const hasCurriculum = lessonTotal > 0;

  async function onEnroll() {
    await paywall.enroll(trackId, track?.courseTitle);
  }

  const continueLessonId = useMemo(() => {
    if (!hasCurriculum) return null;
    const locks = new Map(
      (detail?.cohortRun?.milestones || []).map((m) => [m.lessonId, m]),
    );
    for (const mod of modules) {
      for (const lesson of mod.lessons) {
        const mile = locks.get(lesson.lessonId);
        if (mile && !mile.available) continue;
        if ((lesson.lessonPercent ?? 0) < 80) return lesson.lessonId;
      }
    }
    return modules[0]?.lessons[0]?.lessonId ?? null;
  }, [modules, detail, hasCurriculum]);

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-3xl px-5 sm:px-8">
        <Link
          to="/learning"
          className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> All tracks
        </Link>

        {!authReady || (user && loading && !detail) ? (
          <p className="mt-10 text-sm text-muted-foreground">Loading track…</p>
        ) : !user ? (
          <div className="mt-10 space-y-4">
            <h1 className="text-3xl font-bold">Sign in to open this track</h1>
            <Link
              to="/login"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow"
            >
              <LogIn className="h-4 w-4" /> Sign in
            </Link>
          </div>
        ) : error && !detail ? (
          <p className="mt-10 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </p>
        ) : track ? (
          <>
            {!enrolled ? (
              <section className="mt-10">
                <p className="eyebrow text-ember">Welcome</p>
                <h1 className="mt-3 text-3xl font-bold sm:text-5xl">
                  Welcome to {track.courseTitle}
                </h1>
                <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
                  {track.does ||
                    "Enroll to join this course. Your mentor will add lessons, videos, and assignments as the program unfolds."}
                </p>
                <p className="mt-3 text-sm text-muted-foreground">
                  {formatTrackPrice(track.price)}
                  {hasCurriculum
                    ? ` · ${modules.length} modules · ${lessonTotal} lessons`
                    : " · Materials will appear after you enroll"}
                </p>
                {error ? (
                  <p className="mt-4 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
                    {error}
                  </p>
                ) : null}
                <button
                  type="button"
                  disabled={paywall.busy}
                  onClick={() => void onEnroll()}
                  className="mt-8 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow disabled:opacity-60"
                >
                  {paywall.busy
                    ? "Working…"
                    : track.price?.isPaid
                      ? `Enroll · ${formatTrackPrice(track.price)}`
                      : "Enroll in this track"}
                </button>
              </section>
            ) : (
              <>
                <h1 className="mt-10 text-3xl font-bold sm:text-5xl">
                  {track.courseTitle}
                </h1>
                <p className="mt-4 text-base leading-relaxed text-muted-foreground">
                  {track.does}
                </p>
                {track.tutorName ? (
                  <p className="mt-2 text-sm text-muted-foreground">
                    Mentor:{" "}
                    <span className="font-medium text-foreground">
                      {track.tutorName}
                    </span>
                  </p>
                ) : null}

                {hasCurriculum ? (
                  <div className="mt-6 flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
                    <span className="inline-flex items-center gap-1.5">
                      <Layers className="h-4 w-4 text-ember" />
                      {modules.length} modules
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <BookOpen className="h-4 w-4 text-ember" />
                      {lessonTotal} lessons
                      {Number(track.duration) > 0
                        ? ` · ~${track.duration} hrs`
                        : ""}
                    </span>
                    <span className="rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold text-brand-soft">
                      {percent}% complete
                    </span>
                  </div>
                ) : (
                  <p className="mt-6 rounded-2xl border border-border/70 bg-card/50 px-5 py-4 text-sm text-muted-foreground">
                    You’re enrolled. Your mentor is still preparing materials —
                    check back soon, or open Coursework if an assignment is
                    posted.
                  </p>
                )}

                {error ? (
                  <p className="mt-4 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
                    {error}
                  </p>
                ) : null}

                {continueLessonId ? (
                  <Link
                    to="/learning/$trackId/lesson/$lessonId"
                    params={{ trackId, lessonId: continueLessonId }}
                    className="mt-8 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow"
                  >
                    Continue learning <ArrowRight className="h-4 w-4" />
                  </Link>
                ) : null}

                {detail.cohortRun?.milestones?.length ? (
                  <section className="mt-12">
                    <h2 className="text-xl font-bold">Cohort walkthrough</h2>
                    <ol className="mt-4 space-y-2">
                      {detail.cohortRun.milestones.map((m, i) => (
                        <li
                          key={m.milestoneId}
                          className="rounded-xl border border-border/70 bg-card px-4 py-3 text-sm"
                        >
                          <span className="font-medium">
                            {i + 1}. {m.title || m.lessonId}
                          </span>
                          <span className="ml-2 text-xs text-muted-foreground">
                            {m.completed
                              ? "done"
                              : m.available
                                ? "open"
                                : m.lockedReason === "release_date"
                                  ? `unlocks ${new Date(m.releaseAt).toLocaleString()}`
                                  : m.lockedReason === "expired"
                                    ? `locked · expired ${m.dueAt ? new Date(m.dueAt).toLocaleString() : ""}`
                                    : "complete the previous milestone first"}
                          </span>
                        </li>
                      ))}
                    </ol>
                  </section>
                ) : null}

                {hasCurriculum ? (
                  <section className="mt-12">
                    <h2 className="text-xl font-bold">Chapters & lessons</h2>
                    <div className="mt-6 space-y-6">
                      {modules.map((mod, i) => {
                        const now = Date.now();
                        const hasWindow = Boolean(mod.releaseAt && mod.dueAt);
                        const chapterReleased =
                          !hasWindow || Number(mod.releaseAt) <= now;
                        const chapterExpired =
                          hasWindow && Number(mod.dueAt) < now;
                        const chapterLockedReason = !hasWindow
                          ? null
                          : !chapterReleased
                            ? "release_date"
                            : chapterExpired
                              ? "expired"
                              : null;
                        return (
                        <div
                          key={mod.moduleId}
                          className="rounded-2xl border border-border/70 bg-card p-5 sm:p-6"
                        >
                          <p className="text-xs font-medium text-muted-foreground">
                            Chapter {i + 1}
                          </p>
                          <h3 className="mt-1 font-display text-lg font-semibold">
                            {mod.title}
                          </h3>
                          <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                            {mod.does}
                          </p>
                          {hasWindow ? (
                            <p className="mt-1 text-xs text-muted-foreground">
                              {new Date(mod.releaseAt!).toLocaleDateString()} →{" "}
                              {new Date(mod.dueAt!).toLocaleDateString()}
                              {chapterLockedReason === "release_date"
                                ? " · not open yet"
                                : chapterLockedReason === "expired"
                                  ? " · window ended (finish incomplete lessons)"
                                  : " · open"}
                            </p>
                          ) : null}
                          {mod.lessons.length > 0 ? (
                            <ul className="mt-5 space-y-2 border-t border-border/60 pt-4">
                              {mod.lessons.map((lesson, li) => {
                                const mile = detail.cohortRun?.milestones?.find(
                                  (item) => item.lessonId === lesson.lessonId,
                                );
                                const canOpen =
                                  chapterReleased && (!mile || mile.available);
                                const typeLabel =
                                  lesson.type === "read" ? "text" : lesson.type;
                                const row = (
                                  <>
                                    <span className="text-xs text-muted-foreground">
                                      {li + 1}.
                                    </span>
                                    <span className="min-w-0 flex-1">
                                      <span className="block font-medium text-foreground">
                                        {lesson.title}
                                      </span>
                                      <span className="block text-xs capitalize text-muted-foreground">
                                        {typeLabel}
                                        {lesson.estimatedMinutes
                                          ? ` · ${lesson.estimatedMinutes} min`
                                          : ""}
                                        {!canOpen
                                          ? chapterLockedReason === "release_date"
                                            ? ` · locked until ${new Date(mod.releaseAt!).toLocaleString()}`
                                            : mile && !mile.available
                                              ? mile.lockedReason ===
                                                "release_date"
                                                ? ` · locked until ${new Date(mile.releaseAt).toLocaleString()}`
                                                : " · locked"
                                              : " · locked"
                                          : chapterExpired
                                            ? " · past due"
                                            : ""}
                                      </span>
                                    </span>
                                    <span className="shrink-0 text-xs font-medium text-ember">
                                      {lesson.lessonPercent ?? 0}%
                                    </span>
                                  </>
                                );
                                return (
                                  <li key={lesson.lessonId}>
                                    {canOpen ? (
                                      <Link
                                        to="/learning/$trackId/lesson/$lessonId"
                                        params={{
                                          trackId,
                                          lessonId: lesson.lessonId,
                                        }}
                                        className="flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors hover:bg-accent/60"
                                      >
                                        {row}
                                      </Link>
                                    ) : (
                                      <div className="flex items-center gap-3 rounded-xl px-3 py-2.5 opacity-70">
                                        {row}
                                      </div>
                                    )}
                                  </li>
                                );
                              })}
                            </ul>
                          ) : null}
                        </div>
                        );
                      })}
                    </div>
                  </section>
                ) : null}
              </>
            )}
          </>
        ) : null}
      </div>
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
