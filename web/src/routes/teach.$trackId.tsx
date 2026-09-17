/**
 * Opened course workspace — students + management for one track.
 * Lander Update/Add still use side sheets on /teach.
 */
import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { User } from "firebase/auth";
import { ArrowLeft } from "lucide-react";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import { AddToCoursePanel } from "@/components/lms/AddToCoursePanel";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  createAssignment,
  fetchLmsModule,
  fetchLmsTrack,
  fetchLmsTracks,
  fetchTrackOverview,
  type MeDto,
  type TrackCardDto,
  type TrackOverviewDto,
} from "@/lib/lmsApi";

type SidePanel = "add" | "update" | null;

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
      blurb="Students and content for this track."
      wide
    >
      {({ user, me }) => (
        <TeachCourseBoard user={user} me={me} trackId={trackId} />
      )}
    </RoleShellPage>
  );
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
  const [sidePanel, setSidePanel] = useState<SidePanel>(null);
  const [modules, setModules] = useState<{ moduleId: string; title: string }[]>(
    [],
  );
  const [lessons, setLessons] = useState<{ lessonId: string; title: string }[]>(
    [],
  );
  const [assignTitle, setAssignTitle] = useState("");
  const [assignPrompt, setAssignPrompt] = useState("");
  const [assignModelAnswer, setAssignModelAnswer] = useState("");
  const [assignLessonId, setAssignLessonId] = useState("");
  const [assignUid, setAssignUid] = useState("");
  const [assignMsg, setAssignMsg] = useState<string | null>(null);

  const trackCard = tracks.find((t) => t.trackId === trackId);
  const title = overview?.title || trackCard?.courseTitle || trackId;
  const mentorLabel = trackCard?.tutorName || "";

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [ov, tr, detail] = await Promise.all([
        fetchTrackOverview(token, trackId),
        fetchLmsTracks(token),
        fetchLmsTrack(token, trackId),
      ]);
      if (!ov.ok || !ov.data) {
        setOverview(null);
        setError(ov.error || "Could not load course");
      } else {
        setOverview(ov.data);
      }
      setTracks(tr.data?.tracks || []);
      if (detail.ok && detail.data) {
        const mods = detail.data.modules || [];
        setModules(
          mods.map((m) => ({ moduleId: m.moduleId, title: m.title })),
        );
        const lessonRows: { lessonId: string; title: string }[] = [];
        await Promise.all(
          mods.map(async (m) => {
            const modEnv = await fetchLmsModule(token, m.moduleId);
            for (const l of modEnv.data?.lessons || []) {
              lessonRows.push({ lessonId: l.lessonId, title: l.title });
            }
          }),
        );
        setLessons(lessonRows);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, [user, trackId]);

  useEffect(() => {
    void load();
  }, [load]);

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
            onClick={() => setSidePanel("add")}
            className="rounded-full border border-border px-4 py-1.5 text-sm font-medium hover:bg-secondary"
          >
            Add material
          </button>
          <button
            type="button"
            onClick={() => setSidePanel("update")}
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

      <section id="students" className="space-y-3">
        <h3 className="font-display text-xl font-semibold">Students</h3>
        <p className="text-sm text-muted-foreground">
          Enrolled in this course only.
        </p>
        {!overview || overview.students.length === 0 ? (
          <p className="text-sm text-muted-foreground">No enrollments yet.</p>
        ) : (
          <ul className="divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {overview.students.map((s) => (
              <li
                key={s.uid}
                className="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-sm"
              >
                <span className="font-medium">{s.displayName}</span>
                <span className="font-semibold text-ember">{s.trackPercent}%</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="assignments" className="space-y-3">
        <h3 className="font-display text-xl font-semibold">Assignments</h3>
        {!overview || overview.assignments.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No course assignments yet — use Add.
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
            {lessons.length > 0 ? (
              <select
                value={assignLessonId}
                onChange={(e) => setAssignLessonId(e.target.value)}
                className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Whole course (no lesson)</option>
                {lessons.map((l) => (
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

      <Sheet
        open={sidePanel !== null}
        onOpenChange={(open) => {
          if (!open) setSidePanel(null);
        }}
      >
        <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-xl">
          <SheetHeader className="pr-8 text-left">
            <SheetTitle>
              {sidePanel === "add" ? "Add material" : "Edit course"}
            </SheetTitle>
            <SheetDescription>
              {sidePanel === "add"
                ? `Video, PDF, assignment, or schedule for ${title}`
                : `Title, modules, lessons, and media for ${title}`}
            </SheetDescription>
          </SheetHeader>
          <div className="mt-6 pb-8">
            {sidePanel === "add" ? (
              <AddToCoursePanel
                user={user}
                schoolId={schoolId}
                tracks={
                  tracks.length
                    ? tracks
                    : [
                        {
                          trackId,
                          courseId: trackId,
                          courseTitle: title,
                          tutorId: "",
                          tutorName: mentorLabel,
                          courseImageUrl: "",
                          tutorAvatarUrl: "",
                          does: "",
                          duration: "",
                          lessons: "0",
                          courseLink: "",
                          isLiked: false,
                          programSlug: "",
                          trackPercent: 0,
                          enrolled: false,
                          audience: [],
                          moduleCount: 0,
                        },
                      ]
                }
                initialTrackId={trackId}
                lockTrack
                onDone={() => {
                  void load();
                }}
              />
            ) : null}
            {sidePanel === "update" ? (
              <CatalogCmsPanel
                user={user}
                schoolId={schoolId}
                selectedTrackId={trackId}
                lessons={lessons}
                modules={modules}
              />
            ) : null}
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
