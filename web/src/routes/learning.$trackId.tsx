import { useCallback, useEffect, useMemo, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Layers,
  LogIn,
} from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  enrollInTrack,
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
  component: TrackDetailPage,
});

type ModuleWithLessons = {
  moduleId: string;
  title: string;
  does: string;
  lessonCount: number;
  modulePercent?: number;
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
  const [enrolling, setEnrolling] = useState(false);

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

  async function onEnroll() {
    if (!user) return;
    setEnrolling(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const result = await enrollInTrack(token, trackId);
      if (!result.ok) {
        setError(result.error || "Enroll failed");
        return;
      }
      await load(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enroll failed");
    } finally {
      setEnrolling(false);
    }
  }

  const track = detail?.track;
  const enrolled = Boolean(detail?.enrollment || track?.enrolled);
  const percent =
    detail?.enrollment?.trackPercent ?? track?.trackPercent ?? 0;

  const continueLessonId = useMemo(() => {
    for (const mod of modules) {
      for (const lesson of mod.lessons) {
        if ((lesson.lessonPercent ?? 0) < 80) return lesson.lessonId;
      }
    }
    return modules[0]?.lessons[0]?.lessonId ?? null;
  }, [modules]);

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
            <p className="eyebrow mt-8 text-ember">
              {track.audience?.join(" · ") || "Track"}
            </p>
            <h1 className="mt-3 text-3xl font-bold sm:text-5xl">
              {track.courseTitle}
            </h1>
            <p className="mt-4 text-base leading-relaxed text-muted-foreground">
              {track.does}
            </p>

            <div className="mt-6 flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <Layers className="h-4 w-4 text-ember" />
                {modules.length || track.moduleCount} modules
              </span>
              <span className="inline-flex items-center gap-1.5">
                <BookOpen className="h-4 w-4 text-ember" />~{track.duration} hrs ·{" "}
                {track.lessons} lessons
              </span>
              {enrolled && (
                <span className="rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold text-brand-soft">
                  {percent}% complete
                </span>
              )}
            </div>

            {error && (
              <p className="mt-4 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
                {error}
              </p>
            )}

            {!enrolled ? (
              <button
                type="button"
                disabled={enrolling}
                onClick={() => void onEnroll()}
                className="mt-8 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow disabled:opacity-60"
              >
                {enrolling ? "Enrolling…" : "Enroll in this track"}
              </button>
            ) : continueLessonId ? (
              <Link
                to="/learning/$trackId/lesson/$lessonId"
                params={{ trackId, lessonId: continueLessonId }}
                className="mt-8 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow"
              >
                Continue learning <ArrowRight className="h-4 w-4" />
              </Link>
            ) : null}

            <section className="mt-12">
              <h2 className="text-xl font-bold">Modules & lessons</h2>
              <div className="mt-6 space-y-6">
                {modules.map((mod, i) => (
                  <div
                    key={mod.moduleId}
                    className="rounded-2xl border border-border/70 bg-card p-5 sm:p-6"
                  >
                    <p className="text-xs font-medium text-muted-foreground">
                      Module {i + 1}
                    </p>
                    <h3 className="mt-1 font-display text-lg font-semibold">
                      {mod.title}
                    </h3>
                    <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                      {mod.does}
                    </p>

                    {mod.lessons.length > 0 ? (
                      <ul className="mt-5 space-y-2 border-t border-border/60 pt-4">
                        {mod.lessons.map((lesson, li) => {
                          const canOpen = enrolled;
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
                                  {lesson.type}
                                  {lesson.estimatedMinutes
                                    ? ` · ${lesson.estimatedMinutes} min`
                                    : ""}
                                </span>
                              </span>
                              <span className="shrink-0 text-xs font-medium text-ember">
                                {enrolled
                                  ? `${lesson.lessonPercent ?? 0}%`
                                  : ""}
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
                    ) : (
                      <p className="mt-4 text-sm text-muted-foreground">
                        {mod.lessonCount} lessons
                        {!enrolled ? " — enroll to open them." : "."}
                      </p>
                    )}
                  </div>
                ))}
              </div>
              {modules.length === 0 && !loading && (
                <p className="mt-4 text-sm text-muted-foreground">
                  No modules yet for this track.
                </p>
              )}
            </section>
          </>
        ) : null}
      </div>
    </div>
  );
}
