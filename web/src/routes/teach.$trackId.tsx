/**
 * Opened course workspace — syllabus, students + progress drill-down.
 */
import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { User } from "firebase/auth";
import { ArrowLeft } from "lucide-react";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  createAssignment,
  fetchLmsTracks,
  fetchTrackOverview,
  fetchTrackStudentDetail,
  type MeDto,
  type TrackCardDto,
  type TrackOverviewDto,
  type TrackStudentDetailDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/teach/$trackId")({
  head: ({ params }) => ({
    meta: [
      {
        title: `${params.trackId} — Teach | Nelsen Savannah`,
      },
    ],
  }),
  component: TeachCoursePage,
});

function TeachCoursePage() {
  const { trackId } = Route.useParams();
  return (
    <RoleShellPage
      shell="mentor"
      title="Course"
      blurb="Students, syllabus, and progress for this track."
      wide
    >
      {({ user, me }) => (
        <TeachCourseBoard user={user} me={me} trackId={trackId} />
      )}
    </RoleShellPage>
  );
}

function formatWindow(start: number | null, end: number | null) {
  if (!start || !end) return "Dates not set";
  return `${new Date(start).toLocaleDateString()} → ${new Date(end).toLocaleDateString()}`;
}

function TeachCourseBoard({
  user,
  me,
  trackId,
}: {
  user: User;
  me: MeDto;
  trackId: string;
}) {
  const schoolId = me.schoolId || me.activeSchoolId || "nelsen-digital";
  const [tracks, setTracks] = useState<TrackCardDto[]>([]);
  const [overview, setOverview] = useState<TrackOverviewDto | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [studentDetail, setStudentDetail] =
    useState<TrackStudentDetailDto | null>(null);
  const [studentBusy, setStudentBusy] = useState(false);
  const [assignTitle, setAssignTitle] = useState("");
  const [assignPrompt, setAssignPrompt] = useState("");
  const [assignModelAnswer, setAssignModelAnswer] = useState("");
  const [assignLessonId, setAssignLessonId] = useState("");
  const [assignUid, setAssignUid] = useState("");
  const [assignMsg, setAssignMsg] = useState<string | null>(null);

  const trackCard = tracks.find((t) => t.trackId === trackId);
  const title = overview?.title || trackCard?.courseTitle || trackId;
  const mentorLabel = trackCard?.tutorName || "";
  const lessonOptions =
    overview?.chapters.flatMap((c) =>
      c.lessons.map((l) => ({ lessonId: l.lessonId, title: l.title })),
    ) || [];

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [ov, tr] = await Promise.all([
        fetchTrackOverview(token, trackId),
        fetchLmsTracks(token),
      ]);
      if (!ov.ok || !ov.data) {
        setOverview(null);
        setError(ov.error || "Could not load course");
      } else {
        setOverview(ov.data);
      }
      setTracks(tr.data?.tracks || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, [user, trackId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function openStudent(uid: string) {
    setStudentBusy(true);
    setStudentDetail(null);
    try {
      const token = await user.getIdToken();
      const res = await fetchTrackStudentDetail(token, trackId, uid);
      if (res.ok && res.data) setStudentDetail(res.data);
      else setError(res.error || "Could not load student");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setStudentBusy(false);
    }
  }

  async function onAssign(e: React.FormEvent) {
    e.preventDefault();
    setAssignMsg(null);
    const token = await user.getIdToken();
    const result = await createAssignment(token, {
      title: assignTitle.trim(),
      trackId,
      ...(assignPrompt.trim() ? { prompt: assignPrompt.trim() } : {}),
      ...(assignModelAnswer.trim()
        ? { modelAnswer: assignModelAnswer.trim() }
        : {}),
      ...(assignLessonId.trim() ? { lessonId: assignLessonId.trim() } : {}),
      ...(assignUid.trim() ? { assigneeUid: assignUid.trim() } : {}),
    });
    if (!result.ok) {
      setAssignMsg(result.error || "Assign failed");
      return;
    }
    setAssignTitle("");
    setAssignPrompt("");
    setAssignModelAnswer("");
    setAssignLessonId("");
    setAssignUid("");
    setAssignMsg("Assigned to this course.");
    void load();
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center gap-3">
        <Link
          to="/teach"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          All courses
        </Link>
      </div>

      <header className="space-y-1">
        <h2 className="font-display text-2xl font-semibold">{title}</h2>
        {overview ? (
          <p className="text-sm text-muted-foreground">
            {overview.studentCount} students · avg {overview.avgProgress}%
            {mentorLabel ? ` · Mentor ${mentorLabel}` : ""}
          </p>
        ) : null}
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setEditOpen(true)}
            className="rounded-full border border-border px-4 py-1.5 text-sm font-medium hover:bg-secondary"
          >
            Edit course
          </button>
        </div>
      </header>

      {error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {busy && !overview ? (
        <p className="text-sm text-muted-foreground">Loading course…</p>
      ) : null}

      <section id="syllabus" className="space-y-3">
        <h3 className="font-display text-xl font-semibold">Syllabus</h3>
        <p className="text-sm text-muted-foreground">
          Chapters with shared start → end windows and nested lessons.
        </p>
        {!overview || overview.chapters.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No chapters yet — use Edit course.
          </p>
        ) : (
          <ul className="space-y-3">
            {overview.chapters.map((ch) => (
              <li
                key={ch.moduleId}
                className="rounded-2xl border border-border/70 bg-card px-5 py-4"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-display font-semibold">{ch.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {formatWindow(ch.releaseAt, ch.dueAt)}
                  </p>
                </div>
                {ch.does ? (
                  <p className="mt-1 text-sm text-muted-foreground">{ch.does}</p>
                ) : null}
                {ch.lessons.length === 0 ? (
                  <p className="mt-2 text-xs text-muted-foreground">No lessons</p>
                ) : (
                  <ul className="mt-2 space-y-1 text-sm">
                    {ch.lessons.map((l) => (
                      <li
                        key={l.lessonId}
                        className="flex justify-between gap-2 text-muted-foreground"
                      >
                        <span>{l.title}</span>
                        <span className="uppercase text-xs">
                          {l.type === "read" ? "text" : l.type}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="students" className="space-y-3">
        <h3 className="font-display text-xl font-semibold">Students</h3>
        <p className="text-sm text-muted-foreground">
          Click a student to see lesson progress (text / video / PDF).
        </p>
        {!overview || overview.students.length === 0 ? (
          <p className="text-sm text-muted-foreground">No enrollments yet.</p>
        ) : (
          <ul className="divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {overview.students.map((s) => (
              <li key={s.uid}>
                <button
                  type="button"
                  onClick={() => void openStudent(s.uid)}
                  className="flex w-full flex-wrap items-center justify-between gap-2 px-5 py-3 text-left text-sm hover:bg-secondary/40"
                >
                  <span className="font-medium">{s.displayName}</span>
                  <span className="font-semibold text-ember">
                    {s.trackPercent}%
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="assignments" className="space-y-3">
        <h3 className="font-display text-xl font-semibold">Assignments</h3>
        {!overview || overview.assignments.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No course assignments yet — use Assign work below.
          </p>
        ) : (
          <ul className="space-y-4">
            {overview.assignments.map((a) => (
              <li
                key={a.id}
                className="rounded-2xl border border-border/70 bg-card px-5 py-4"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-display font-semibold">{a.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {a.completedCount} done · {a.missingCount} missing
                  </p>
                </div>
                <ul className="mt-2 space-y-1 text-xs text-muted-foreground">
                  {a.students.map((row) => (
                    <li
                      key={`${a.id}-${row.uid}`}
                      className="flex justify-between gap-2"
                    >
                      <span>{row.displayName}</span>
                      <span
                        className={
                          row.status === "missing"
                            ? "text-destructive"
                            : "text-ember"
                        }
                      >
                        {row.status}
                        {row.score != null ? ` · ${row.score}` : ""}
                      </span>
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="assign" className="space-y-3">
        <h3 className="font-display text-xl font-semibold">Assign work</h3>
        <p className="text-sm text-muted-foreground">
          Course-wide by default for <span className="font-medium">{title}</span>
          . Optionally target one lesson or one mentee.
        </p>
        <form onSubmit={(e) => void onAssign(e)} className="space-y-3">
          <input
            required
            value={assignTitle}
            onChange={(e) => setAssignTitle(e.target.value)}
            placeholder="Title"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <textarea
            value={assignPrompt}
            onChange={(e) => setAssignPrompt(e.target.value)}
            placeholder="Prompt / instructions"
            rows={3}
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <textarea
            value={assignModelAnswer}
            onChange={(e) => setAssignModelAnswer(e.target.value)}
            placeholder="Model answer (mentors only)"
            rows={2}
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <div className="flex flex-wrap gap-2">
            {lessonOptions.length > 0 ? (
              <select
                value={assignLessonId}
                onChange={(e) => setAssignLessonId(e.target.value)}
                className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Whole course (no lesson)</option>
                {lessonOptions.map((l) => (
                  <option key={l.lessonId} value={l.lessonId}>
                    {l.title}
                  </option>
                ))}
              </select>
            ) : null}
            <input
              value={assignUid}
              onChange={(e) => setAssignUid(e.target.value)}
              placeholder="one mentee uid (optional)"
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <button
            type="submit"
            className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground"
          >
            Assign
          </button>
          {assignMsg ? (
            <p className="text-sm text-muted-foreground">{assignMsg}</p>
          ) : null}
        </form>
      </section>

      <Sheet open={editOpen} onOpenChange={setEditOpen}>
        <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-xl">
          <SheetHeader className="pr-8 text-left">
            <SheetTitle>Edit course</SheetTitle>
            <SheetDescription>
              Chapters, dates, and lessons for {title}
            </SheetDescription>
          </SheetHeader>
          <div className="mt-6 pb-8">
            <CatalogCmsPanel
              user={user}
              schoolId={schoolId}
              selectedTrackId={trackId}
              onChanged={() => void load()}
            />
          </div>
        </SheetContent>
      </Sheet>

      <Sheet
        open={studentDetail !== null || studentBusy}
        onOpenChange={(open) => {
          if (!open) {
            setStudentDetail(null);
            setStudentBusy(false);
          }
        }}
      >
        <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-xl">
          <SheetHeader className="pr-8 text-left">
            <SheetTitle>
              {studentDetail?.student.displayName || "Student"}
            </SheetTitle>
            <SheetDescription>
              {studentDetail
                ? `${studentDetail.student.trackPercent}% overall`
                : "Loading progress…"}
            </SheetDescription>
          </SheetHeader>
          <div className="mt-6 space-y-4 pb-8">
            {studentBusy && !studentDetail ? (
              <p className="text-sm text-muted-foreground">Loading…</p>
            ) : null}
            {studentDetail?.chapters.map((ch) => (
              <div
                key={ch.moduleId}
                className="rounded-xl border border-border/60 px-4 py-3"
              >
                <p className="font-medium">{ch.title}</p>
                <p className="text-xs text-muted-foreground">
                  {formatWindow(ch.releaseAt, ch.dueAt)}
                </p>
                <ul className="mt-2 space-y-2 text-sm">
                  {ch.lessons.map((l) => (
                    <li key={l.lessonId} className="space-y-0.5">
                      <div className="flex justify-between gap-2">
                        <span>
                          {l.title}{" "}
                          <span className="text-xs uppercase text-muted-foreground">
                            {l.type === "read" ? "text" : l.type}
                          </span>
                        </span>
                        <span className="font-semibold text-ember">
                          {l.lessonPercent}%
                        </span>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {l.type === "video" || l.watchPct > 0
                          ? `Watch ${l.watchPct}% (${l.watchSeconds}s) · `
                          : ""}
                        content {l.contentPct}%
                        {l.hasQuiz ? ` · quiz ${l.quizPct}%` : ""}
                        {l.submission
                          ? ` · submitted ${new Date(l.submission.submittedAt).toLocaleString()}`
                          : ""}
                      </p>
                      {l.submission?.body ? (
                        <p className="rounded-lg bg-secondary/40 px-2 py-1 text-xs whitespace-pre-wrap">
                          {l.submission.body}
                        </p>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
