import { useCallback, useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import { AddToCoursePanel } from "@/components/lms/AddToCoursePanel";
import {
  addCohortMember,
  authorLessonQuiz,
  createAssignment,
  createCohortRun,
  createMilestone,
  createSchoolCohort,
  fetchAssignedOutbox,
  fetchLmsTracks,
  fetchMenteeProgress,
  fetchSchoolCohorts,
  fetchSubmissionQueue,
  fetchTrackOverview,
  markSubmission,
  setTrackPricing,
  type AssignmentDto,
  type CohortDto,
  type MeDto,
  type MenteeProgressDto,
  type QueueItemDto,
  type TrackCardDto,
  type TrackOverviewDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/teach")({
  head: () => ({
    meta: [
      { title: "Teach — Nelsen Savannah LMS" },
      {
        name: "description",
        content: "Mentor workspace — students, marking, and assignments.",
      },
    ],
  }),
  component: TeachPage,
});

function TeachPage() {
  return (
    <RoleShellPage
      shell="mentor"
      title="Teach"
      blurb="Manage courses, milestones, marking, and mentee progress."
    >
      {({ user, me }) => <TeachBoard user={user} me={me} />}
    </RoleShellPage>
  );
}

function TeachBoard({ user, me }: { user: User; me: MeDto }) {
  const [queue, setQueue] = useState<QueueItemDto[]>([]);
  const [mentees, setMentees] = useState<MenteeProgressDto[]>([]);
  const [outbox, setOutbox] = useState<AssignmentDto[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [markingId, setMarkingId] = useState<string | null>(null);
  const [score, setScore] = useState(85);
  const [feedback, setFeedback] = useState("");
  const [assignTitle, setAssignTitle] = useState("");
  const [assignPrompt, setAssignPrompt] = useState("");
  const [assignModelAnswer, setAssignModelAnswer] = useState("");
  const [assignTrackId, setAssignTrackId] = useState("");
  const [assignLessonId, setAssignLessonId] = useState("");
  const [assignUid, setAssignUid] = useState("");
  const [assignMsg, setAssignMsg] = useState<string | null>(null);
  const [cohorts, setCohorts] = useState<CohortDto[]>([]);
  const [tracks, setTracks] = useState<TrackCardDto[]>([]);
  const [cohortName, setCohortName] = useState("");
  const [runCohortId, setRunCohortId] = useState("");
  const [memberCohortId, setMemberCohortId] = useState("");
  const [memberUid, setMemberUid] = useState("");
  const [runTrackId, setRunTrackId] = useState("");
  const [runId, setRunId] = useState("");
  const [mileLessonId, setMileLessonId] = useState("");
  const [mileRelease, setMileRelease] = useState("");
  const [mileDue, setMileDue] = useState("");
  const [mileRequiresPrevious, setMileRequiresPrevious] = useState(true);
  const [priceTrackId, setPriceTrackId] = useState("");
  const [priceAmount, setPriceAmount] = useState("0");
  const [quizLessonId, setQuizLessonId] = useState("");
  const [quizPrompt, setQuizPrompt] = useState("");
  const [quizA, setQuizA] = useState("");
  const [quizB, setQuizB] = useState("");
  const [quizCorrect, setQuizCorrect] = useState("a");
  const [materialsMsg, setMaterialsMsg] = useState<string | null>(null);
  const [cmsTrackId, setCmsTrackId] = useState("");
  const [overview, setOverview] = useState<TrackOverviewDto | null>(null);
  const [overviewBusy, setOverviewBusy] = useState(false);
  const [overviewErr, setOverviewErr] = useState<string | null>(null);

  const schoolId = me.schoolId || me.activeSchoolId || "nelsen-digital";

  function statsForTrack(trackId: string) {
    const rows = mentees.filter((m) => m.trackId === trackId);
    const studentCount = rows.length;
    const avgProgress =
      studentCount === 0
        ? 0
        : Math.round(
            rows.reduce((a, m) => a + Number(m.trackPercent || 0), 0) /
              studentCount,
          );
    return { studentCount, avgProgress };
  }

  async function openTrack(trackId: string) {
    setCmsTrackId(trackId);
    setPriceTrackId(trackId);
    setRunTrackId(trackId);
    setAssignTrackId(trackId);
    setOverviewBusy(true);
    setOverviewErr(null);
    try {
      const token = await user.getIdToken();
      const envelope = await fetchTrackOverview(token, trackId);
      if (!envelope.ok || !envelope.data) {
        setOverview(null);
        setOverviewErr(envelope.error || "Could not load course overview");
        return;
      }
      setOverview(envelope.data);
    } catch (err) {
      setOverview(null);
      setOverviewErr(err instanceof Error ? err.message : "Network error");
    } finally {
      setOverviewBusy(false);
    }
  }

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [q, m, a, c, t] = await Promise.all([
        fetchSubmissionQueue(token),
        fetchMenteeProgress(token, me.uid),
        fetchAssignedOutbox(token),
        fetchSchoolCohorts(token, schoolId),
        fetchLmsTracks(token),
      ]);
      if (!q.ok) setError(q.error || "Queue failed");
      setQueue(q.data?.queue || []);
      setMentees(m.data?.mentees || []);
      setOutbox(a.data?.assignments || []);
      setCohorts(c.data?.cohorts || []);
      setTracks(t.data?.tracks || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, [user, me.uid, schoolId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onMark(id: string, passed: boolean) {
    setMarkingId(id);
    try {
      const token = await user.getIdToken();
      const result = await markSubmission(token, id, {
        score: passed ? Math.max(score, 80) : Math.min(score, 79),
        passed,
        feedback,
      });
      if (!result.ok) {
        setError(result.error || "Mark failed");
        return;
      }
      setFeedback("");
      await load();
    } finally {
      setMarkingId(null);
    }
  }

  async function onAssign(e: React.FormEvent) {
    e.preventDefault();
    setAssignMsg(null);
    const token = await user.getIdToken();
    const result = await createAssignment(token, {
      title: assignTitle.trim(),
      ...(assignPrompt.trim() ? { prompt: assignPrompt.trim() } : {}),
      ...(assignModelAnswer.trim() ? { modelAnswer: assignModelAnswer.trim() } : {}),
      ...(assignTrackId.trim() ? { trackId: assignTrackId.trim() } : {}),
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
    setAssignMsg("Assigned.");
    await load();
  }

  if (busy) {
    return <p className="text-sm text-muted-foreground">Loading mentor board…</p>;
  }

  return (
    <div className="space-y-10">
      {error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      <section id="courses">
        <h2 className="font-display text-xl font-semibold">Your courses</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Open a course for roster &amp; assignments, or Add / Update content.
        </p>
        {tracks.length === 0 ? (
          <p className="mt-4 text-sm text-muted-foreground">
            No tracks yet — create one in Catalog CMS.
          </p>
        ) : (
          <ul className="mt-4 space-y-3">
            {tracks.map((t) => {
              const stats = statsForTrack(t.trackId);
              return (
              <li
                key={t.trackId}
                className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border/70 bg-card px-5 py-4"
              >
                <div>
                  <p className="font-display font-semibold">{t.courseTitle}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {stats.studentCount} students · avg {stats.avgProgress}%
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => void openTrack(t.trackId)}
                    className="rounded-full bg-ember-gradient px-3 py-1.5 text-xs font-semibold text-maroon-foreground"
                  >
                    Open
                  </button>
                  <a
                    href="#add"
                    onClick={() => {
                      setCmsTrackId(t.trackId);
                      setPriceTrackId(t.trackId);
                      setRunTrackId(t.trackId);
                    }}
                    className="rounded-full border border-border px-3 py-1.5 text-xs font-medium hover:bg-secondary"
                  >
                    Add
                  </a>
                  <a
                    href="#cms"
                    onClick={() => {
                      setCmsTrackId(t.trackId);
                      setPriceTrackId(t.trackId);
                      setRunTrackId(t.trackId);
                    }}
                    className="rounded-full border border-border px-3 py-1.5 text-xs font-medium hover:bg-secondary"
                  >
                    Update
                  </a>
                </div>
              </li>
              );
            })}
          </ul>
        )}
      </section>

      <section id="cockpit" className="space-y-4 rounded-2xl border border-border/70 bg-card/40 p-5">
        <h2 className="font-display text-xl font-semibold">Course cockpit</h2>
        {!cmsTrackId ? (
          <p className="text-sm text-muted-foreground">
            Open a course above to see students and assignment completion.
          </p>
        ) : overviewBusy ? (
          <p className="text-sm text-muted-foreground">Loading overview…</p>
        ) : overviewErr ? (
          <p className="text-sm text-destructive">{overviewErr}</p>
        ) : overview ? (
          <>
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <div>
                <p className="font-display text-lg font-semibold">{overview.title}</p>
                <p className="text-sm text-muted-foreground">
                  {overview.studentCount} students · avg {overview.avgProgress}%
                </p>
              </div>
              <a
                href="#add"
                className="text-sm font-medium text-ember hover:underline"
              >
                Add content →
              </a>
            </div>

            <div>
              <h3 className="font-medium">Students</h3>
              {overview.students.length === 0 ? (
                <p className="mt-2 text-sm text-muted-foreground">
                  No enrollments yet.
                </p>
              ) : (
                <ul className="mt-2 divide-y divide-border/60 rounded-xl border border-border/60">
                  {overview.students.map((s) => (
                    <li
                      key={s.uid}
                      className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm"
                    >
                      <span className="font-medium">{s.displayName}</span>
                      <span className="font-semibold text-ember">
                        {s.trackPercent}%
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div>
              <h3 className="font-medium">Assignments</h3>
              {overview.assignments.length === 0 ? (
                <p className="mt-2 text-sm text-muted-foreground">
                  No course assignments yet — use Add to course.
                </p>
              ) : (
                <ul className="mt-2 space-y-4">
                  {overview.assignments.map((a) => (
                    <li
                      key={a.id}
                      className="rounded-xl border border-border/60 px-4 py-3"
                    >
                      <div className="flex flex-wrap items-baseline justify-between gap-2">
                        <p className="font-medium">{a.title}</p>
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
            </div>
          </>
        ) : (
          <p className="text-sm text-muted-foreground">
            Select Open on a course to load the cockpit.
          </p>
        )}
      </section>

      <AddToCoursePanel
        user={user}
        schoolId={schoolId}
        tracks={tracks}
        runId={runId || undefined}
        initialTrackId={cmsTrackId || undefined}
        onDone={() => {
          void load();
          if (cmsTrackId) void openTrack(cmsTrackId);
        }}
      />

      <section id="cms" className="rounded-2xl border border-border/70 bg-card/40 p-5">
        <CatalogCmsPanel user={user} schoolId={schoolId} selectedTrackId={cmsTrackId} />
      </section>

      <section id="queue">
        <h2 className="font-display text-xl font-semibold">Marking queue</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Pending assignment submissions. Marking updates student progress %.
        </p>
        {queue.length === 0 ? (
          <p className="mt-4 text-sm text-muted-foreground">Queue is empty.</p>
        ) : (
          <ul className="mt-4 space-y-3">
            {queue.map((item) => (
              <li
                key={item.id}
                className="rounded-2xl border border-border/70 bg-card px-5 py-4"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-display font-semibold">
                    {item.lessonTitle || item.lessonId}
                  </p>
                  <span className="text-xs text-muted-foreground">
                    {item.menteeName || item.menteeId}
                  </span>
                </div>
                <p className="mt-2 line-clamp-4 text-sm text-muted-foreground">
                  {item.text || "(no text)"}
                </p>
                <div className="mt-3 flex flex-wrap items-end gap-3">
                  <label className="text-xs text-muted-foreground">
                    Score
                    <input
                      type="number"
                      min={0}
                      max={100}
                      value={score}
                      onChange={(e) => setScore(Number(e.target.value) || 0)}
                      className="ml-2 w-20 rounded-lg border border-border bg-background px-2 py-1 text-sm"
                    />
                  </label>
                  <input
                    value={feedback}
                    onChange={(e) => setFeedback(e.target.value)}
                    placeholder="Feedback"
                    className="min-w-[12rem] flex-1 rounded-lg border border-border bg-background px-3 py-1.5 text-sm"
                  />
                  <button
                    type="button"
                    disabled={markingId === item.id}
                    onClick={() => void onMark(item.id, true)}
                    className="rounded-full bg-ember-gradient px-4 py-1.5 text-sm font-semibold text-maroon-foreground"
                  >
                    Pass
                  </button>
                  <button
                    type="button"
                    disabled={markingId === item.id}
                    onClick={() => void onMark(item.id, false)}
                    className="rounded-full border border-border px-4 py-1.5 text-sm font-medium"
                  >
                    Fail
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="students">
        <h2 className="font-display text-xl font-semibold">Students</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Recent enrollments and track progress.
        </p>
        {mentees.length === 0 ? (
          <p className="mt-4 text-sm text-muted-foreground">No students yet.</p>
        ) : (
          <ul className="mt-4 divide-y divide-border/60 rounded-2xl border border-border/70 bg-card">
            {mentees.map((m) => (
              <li
                key={`${m.uid}-${m.trackId}`}
                className="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-sm"
              >
                <span className="font-medium">{m.displayName || m.uid}</span>
                <span className="text-muted-foreground">{m.trackId}</span>
                <span className="font-semibold text-ember">{m.trackPercent}%</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="assign">
        <h2 className="font-display text-xl font-semibold">Assign work</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Prefer <a href="#add" className="text-ember underline">Add to course → Assignment</a>{" "}
          for course-wide work. This form is for edge cases (single mentee).
        </p>
        <form onSubmit={(e) => void onAssign(e)} className="mt-4 space-y-3">
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
            placeholder="Prompt / instructions for the student"
            rows={3}
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <textarea
            value={assignModelAnswer}
            onChange={(e) => setAssignModelAnswer(e.target.value)}
            placeholder="Model / expected answer (mentors only — not shown to students)"
            rows={3}
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <div className="flex flex-wrap gap-3">
            <select
              required
              value={assignTrackId}
              onChange={(e) => setAssignTrackId(e.target.value)}
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Select course</option>
              {tracks.map((t) => (
                <option key={t.trackId} value={t.trackId}>
                  {t.courseTitle}
                </option>
              ))}
            </select>
            <input
              value={assignLessonId}
              onChange={(e) => setAssignLessonId(e.target.value)}
              placeholder="lessonId (optional)"
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              value={assignUid}
              onChange={(e) => setAssignUid(e.target.value)}
              placeholder="one mentee uid (optional — leave blank for whole course)"
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
        {outbox.length > 0 ? (
          <ul className="mt-6 space-y-2 text-sm">
            {outbox.slice(0, 8).map((a) => (
              <li key={a.id} className="text-muted-foreground">
                <span className="font-medium text-foreground">{a.title}</span>
                {a.trackId ? ` · ${a.trackId}` : ""}
                {a.lessonId ? ` · lesson ${a.lessonId}` : ""}
                {a.assigneeUid ? ` · ${a.assigneeUid}` : " · whole course"}
                {a.modelAnswer ? " · has model answer" : ""}
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <section id="materials" className="space-y-4 rounded-2xl border border-border/70 bg-card/50 p-5">
        <h2 className="font-display text-xl font-semibold">Cohort walkthrough</h2>
        <p className="text-sm text-muted-foreground">
          Reuse a track, schedule milestones, set a price, and publish a quiz version
          for this intake. Catalog CMS still creates the actual lessons.
        </p>
        {cohorts.length > 0 ? (
          <ul className="text-sm text-muted-foreground">
            {cohorts.map((c) => (
              <li key={c.cohortId}>
                <span className="font-medium text-foreground">{c.name}</span>
                {` · ${c.cohortId} · ${c.memberCount || 0} members`}
              </li>
            ))}
          </ul>
        ) : null}

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void (async () => {
              setMaterialsMsg(null);
              const token = await user.getIdToken();
              const result = await createSchoolCohort(token, schoolId, {
                name: cohortName.trim(),
              });
              setMaterialsMsg(result.ok ? "Cohort created." : result.error || "Failed");
              if (result.ok && result.data?.cohort.cohortId) {
                setRunCohortId(result.data.cohort.cohortId);
                setCohortName("");
                await load();
              }
            })();
          }}
          className="space-y-2"
        >
          <h3 className="font-medium">New cohort</h3>
          <div className="flex flex-wrap gap-2">
            <input
              required
              value={cohortName}
              onChange={(e) => setCohortName(e.target.value)}
              placeholder="Intake name (e.g. May 2026)"
              className="min-w-[12rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <button
              type="submit"
              className="rounded-full bg-ember-gradient px-4 py-2 text-sm font-semibold text-maroon-foreground"
            >
              Create cohort
            </button>
          </div>
        </form>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void (async () => {
              setMaterialsMsg(null);
              const token = await user.getIdToken();
              const result = await addCohortMember(
                token,
                schoolId,
                memberCohortId,
                memberUid,
              );
              setMaterialsMsg(
                result.ok ? "Learner added to cohort." : result.error || "Failed",
              );
            })();
          }}
          className="space-y-2"
        >
          <h3 className="font-medium">Add learner to cohort</h3>
          <div className="flex flex-wrap gap-2">
            <select
              required
              value={memberCohortId}
              onChange={(e) => setMemberCohortId(e.target.value)}
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Select cohort</option>
              {cohorts.map((c) => (
                <option key={c.cohortId} value={c.cohortId}>
                  {c.name}
                </option>
              ))}
            </select>
            <select
              required
              value={memberUid}
              onChange={(e) => setMemberUid(e.target.value)}
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Select learner</option>
              {[...new Map(mentees.map((m) => [m.uid, m])).values()].map((m) => (
                <option key={m.uid} value={m.uid}>
                  {m.displayName || m.uid}
                </option>
              ))}
            </select>
            <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
              Add learner
            </button>
          </div>
        </form>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void (async () => {
              setMaterialsMsg(null);
              const token = await user.getIdToken();
              const result = await createCohortRun(token, schoolId, runCohortId, {
                trackId: runTrackId,
              });
              setMaterialsMsg(result.ok ? "Track attached to cohort." : result.error || "Failed");
              if (result.ok && result.data?.run.runId) {
                setRunId(result.data.run.runId);
                await load();
              }
            })();
          }}
          className="space-y-2"
        >
          <h3 className="font-medium">Attach track to cohort</h3>
          <div className="flex flex-wrap gap-2">
            <select
              required
              value={runCohortId}
              onChange={(e) => setRunCohortId(e.target.value)}
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Select cohort</option>
              {cohorts.map((c) => (
                <option key={c.cohortId} value={c.cohortId}>
                  {c.name}
                </option>
              ))}
            </select>
            <select
              required
              value={runTrackId}
              onChange={(e) => setRunTrackId(e.target.value)}
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Select track</option>
              {tracks.map((t) => (
                <option key={t.trackId} value={t.trackId}>
                  {t.courseTitle}
                </option>
              ))}
            </select>
            <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
              Attach
            </button>
          </div>
        </form>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void (async () => {
              setMaterialsMsg(null);
              const token = await user.getIdToken();
              const result = await createMilestone(token, schoolId, runId, {
                lessonId: mileLessonId.trim(),
                releaseAt: new Date(mileRelease).getTime(),
                dueAt: mileDue ? new Date(mileDue).getTime() : undefined,
                requiresPreviousCompletion: mileRequiresPrevious,
              });
              setMaterialsMsg(result.ok ? "Milestone scheduled." : result.error || "Failed");
            })();
          }}
          className="space-y-2"
        >
          <h3 className="font-medium">Schedule milestone</h3>
          <div className="flex flex-wrap gap-2">
            <input
              value={runId}
              onChange={(e) => setRunId(e.target.value)}
              placeholder="runId (from attach)"
              className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              required
              value={mileLessonId}
              onChange={(e) => setMileLessonId(e.target.value)}
              placeholder="lessonId"
              className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              required
              type="datetime-local"
              value={mileRelease}
              onChange={(e) => setMileRelease(e.target.value)}
              className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              type="datetime-local"
              value={mileDue}
              onChange={(e) => setMileDue(e.target.value)}
              aria-label="Milestone due date"
              className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <label className="inline-flex items-center gap-2 text-sm text-muted-foreground">
              <input
                type="checkbox"
                checked={mileRequiresPrevious}
                onChange={(e) => setMileRequiresPrevious(e.target.checked)}
              />
              Require previous
            </label>
            <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
              Schedule
            </button>
          </div>
        </form>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void (async () => {
              setMaterialsMsg(null);
              const token = await user.getIdToken();
              const cents = Math.round(Number(priceAmount || 0) * 100);
              const result = await setTrackPricing(token, schoolId, priceTrackId, {
                amountMinor: cents,
                currency: "KES",
              });
              setMaterialsMsg(
                result.ok
                  ? cents
                    ? "Price set — enroll will paywall."
                    : "Track is free."
                  : result.error || "Failed",
              );
            })();
          }}
          className="space-y-2"
        >
          <h3 className="font-medium">Track price</h3>
          <div className="flex flex-wrap gap-2">
            <select
              required
              value={priceTrackId}
              onChange={(e) => setPriceTrackId(e.target.value)}
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Select track</option>
              {tracks.map((t) => (
                <option key={t.trackId} value={t.trackId}>
                  {t.courseTitle}
                </option>
              ))}
            </select>
            <input
              value={priceAmount}
              onChange={(e) => setPriceAmount(e.target.value)}
              placeholder="KES amount (0 = free)"
              className="w-40 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
              Save price
            </button>
          </div>
        </form>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void (async () => {
              setMaterialsMsg(null);
              const token = await user.getIdToken();
              const result = await authorLessonQuiz(token, schoolId, quizLessonId.trim(), {
                prompt: quizPrompt.trim(),
                options: [
                  { id: "a", text: quizA.trim() },
                  { id: "b", text: quizB.trim() },
                ],
                correctOptionId: quizCorrect,
                runId: runId || undefined,
              });
              setMaterialsMsg(
                result.ok
                  ? `Quiz v${result.data?.quiz.version} published for future attempts.`
                  : result.error || "Failed",
              );
            })();
          }}
          className="space-y-2"
        >
          <h3 className="font-medium">Quiz for this cohort lesson</h3>
          <p className="text-xs text-muted-foreground">
            Uses the current runId above. New versions affect future attempts only.
          </p>
          <input
            required
            value={quizLessonId}
            onChange={(e) => setQuizLessonId(e.target.value)}
            placeholder="lessonId"
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <textarea
            required
            value={quizPrompt}
            onChange={(e) => setQuizPrompt(e.target.value)}
            placeholder="Question"
            rows={2}
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <div className="flex flex-wrap gap-2">
            <input
              required
              value={quizA}
              onChange={(e) => setQuizA(e.target.value)}
              placeholder="Option A"
              className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              required
              value={quizB}
              onChange={(e) => setQuizB(e.target.value)}
              placeholder="Option B"
              className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <select
              value={quizCorrect}
              onChange={(e) => setQuizCorrect(e.target.value)}
              className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="a">A is correct</option>
              <option value="b">B is correct</option>
            </select>
            <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
              Publish quiz version
            </button>
          </div>
        </form>

        {materialsMsg ? (
          <p className="rounded-xl border border-border/60 bg-background/80 px-4 py-3 text-sm text-muted-foreground">
            {materialsMsg}
          </p>
        ) : null}
      </section>

      <section id="payouts" className="rounded-2xl border border-border/70 bg-card/40 p-5">
        <h2 className="font-display text-xl font-semibold">Tutor payouts</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Paid from your school’s ledger (prototype). Full dashboard lands with payments.
        </p>
        <p className="mt-3 text-sm text-muted-foreground">No payout rows yet.</p>
      </section>
    </div>
  );
}
