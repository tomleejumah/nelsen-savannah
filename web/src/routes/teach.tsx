import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { User } from "firebase/auth";

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
  fetchAssignedOutbox,
  fetchLmsTracks,
  fetchMenteeProgress,
  fetchSubmissionQueue,
  markSubmission,
  type AssignmentDto,
  type MeDto,
  type MenteeProgressDto,
  type QueueItemDto,
  type TrackCardDto,
} from "@/lib/lmsApi";

type SidePanel = "update" | null;

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
      blurb="Open a course for students and content. Edit course opens a side panel."
      wide
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
  const [tracks, setTracks] = useState<TrackCardDto[]>([]);
  const [cmsTrackId, setCmsTrackId] = useState("");
  const [sidePanel, setSidePanel] = useState<SidePanel>(null);

  const schoolId = me.schoolId || me.activeSchoolId || "nelsen-digital";

  function openSide(trackId: string, panel: SidePanel) {
    setCmsTrackId(trackId);
    setSidePanel(panel);
  }

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

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [q, m, a, t] = await Promise.all([
        fetchSubmissionQueue(token),
        fetchMenteeProgress(token, me.uid),
        fetchAssignedOutbox(token),
        fetchLmsTracks(token),
      ]);
      if (!q.ok) setError(q.error || "Queue failed");
      setQueue(q.data?.queue || []);
      setMentees(m.data?.mentees || []);
      setOutbox(a.data?.assignments || []);
      setTracks(t.data?.tracks || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, [user, me.uid]);

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
          Open a course for students and syllabus. Edit course opens a side panel
          to add chapters and lessons.
        </p>
        {tracks.length === 0 ? (
          <p className="mt-4 text-sm text-muted-foreground">
            No tracks yet — admins publish new courses.
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
                    <Link
                      to="/teach/$trackId"
                      params={{ trackId: t.trackId }}
                      className="rounded-full bg-ember-gradient px-3 py-1.5 text-xs font-semibold text-maroon-foreground"
                    >
                      Open
                    </Link>
                    <button
                      type="button"
                      onClick={() => openSide(t.trackId, "update")}
                      className="rounded-full border border-border px-3 py-1.5 text-xs font-medium hover:bg-secondary"
                    >
                      Edit course
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <Sheet
        open={sidePanel !== null}
        onOpenChange={(open) => {
          if (!open) setSidePanel(null);
        }}
      >
        <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-xl">
          <SheetHeader className="pr-8 text-left">
            <SheetTitle>Edit course</SheetTitle>
            <SheetDescription>
              Chapters, dates, and lessons for{" "}
              {tracks.find((t) => t.trackId === cmsTrackId)?.courseTitle ||
                cmsTrackId ||
                "this course"}
            </SheetDescription>
          </SheetHeader>
          <div className="mt-6 pb-8">
            {sidePanel === "update" && cmsTrackId ? (
              <CatalogCmsPanel
                user={user}
                schoolId={schoolId}
                selectedTrackId={cmsTrackId}
                onChanged={() => void load()}
              />
            ) : null}
          </div>
        </SheetContent>
      </Sheet>

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
                      onChange={(e) => setScore(Number(e.target.value))}
                      className="ml-2 w-16 rounded-lg border border-border bg-background px-2 py-1 text-sm"
                    />
                  </label>
                  <input
                    value={feedback}
                    onChange={(e) => setFeedback(e.target.value)}
                    placeholder="Feedback"
                    className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
                  />
                  <button
                    type="button"
                    disabled={markingId === item.id}
                    onClick={() => void onMark(item.id, true)}
                    className="rounded-full bg-ember-gradient px-3 py-1.5 text-xs font-semibold text-maroon-foreground disabled:opacity-50"
                  >
                    Pass
                  </button>
                  <button
                    type="button"
                    disabled={markingId === item.id}
                    onClick={() => void onMark(item.id, false)}
                    className="rounded-full border border-border px-3 py-1.5 text-xs disabled:opacity-50"
                  >
                    Fail
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="assign">
        <h2 className="font-display text-xl font-semibold">Recent assigns</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Assign from inside a course (Open → Assign work).
        </p>
        {outbox.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">No assignments yet.</p>
        ) : (
          <ul className="mt-4 space-y-2 text-sm">
            {outbox.slice(0, 8).map((a) => (
              <li key={a.id} className="text-muted-foreground">
                <span className="font-medium text-foreground">{a.title}</span>
                {a.trackId ? ` · ${a.trackId}` : ""}
                {a.lessonId ? ` · lesson ${a.lessonId}` : ""}
                {a.assigneeUid ? ` · ${a.assigneeUid}` : " · whole course"}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="payouts" className="rounded-2xl border border-border/70 bg-card/40 p-5">
        <h2 className="font-display text-xl font-semibold">Your payouts</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Paid from your school’s ledger (prototype). Full dashboard lands with
          payments.
        </p>
        <p className="mt-3 text-sm text-muted-foreground">No payout rows yet.</p>
      </section>
    </div>
  );
}
