import { useCallback, useEffect, useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { ArrowLeft, CheckCircle2, LogIn } from "lucide-react";
import { toast } from "sonner";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  fetchLmsLesson,
  fetchMediaPlaybackUrl,
  patchLessonProgress,
  submitAssignment,
  submitLessonQuiz,
  type LessonDto,
} from "@/lib/lmsApi";

/** Re-sign this many seconds before the current URL dies. */
const RESIGN_LEAD_SECONDS = 60;

/**
 * Playback URLs are deliberately short-lived, so the player has to be able to
 * swap in a fresh signature without losing the viewer's position. Seeking issues
 * new range requests against the same signature, which is why an expired URL
 * shows up as a media error rather than a stall.
 */
function SignedMediaPlayer({
  user,
  lesson,
}: {
  user: User | null;
  lesson: LessonDto;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [url, setUrl] = useState(lesson.playbackUrl || lesson.contentUrl || "");
  const [expiresAt, setExpiresAt] = useState(lesson.playbackExpiresAt ?? null);
  const [refreshing, setRefreshing] = useState(false);
  const mediaId = lesson.mediaId ?? null;

  useEffect(() => {
    setUrl(lesson.playbackUrl || lesson.contentUrl || "");
    setExpiresAt(lesson.playbackExpiresAt ?? null);
  }, [lesson.playbackUrl, lesson.contentUrl, lesson.playbackExpiresAt]);

  const resign = useCallback(async () => {
    if (!user || !mediaId || refreshing) return;
    setRefreshing(true);
    try {
      const token = await user.getIdToken();
      const envelope = await fetchMediaPlaybackUrl(token, mediaId);
      if (!envelope.ok || !envelope.data) return;
      const video = videoRef.current;
      const resumeAt = video?.currentTime ?? 0;
      const wasPlaying = video ? !video.paused && !video.ended : false;
      setUrl(envelope.data.url);
      setExpiresAt(envelope.data.expiresAt);
      if (video) {
        const restore = () => {
          video.currentTime = resumeAt;
          if (wasPlaying) void video.play();
          video.removeEventListener("loadedmetadata", restore);
        };
        video.addEventListener("loadedmetadata", restore);
      }
    } finally {
      setRefreshing(false);
    }
  }, [user, mediaId, refreshing]);

  useEffect(() => {
    if (!expiresAt || !mediaId) return;
    const msLeft = (expiresAt - RESIGN_LEAD_SECONDS) * 1000 - Date.now();
    const timer = setTimeout(() => void resign(), Math.max(msLeft, 1000));
    return () => clearTimeout(timer);
  }, [expiresAt, mediaId, resign]);

  const playable =
    /\.(mp4|webm|ogg)(\?|$)/i.test(url) || url.includes("/lms/media/");

  return (
    <div className="space-y-3">
      {playable ? (
        <video
          ref={videoRef}
          className="w-full rounded-xl bg-black"
          controls
          src={url || undefined}
          onError={() => void resign()}
        />
      ) : null}
      <a
        href={url || "#"}
        target="_blank"
        rel="noreferrer"
        className="inline-flex text-sm font-semibold text-ember hover:underline"
      >
        Open lesson media
      </a>
      {expiresAt ? (
        <p className="text-xs text-muted-foreground">
          {refreshing
            ? "Refreshing secure link…"
            : "This link is time-limited and refreshes automatically while you watch."}
        </p>
      ) : null}
    </div>
  );
}

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
  const [quizScore, setQuizScore] = useState(80);
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null);
  const [assignmentText, setAssignmentText] = useState("");
  const [submittedOk, setSubmittedOk] = useState(false);

  const load = useCallback(
    async (u: User) => {
      setLoading(true);
      setError(null);
      try {
        const token = await u.getIdToken();
        const envelope = await fetchLmsLesson(token, lessonId);
        if (!envelope.ok || !envelope.data?.lesson) {
          setLesson(null);
          setError(envelope.error || "Could not load lesson");
          return;
        }
        setLesson(envelope.data.lesson);
        if (!envelope.data.lesson.milestone || envelope.data.lesson.milestone.available) {
          await patchLessonProgress(token, lessonId, {
            opened: true,
            lastPlatform: "web",
          });
        }
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

  function applyProgress(
    lessonPercent?: number,
    status?: string,
    nextTrackPercent?: number,
  ) {
    if (nextTrackPercent != null) setTrackPercent(nextTrackPercent);
    setLesson((prev) =>
      prev
        ? {
            ...prev,
            lessonPercent: lessonPercent ?? prev.lessonPercent,
            status: status ?? prev.status,
          }
        : prev,
    );
  }

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
      applyProgress(
        result.data.progress.lessonPercent,
        result.data.progress.status,
        result.data.progress.trackPercent,
      );
      toast.success("Progress saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save progress");
    } finally {
      setSaving(false);
    }
  }

  async function onSubmitQuiz(passed: boolean) {
    if (!user) return;
    setSaving(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const authored = lesson?.quiz?.mode === "single_answer";
      if (authored && !selectedOptionId) {
        setError("Choose an answer first.");
        setSaving(false);
        return;
      }
      const result = await submitLessonQuiz(token, lessonId, authored
        ? { selectedOptionId: selectedOptionId || undefined, lastPlatform: "web" }
        : {
            score: passed ? Math.max(quizScore, 80) : Math.min(quizScore, 79),
            passed,
            lastPlatform: "web",
          });
      if (!result.ok || !result.data) {
        setError(result.error || "Quiz submit failed");
        return;
      }
      applyProgress(
        result.data.lessonPercent,
        undefined,
        result.data.trackPercent ?? undefined,
      );
      toast.success(passed ? "Quiz recorded as pass" : "Quiz score saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Quiz submit failed");
    } finally {
      setSaving(false);
    }
  }

  async function onSubmitAssignment() {
    if (!user) return;
    const text = assignmentText.trim();
    if (text.length < 8) {
      setError("Write a bit more before submitting (at least a short paragraph).");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const result = await submitAssignment(token, { lessonId, text });
      if (!result.ok || !result.data) {
        setError(result.error || "Submission failed");
        return;
      }
      setSubmittedOk(true);
      toast.success("Assignment submitted for review");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Submission failed");
    } finally {
      setSaving(false);
    }
  }

  const done = (lesson?.lessonPercent ?? 0) >= 80;
  const showQuiz = Boolean(lesson?.hasQuiz || lesson?.quiz);
  const showAssignment = Boolean(lesson?.hasAssignment || lesson?.assignmentPrompt);

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
            {lesson.milestone && !lesson.milestone.available ? (
              <p className="mt-4 rounded-xl border border-border bg-secondary/50 px-4 py-3 text-sm">
                This milestone is locked
                {lesson.milestone.lockedReason === "release_date"
                  ? ` until ${new Date(lesson.milestone.releaseAt).toLocaleString()}.`
                  : " until you complete the previous one."}
              </p>
            ) : null}
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
                  className="prose max-w-none text-sm dark:prose-invert"
                  dangerouslySetInnerHTML={{ __html: lesson.bodyHtml }}
                />
              ) : null}
              {lesson.playbackUrl || lesson.contentUrl ? (
                <SignedMediaPlayer user={user} lesson={lesson} />
              ) : null}
            </div>

            {showQuiz && lesson.quiz?.mode === "single_answer" ? (
              <div className="mt-6 space-y-4 rounded-2xl border border-border/70 bg-card p-6">
                <h2 className="font-display text-lg font-semibold">Quiz</h2>
                <p className="text-sm text-muted-foreground">{lesson.quiz.prompt}</p>
                <div className="space-y-2">
                  {(lesson.quiz.options || []).map((option) => (
                    <label
                      key={option.id}
                      className="flex cursor-pointer items-center gap-3 rounded-xl border border-border px-3 py-2 text-sm"
                    >
                      <input
                        type="radio"
                        name="quiz-option"
                        checked={selectedOptionId === option.id}
                        onChange={() => setSelectedOptionId(option.id)}
                      />
                      {option.text}
                    </label>
                  ))}
                </div>
                <button
                  type="button"
                  disabled={saving || !selectedOptionId}
                  onClick={() => void onSubmitQuiz(true)}
                  className="rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground disabled:opacity-60"
                >
                  Submit answer
                </button>
              </div>
            ) : showQuiz ? (
              <div className="mt-6 space-y-4 rounded-2xl border border-border/70 bg-card p-6">
                <h2 className="font-display text-lg font-semibold">Quiz</h2>
                <p className="text-sm text-muted-foreground">
                  {lesson.quiz?.prompt ||
                    "Record how you did on this lesson’s quiz."}
                </p>
                <label className="block text-sm font-medium text-foreground">
                  Score: {quizScore}%
                  <input
                    type="range"
                    min={0}
                    max={100}
                    value={quizScore}
                    onChange={(e) => setQuizScore(Number(e.target.value))}
                    className="mt-2 w-full accent-[var(--maroon)]"
                  />
                </label>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => void onSubmitQuiz(true)}
                    className="rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground disabled:opacity-60"
                  >
                    Submit as pass (≥80)
                  </button>
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => void onSubmitQuiz(false)}
                    className="rounded-full border border-border px-5 py-2.5 text-sm font-medium hover:bg-accent disabled:opacity-60"
                  >
                    Save score only
                  </button>
                </div>
              </div>
            ) : null}

            {showAssignment && (
              <div className="mt-6 space-y-4 rounded-2xl border border-border/70 bg-card p-6">
                <h2 className="font-display text-lg font-semibold">Assignment</h2>
                <p className="text-sm text-muted-foreground">
                  {lesson.assignmentPrompt ||
                    "Write your response and submit for mentor review."}
                </p>
                {submittedOk ? (
                  <p className="inline-flex items-center gap-2 text-sm font-medium text-brand-soft">
                    <CheckCircle2 className="h-4 w-4" /> Submitted — check{" "}
                    <Link to="/learning/coursework" className="underline">
                      Coursework
                    </Link>{" "}
                    for status.
                  </p>
                ) : (
                  <>
                    <textarea
                      value={assignmentText}
                      onChange={(e) => setAssignmentText(e.target.value)}
                      rows={5}
                      placeholder="Your answer…"
                      className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-maroon/30"
                    />
                    <button
                      type="button"
                      disabled={saving}
                      onClick={() => void onSubmitAssignment()}
                      className="rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground disabled:opacity-60"
                    >
                      {saving ? "Submitting…" : "Submit assignment"}
                    </button>
                  </>
                )}
              </div>
            )}

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
                  {saving ? "Saving…" : "Mark content complete"}
                </button>
              )}
              <Link
                to="/learning/coursework"
                className="rounded-full border border-border px-5 py-2.5 text-sm font-medium hover:bg-accent"
              >
                My coursework
              </Link>
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
