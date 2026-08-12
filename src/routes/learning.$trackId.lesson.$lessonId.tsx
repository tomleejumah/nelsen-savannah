import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { ArrowLeft, CheckCircle2, LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  fetchLmsLesson,
  patchLessonProgress,
  type LessonDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/learning/$trackId/lesson/$lessonId")({
  head: ({ params }) => ({
    meta: [{ title: `Lesson — ${params.lessonId} | Nelsen Savannah` }],
  }),
  component: LessonPage,
});

function LessonPage() {
  const { trackId, lessonId } = Route.useParams();
  const [user, setUser] = useState<User | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [lesson, setLesson] = useState<LessonDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [trackPercent, setTrackPercent] = useState<number | null>(null);

  const load = useCallback(
    async (u: User) => {
      setLoading(true);
      setError(null);
      try {
        const token = await u.getIdToken();
        // Mark opened as soon as we land
        await patchLessonProgress(token, lessonId, {
          opened: true,
          lastPlatform: "web",
        });
        const envelope = await fetchLmsLesson(token, lessonId);
        if (!envelope.ok || !envelope.data?.lesson) {
          setLesson(null);
          setError(envelope.error || "Could not load lesson");
          return;
        }
        setLesson(envelope.data.lesson);
      } catch (err) {
        setLesson(null);
        setError(err instanceof Error ? err.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [lessonId],
  );

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      setUser(next);
      setAuthReady(true);
      if (next) void load(next);
      else setLesson(null);
    });
  }, [load]);

  async function markComplete() {
    if (!user) return;
    setSaving(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const result = await patchLessonProgress(token, lessonId, {
        opened: true,
        contentPct: 100,
        lastPlatform: "web",
      });
      if (!result.ok || !result.data) {
        setError(result.error || "Could not save progress");
        return;
      }
      setTrackPercent(result.data.progress.trackPercent);
      setLesson((prev) =>
        prev
          ? {
              ...prev,
              lessonPercent: result.data!.progress.lessonPercent,
              status: result.data!.progress.status,
            }
          : prev,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save progress");
    } finally {
      setSaving(false);
    }
  }

  const done = (lesson?.lessonPercent ?? 0) >= 80;

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-2xl px-5 sm:px-8">
        <Link
          to="/learning/$trackId"
          params={{ trackId }}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> Back to track
        </Link>

        {!authReady || (user && loading && !lesson) ? (
          <p className="mt-10 text-sm text-muted-foreground">Loading lesson…</p>
        ) : !user ? (
          <div className="mt-10 space-y-4">
            <h1 className="text-3xl font-bold">Sign in to continue</h1>
            <Link
              to="/login"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground"
            >
              <LogIn className="h-4 w-4" /> Sign in
            </Link>
          </div>
        ) : error && !lesson ? (
          <p className="mt-10 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </p>
        ) : lesson ? (
          <>
            <p className="eyebrow mt-8 capitalize text-ember">{lesson.type}</p>
            <h1 className="mt-3 text-3xl font-bold sm:text-4xl">{lesson.title}</h1>
            {lesson.estimatedMinutes > 0 && (
              <p className="mt-2 text-sm text-muted-foreground">
                ~{lesson.estimatedMinutes} min
              </p>
            )}

            <div className="mt-8 space-y-4 rounded-2xl border border-border/70 bg-card p-6 sm:p-8">
              <p className="text-base leading-relaxed text-foreground">
                {lesson.does || "Work through this lesson, then mark it complete."}
              </p>
              {lesson.bodyHtml ? (
                <div
                  className="prose prose-invert max-w-none text-sm"
                  dangerouslySetInnerHTML={{ __html: lesson.bodyHtml }}
                />
              ) : null}
              {lesson.playbackUrl || lesson.contentUrl ? (
                <a
                  href={lesson.playbackUrl || lesson.contentUrl || "#"}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex text-sm font-semibold text-ember hover:underline"
                >
                  Open lesson media
                </a>
              ) : null}
            </div>

            {error && (
              <p className="mt-4 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
                {error}
              </p>
            )}

            <div className="mt-8 flex flex-wrap items-center gap-3">
              {done ? (
                <span className="inline-flex items-center gap-2 rounded-full bg-brand/10 px-4 py-2 text-sm font-semibold text-brand-soft">
                  <CheckCircle2 className="h-4 w-4" /> Lesson complete
                  {trackPercent != null ? ` · Track ${trackPercent}%` : ""}
                </span>
              ) : (
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => void markComplete()}
                  className="rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow disabled:opacity-60"
                >
                  {saving ? "Saving…" : "Mark complete"}
                </button>
              )}
              <Link
                to="/learning/$trackId"
                params={{ trackId }}
                className="rounded-full border border-border px-5 py-2.5 text-sm font-medium hover:bg-accent"
              >
                Back to modules
              </Link>
            </div>
          </>
        ) : null}
      </div>
    </div>
  );
}
