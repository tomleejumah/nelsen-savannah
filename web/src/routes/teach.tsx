import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import {
  createAssignment,
  fetchAssignedOutbox,
  fetchMenteeProgress,
  fetchSubmissionQueue,
  markSubmission,
  type AssignmentDto,
  type MeDto,
  type MenteeProgressDto,
  type QueueItemDto,
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
      blurb="Mark submissions, watch student progress, and assign coursework."
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
  const [assignTrackId, setAssignTrackId] = useState("");
  const [assignUid, setAssignUid] = useState("");
  const [assignMsg, setAssignMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [q, m, a] = await Promise.all([
        fetchSubmissionQueue(token),
        fetchMenteeProgress(token, me.uid),
        fetchAssignedOutbox(token),
      ]);
      if (!q.ok) setError(q.error || "Queue failed");
      setQueue(q.data?.queue || []);
      setMentees(m.data?.mentees || []);
      setOutbox(a.data?.assignments || []);
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

  async function onAssign(e: React.FormEvent) {
    e.preventDefault();
    setAssignMsg(null);
    const token = await user.getIdToken();
    const result = await createAssignment(token, {
      title: assignTitle.trim(),
      prompt: assignPrompt.trim() || undefined,
      trackId: assignTrackId.trim() || undefined,
      assigneeUid: assignUid.trim() || undefined,
    });
    if (!result.ok) {
      setAssignMsg(result.error || "Assign failed");
      return;
    }
    setAssignTitle("");
    setAssignPrompt("");
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
          Target a student uid and/or a track (all enrolled students).
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
            placeholder="Prompt / instructions"
            rows={3}
            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <div className="flex flex-wrap gap-3">
            <input
              value={assignTrackId}
              onChange={(e) => setAssignTrackId(e.target.value)}
              placeholder="trackId (optional)"
              className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              value={assignUid}
              onChange={(e) => setAssignUid(e.target.value)}
              placeholder="assignee uid (optional)"
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
                {a.assigneeUid ? ` · ${a.assigneeUid}` : ""}
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <Link
        to="/learning"
        className="inline-flex text-sm font-medium text-maroon hover:underline"
      >
        Also browse learning catalog →
      </Link>
    </div>
  );
}
