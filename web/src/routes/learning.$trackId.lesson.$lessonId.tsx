import { useCallback, useEffect, useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { ArrowLeft, CheckCircle2, LogIn } from "lucide-react";
import { toast } from "sonner";

import { getFirebaseAuth } from "@/lib/firebase";
import { PdfReader } from "@/components/lms/PdfReader";
import { CodeWorkspace } from "@/components/lms/CodeWorkspace";
// import { CodeLab } from "@/components/lms/CodeLab";
import {
  fetchLmsLesson,
  fetchLmsTrack,
  fetchMediaPlaybackUrl,
  patchLessonProgress,
  submitAssignment,
  submitLessonQuiz,
  type LessonDto,
  type TrackCardDto,
} from "@/lib/lmsApi";

/** Re-sign this many seconds before the current URL dies. */
const RESIGN_LEAD_SECONDS = 60;

function formatMediaTime(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const total = Math.floor(seconds);
  const minutes = Math.floor(total / 60);
  const secs = String(total % 60).padStart(2, "0");
  return `${minutes}:${secs}`;
}

/**
 * Playback URLs are deliberately short-lived, so the player has to be able to
 * swap in a fresh signature without losing the viewer's position. Seeking issues
 * new range requests against the same signature, which is why an expired URL
 * shows up as a media error rather than a stall.
 */
function SignedMediaPlayer({
  user,
  lesson,
  onWatchProgress,
  onPdfProgress,
}: {
  user: User | null;
  lesson: LessonDto;
  onWatchProgress?: (info: { watchSeconds: number; watchPct: number }) => void;
  onPdfProgress?: (info: { page: number; contentPct: number }) => void;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [url, setUrl] = useState(lesson.playbackUrl || lesson.contentUrl || "");
  const [expiresAt, setExpiresAt] = useState(lesson.playbackExpiresAt ?? null);
  const [refreshing, setRefreshing] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  const [volume, setVolume] = useState(1);
  const [speed, setSpeed] = useState(1);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);
  const lastReport = useRef(0);
  const mediaId = lesson.mediaId ?? null;
  const isPdf = lesson.type === "pdf" || lesson.isPdf === true || /\.pdf(\?|$)/i.test(url);

  useEffect(() => {
    setUrl(lesson.playbackUrl || lesson.contentUrl || "");
    setExpiresAt(lesson.playbackExpiresAt ?? null);
    setPosition(0);
    setDuration(0);
    setPlaying(false);
  }, [lesson.lessonId, lesson.playbackUrl, lesson.contentUrl, lesson.playbackExpiresAt]);

  useEffect(() => {
    if (videoRef.current) videoRef.current.playbackRate = speed;
  }, [speed]);

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

  function reportWatch() {
    const video = videoRef.current;
    if (!video || !onWatchProgress) return;
    const duration = video.duration;
    if (!Number.isFinite(duration) || duration <= 0) return;
    const watched = Math.floor(video.currentTime);
    const pct = Math.min(100, Math.round((watched / duration) * 100));
    const now = Date.now();
    if (now - lastReport.current < 4000 && pct < 95) return;
    lastReport.current = now;
    onWatchProgress({ watchSeconds: watched, watchPct: pct });
  }

  function togglePlay() {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused || video.ended) void video.play();
    else video.pause();
  }

  function seekBy(seconds: number) {
    const video = videoRef.current;
    if (!video || !Number.isFinite(video.duration)) return;
    video.currentTime = Math.max(0, Math.min(video.duration, video.currentTime + seconds));
    setPosition(video.currentTime);
  }

  function setVideoVolume(next: number) {
    const video = videoRef.current;
    if (!video) return;
    const value = Math.max(0, Math.min(1, next));
    video.volume = value;
    video.muted = value === 0;
    setVolume(value);
    setMuted(video.muted);
  }

  function toggleMute() {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !video.muted;
    setMuted(video.muted);
  }

  async function toggleFullscreen() {
    const video = videoRef.current;
    if (!video) return;
    if (document.fullscreenElement) await document.exitFullscreen();
    else if (video.requestFullscreen) await video.requestFullscreen();
  }

  const playable = !isPdf && (/\.(mp4|webm|ogg)(\?|$)/i.test(url) || url.includes("/lms/media/"));

  return (
    <div className="space-y-3">
      {isPdf && url ? (
        <PdfReader
          url={url}
          title={lesson.title}
          initialPage={Math.max(1, lesson.lastPage || 1)}
          onPageProgress={(info) =>
            onPdfProgress?.({ page: info.page, contentPct: info.contentPct })
          }
        />
      ) : null}
      {playable ? (
        <div className="overflow-hidden rounded-xl border border-border/70 bg-black">
          <video
            ref={videoRef}
            className="aspect-video w-full bg-black"
            src={url || undefined}
            playsInline
            onError={() => void resign()}
            onLoadedMetadata={(e) => {
              setDuration(e.currentTarget.duration || 0);
              e.currentTarget.playbackRate = speed;
            }}
            onDurationChange={(e) => setDuration(e.currentTarget.duration || 0)}
            onPlay={() => setPlaying(true)}
            onPause={(e) => {
              setPlaying(false);
              reportWatch();
              setPosition(e.currentTarget.currentTime);
            }}
            onTimeUpdate={(e) => {
              setPosition(e.currentTarget.currentTime);
              reportWatch();
            }}
            onEnded={(e) => {
              setPlaying(false);
              setPosition(e.currentTarget.duration || e.currentTarget.currentTime);
              reportWatch();
            }}
            onVolumeChange={(e) => {
              setMuted(e.currentTarget.muted);
              setVolume(e.currentTarget.volume);
            }}
          />
          <div className="space-y-3 bg-card px-3 py-3 text-foreground">
            <input
              aria-label="Video progress"
              type="range"
              min={0}
              max={Math.max(duration, 0)}
              step={0.1}
              value={Math.min(position, duration || 0)}
              onChange={(e) => {
                const next = Number(e.target.value);
                if (videoRef.current) videoRef.current.currentTime = next;
                setPosition(next);
              }}
              className="w-full accent-ember"
            />
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <button type="button" onClick={() => seekBy(-10)} className="rounded-full border border-border px-3 py-1.5 font-semibold">
                −10s
              </button>
              <button type="button" onClick={togglePlay} className="rounded-full bg-ember-gradient px-4 py-1.5 font-semibold text-maroon-foreground">
                {playing ? "Pause" : "Play"}
              </button>
              <button type="button" onClick={() => seekBy(10)} className="rounded-full border border-border px-3 py-1.5 font-semibold">
                +10s
              </button>
              <span className="min-w-[72px] text-muted-foreground">
                {formatMediaTime(position)} / {formatMediaTime(duration)}
              </span>
              <button type="button" onClick={toggleMute} className="rounded-full border border-border px-3 py-1.5 font-semibold">
                {muted ? "Unmute" : "Mute"}
              </button>
              <label className="flex items-center gap-2 text-muted-foreground">
                Volume
                <input
                  aria-label="Volume"
                  type="range"
                  min={0}
                  max={1}
                  step={0.05}
                  value={muted ? 0 : volume}
                  onChange={(e) => setVideoVolume(Number(e.target.value))}
                  className="w-20 accent-ember"
                />
              </label>
              <select
                aria-label="Playback speed"
                value={speed}
                onChange={(e) => setSpeed(Number(e.target.value))}
                className="rounded-full border border-border bg-background px-3 py-1.5 font-semibold"
              >
                {[0.5, 0.75, 1, 1.25, 1.5, 2].map((value) => (
                  <option key={value} value={value}>{value}x</option>
                ))}
              </select>
              <button type="button" onClick={() => void toggleFullscreen()} className="rounded-full border border-border px-3 py-1.5 font-semibold">
                Fullscreen
              </button>
            </div>
          </div>
        </div>
      ) : null}
      <a
        href={url || "#"}
        target="_blank"
        rel="noreferrer"
        className="inline-flex text-sm font-semibold text-ember hover:underline"
      >
        {isPdf ? "Open PDF in a new tab" : "Open lesson media"}
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
  const [track, setTrack] = useState<TrackCardDto | null>(null);
  const [ideOpen, setIdeOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [trackPercent, setTrackPercent] = useState<number | null>(null);
  const [quizScore, setQuizScore] = useState(80);
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null);
  const [quizAnswers, setQuizAnswers] = useState<Record<string, string>>({});
  const [assignmentText, setAssignmentText] = useState("");
  const [assignmentAnswers, setAssignmentAnswers] = useState<Record<string, string>>({});
  const [submittedOk, setSubmittedOk] = useState(false);

  const load = useCallback(
    async (u: User) => {
      setLoading(true);
      setError(null);
      try {
        const token = await u.getIdToken();
        const [envelope, trackEnvelope] = await Promise.all([
          fetchLmsLesson(token, lessonId),
          fetchLmsTrack(token, trackId),
        ]);
        setTrack(trackEnvelope.data?.track || null);
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
    [lessonId, trackId],
  );

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      setUser(next);
      setAuthReady(true);
      if (next) void load(next);
      else setLesson(null);
    });
  }, [load]);

  function applyProgress(lessonPercent?: number, status?: string, nextTrackPercent?: number) {
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

  async function onPdfProgress(info: { page: number; contentPct: number }) {
    if (!user) return;
    try {
      const token = await user.getIdToken();
      const result = await patchLessonProgress(token, lessonId, {
        opened: true,
        contentPct: info.contentPct,
        watchSeconds: info.page,
        lastPlatform: "web",
      });
      if (result.ok && result.data) {
        applyProgress(
          result.data.progress.lessonPercent,
          result.data.progress.status,
          result.data.progress.trackPercent,
        );
      }
    } catch {
      /* ignore transient page sync errors */
    }
  }

  async function onWatchProgress(info: { watchSeconds: number; watchPct: number }) {
    if (!user) return;
    try {
      const token = await user.getIdToken();
      const result = await patchLessonProgress(token, lessonId, {
        opened: true,
        watchSeconds: info.watchSeconds,
        watchPct: info.watchPct,
        contentPct: info.watchPct,
        lastPlatform: "web",
      });
      if (result.ok && result.data) {
        applyProgress(
          result.data.progress.lessonPercent,
          result.data.progress.status,
          result.data.progress.trackPercent,
        );
      }
    } catch {
      /* ignore transient watch sync errors */
    }
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
      const mode = lesson?.quiz?.mode;
      const authored = mode === "single_answer" || mode === "multi_answer";
      if (mode === "single_answer" && !selectedOptionId) {
        setError("Choose an answer first.");
        setSaving(false);
        return;
      }
      if (mode === "multi_answer") {
        const qs = lesson?.quiz?.questions || [];
        const missing = qs.find((q) => !quizAnswers[q.id]);
        if (missing) {
          setError("Answer every question before submitting.");
          setSaving(false);
          return;
        }
      }
      const result = await submitLessonQuiz(
        token,
        lessonId,
        authored
          ? mode === "multi_answer"
            ? { answers: quizAnswers, lastPlatform: "web" }
            : {
                selectedOptionId: selectedOptionId || undefined,
                lastPlatform: "web",
              }
          : {
              score: passed ? Math.max(quizScore, 80) : Math.min(quizScore, 79),
              passed,
              lastPlatform: "web",
            },
      );
      if (!result.ok || !result.data) {
        setError(result.error || "Quiz submit failed");
        return;
      }
      applyProgress(result.data.lessonPercent, undefined, result.data.trackPercent ?? undefined);
      const pct = result.data.quizPct;
      toast.success(
        typeof pct === "number"
          ? `Quiz scored ${Math.round(pct)}%`
          : passed
            ? "Quiz recorded as pass"
            : "Quiz score saved",
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Quiz submit failed");
    } finally {
      setSaving(false);
    }
  }

  async function onSubmitAssignment() {
    if (!user || !lesson) return;
    const assignments = lesson.assignments || [];
    if (assignments.length > 0) {
      const incomplete = assignments.some(
        (assignment) => (assignmentAnswers[assignment.id] || "").trim().length < 2,
      );
      if (incomplete) {
        setError("Answer every assignment question before submitting.");
        return;
      }
    } else if (assignmentText.trim().length < 8) {
      setError("Write a bit more before submitting (at least a short paragraph).");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      if (assignments.length > 0) {
        const results = await Promise.all(
          assignments.map((assignment) =>
            submitAssignment(token, {
              lessonId,
              assignmentId: assignment.id,
              text: assignmentAnswers[assignment.id].trim(),
            }),
          ),
        );
        const failed = results.find((result) => !result.ok || !result.data);
        if (failed) {
          setError(failed.error || "One or more answers failed to submit");
          return;
        }
      } else {
        const result = await submitAssignment(token, {
          lessonId,
          text: assignmentText.trim(),
        });
        if (!result.ok || !result.data) {
          setError(result.error || "Submission failed");
          return;
        }
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
  const lessonType = lesson?.type === "read" ? "text" : lesson?.type || "text";
  const showQuiz = Boolean(lesson?.hasQuiz || lesson?.quiz);
  const showAssignment = Boolean(lesson?.hasAssignment);

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div
        className={`mx-auto px-4 sm:px-8 ${lessonType === "pdf" ? "max-w-3xl" : lessonType === "code" ? "max-w-4xl" : "max-w-2xl"}`}
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Link
            to="/learning/$trackId"
            params={{ trackId }}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to track
          </Link>
          {track?.ideEnabled && user ? (
            <button
              type="button"
              onClick={() => setIdeOpen(true)}
              className="rounded-full border border-ember/40 bg-ember/10 px-4 py-2 text-sm font-semibold text-ember hover:bg-ember/20"
            >
              Go to IDE
            </button>
          ) : null}
        </div>

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
            <p className="eyebrow mt-8 capitalize text-ember">{lessonType}</p>
            <h1 className="mt-3 text-3xl font-bold sm:text-4xl">{lesson.title}</h1>
            {(() => {
              const locked = Boolean(lesson.milestone) && !lesson.milestone!.available;
              if (locked) {
                const reason = lesson.milestone!.lockedReason;
                return (
                  <>
                    <p className="mt-4 rounded-xl border border-border bg-secondary/50 px-4 py-3 text-sm">
                      This chapter is locked
                      {reason === "release_date"
                        ? ` until ${new Date(lesson.milestone!.releaseAt).toLocaleString()}.`
                        : reason === "expired"
                          ? ` — the window ended${lesson.milestone!.dueAt ? ` ${new Date(lesson.milestone!.dueAt).toLocaleString()}` : ""}.`
                          : " until you complete the previous step."}
                    </p>
                    <div className="mt-8 flex flex-wrap items-center gap-3">
                      <Link
                        to="/learning/$trackId"
                        params={{ trackId }}
                        className="cursor-pointer rounded-full border border-border px-5 py-2.5 text-sm font-medium hover:bg-accent"
                      >
                        Back to chapters
                      </Link>
                    </div>
                  </>
                );
              }
              return (
                <>
                  {lesson.estimatedMinutes > 0 && (
                    <p className="mt-2 text-sm text-muted-foreground">
                      ~{lesson.estimatedMinutes} min
                    </p>
                  )}

                  <div className="mt-8 space-y-4 rounded-2xl border border-border/70 bg-card p-5 sm:p-6">
                    <p className="text-base leading-relaxed text-foreground">
                      {lesson.does ||
                        (lessonType === "text"
                          ? "Read the prompts below and submit your answers."
                          : lessonType === "code"
                            ? "Write and run the program in the lab below."
                            : "Work through this lesson, then mark it complete.")}
                    </p>
                    {lesson.bodyHtml ? (
                      <div
                        className="prose max-w-none text-sm dark:prose-invert"
                        dangerouslySetInnerHTML={{ __html: lesson.bodyHtml }}
                      />
                    ) : null}
{lessonType === "video" && (lesson.playbackUrl || lesson.contentUrl) ? (
                      <SignedMediaPlayer
                        user={user}
                        lesson={lesson}
                        onWatchProgress={(info) => void onWatchProgress(info)}
                      />
                    ) : null}
                  </div>
                  {lessonType === "pdf" && (lesson.playbackUrl || lesson.contentUrl) ? (
                    <div className="mt-6">
                      <SignedMediaPlayer
                        user={user}
                        lesson={lesson}
                        onPdfProgress={(info) => void onPdfProgress(info)}
                      />
                    </div>
                  ) : null}

                  {showQuiz &&
                  (lesson.quiz?.mode === "single_answer" ||
                    lesson.quiz?.mode === "multi_answer") ? (
                    <div className="mt-6 space-y-4 rounded-2xl border border-border/70 bg-card p-6">
                      <h2 className="font-display text-lg font-semibold">Quiz</h2>
                      {lesson.quiz.mode === "multi_answer" ? (
                        <div className="space-y-6">
                          {(lesson.quiz.questions || []).map((q, qi) => (
                            <div key={q.id} className="space-y-2">
                              <p className="text-sm font-medium text-foreground">
                                {qi + 1}. {q.prompt}
                              </p>
                              <div className="space-y-2">
                                {(q.options || []).map((option) => (
                                  <label
                                    key={option.id}
                                    className="flex cursor-pointer items-center gap-3 rounded-xl border border-border px-3 py-2 text-sm"
                                  >
                                    <input
                                      type="radio"
                                      name={`quiz-${q.id}`}
                                      checked={quizAnswers[q.id] === option.id}
                                      onChange={() =>
                                        setQuizAnswers((prev) => ({
                                          ...prev,
                                          [q.id]: option.id,
                                        }))
                                      }
                                    />
                                    <span className="mr-1 text-xs font-semibold uppercase text-muted-foreground">
                                      {option.id}
                                    </span>
                                    {option.text}
                                  </label>
                                ))}
                              </div>
                            </div>
                          ))}
                          <button
                            type="button"
                            disabled={
                              saving ||
                              (lesson.quiz.questions || []).some((q) => !quizAnswers[q.id])
                            }
                            onClick={() => void onSubmitQuiz(true)}
                            className="rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground disabled:opacity-60"
                          >
                            Submit answers
                          </button>
                        </div>
                      ) : (
                        <>
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
                                <span className="mr-1 text-xs font-semibold uppercase text-muted-foreground">
                                  {option.id}
                                </span>
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
                        </>
                      )}
                    </div>
                  ) : showQuiz ? (
                    <div className="mt-6 space-y-4 rounded-2xl border border-border/70 bg-card p-6">
                      <h2 className="font-display text-lg font-semibold">Quiz</h2>
                      <p className="text-sm text-muted-foreground">
                        {lesson.quiz?.prompt || "Record how you did on this lesson’s quiz."}
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
                      {lesson.assignmentPrompt ? (
                        <p className="text-sm text-muted-foreground">{lesson.assignmentPrompt}</p>
                      ) : null}
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
                          {(lesson.assignments || []).length > 0 ? (
                            <div className="space-y-5">
                              {(lesson.assignments || []).map((assignment, index) => (
                                <div key={assignment.id} className="space-y-2">
                                  <p className="text-sm font-medium text-foreground">
                                    {index + 1}. {assignment.prompt || assignment.title}
                                  </p>
                                  <textarea
                                    value={assignmentAnswers[assignment.id] || ""}
                                    onChange={(e) =>
                                      setAssignmentAnswers((current) => ({
                                        ...current,
                                        [assignment.id]: e.target.value,
                                      }))
                                    }
                                    rows={4}
                                    placeholder="Your answer…"
                                    className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-maroon/30"
                                  />
                                </div>
                              ))}
                            </div>
                          ) : (
                            <textarea
                              value={assignmentText}
                              onChange={(e) => setAssignmentText(e.target.value)}
                              rows={5}
                              placeholder="Your answer…"
                              className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-maroon/30"
                            />
                          )}
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
                        style={{ cursor: saving ? "not-allowed" : "pointer" }}
                        className="rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow disabled:opacity-60"
                      >
                        {saving ? "Saving…" : "Mark content complete"}
                      </button>
                    )}
                    <Link
                      to="/learning/coursework"
                      className="cursor-pointer rounded-full border border-border px-5 py-2.5 text-sm font-medium hover:bg-accent"
                    >
                      My coursework
                    </Link>
                    <Link
                      to="/learning/$trackId"
                      params={{ trackId }}
                      className="cursor-pointer rounded-full border border-border px-5 py-2.5 text-sm font-medium hover:bg-accent"
                    >
                      Back to modules
                    </Link>
                  </div>
                </>
              );
            })()}
          </>
        ) : null}
      </div>
      {ideOpen && user && track?.ideEnabled ? (
        <CodeWorkspace user={user} track={track} onClose={() => setIdeOpen(false)} />
      ) : null}
    </div>
  );
}
