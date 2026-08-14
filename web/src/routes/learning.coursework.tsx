import { useCallback, useEffect, useState, type FormEvent } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  fetchMyAssignments,
  fetchMySubmissions,
  submitAssignment,
  type AssignmentDto,
  type SubmissionDto,
} from "@/lib/lmsApi";

export const Route = createFileRoute("/learning/coursework")({
  head: () => ({
    meta: [
      { title: "My coursework — Nelsen Savannah" },
      {
        name: "description",
        content: "Assigned work inbox and your LMS submissions.",
      },
    ],
  }),
  component: CourseworkPage,
});

function CourseworkPage() {
  const [user, setUser] = useState<User | null>(null);
  const [items, setItems] = useState<SubmissionDto[]>([]);
  const [assigned, setAssigned] = useState<AssignmentDto[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [savingId, setSavingId] = useState<string | null>(null);
  const [submitMsg, setSubmitMsg] = useState<string | null>(null);

  const load = useCallback(async (u: User) => {
    setBusy(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      const [subs, asgs] = await Promise.all([
        fetchMySubmissions(token),
        fetchMyAssignments(token),
      ]);
      if (!subs.ok || !subs.data) {
        setItems([]);
        setError(subs.error || "Could not load coursework");
      } else {
        setItems(subs.data.submissions || []);
      }
      setAssigned(asgs.data?.assignments || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
      setItems([]);
      setAssigned([]);
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      setUser(next);
      if (next) void load(next);
      else {
        setItems([]);
        setAssigned([]);
        setBusy(false);
      }
    });
  }, [load]);

  async function onSubmitAnswer(a: AssignmentDto, e: FormEvent) {
    e.preventDefault();
    if (!user) return;
    const text = (drafts[a.id] || "").trim();
    if (!text) {
      setSubmitMsg("Write your answer before submitting.");
      return;
    }
    setSavingId(a.id);
    setSubmitMsg(null);
    try {
      const token = await user.getIdToken();
      const result = await submitAssignment(token, {
        assignmentId: a.id,
        lessonId: a.lessonId || undefined,
        text,
      });
      if (!result.ok) {
        setSubmitMsg(result.error || "Submit failed");
        return;
      }
      setDrafts((d) => ({ ...d, [a.id]: "" }));
      setSubmitMsg("Submitted — waiting for mentor mark.");
      await load(user);
    } catch (err) {
      setSubmitMsg(err instanceof Error ? err.message : "Submit failed");
    } finally {
      setSavingId(null);
    }
  }

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-3xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Student</p>
        <h1 className="mt-4 font-display text-4xl font-bold">My coursework</h1>
        <p className="mt-3 text-muted-foreground">
          Open an assignment, write your answer, and submit. Mentors mark from Teach.
        </p>
        <div className="mt-4 flex flex-wrap gap-3 text-sm">
          <Link to="/learning" className="font-medium text-maroon hover:underline">
            ← Catalog
          </Link>
          <Link
            to="/learning/certificates"
            className="font-medium text-maroon hover:underline"
          >
            Certificates
          </Link>
          <Link to="/profile" className="font-medium text-maroon hover:underline">
            Profile
          </Link>
        </div>

        {!user ? (
          <div className="mt-10">
            <Link
              to="/login"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground"
            >
              <LogIn className="h-4 w-4" /> Sign in
            </Link>
          </div>
        ) : busy ? (
          <p className="mt-10 text-sm text-muted-foreground">Loading…</p>
        ) : error ? (
          <p className="mt-10 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </p>
        ) : (
          <>
            <h2 className="mt-10 font-display text-xl font-semibold">Assigned to you</h2>
            {submitMsg ? (
              <p className="mt-3 text-sm text-muted-foreground">{submitMsg}</p>
            ) : null}
            {assigned.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">No open assignments.</p>
            ) : (
              <ul className="mt-4 space-y-3">
                {assigned.map((a) => {
                  const isOpen = openId === a.id;
                  const already = items.find((s) => s.assignmentId === a.id);
                  return (
                    <li
                      key={a.id}
                      className="rounded-2xl border border-border/70 bg-card px-5 py-4"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0 flex-1">
                          <p className="font-display font-semibold">{a.title}</p>
                          <p className="mt-2 text-sm text-muted-foreground">
                            {a.prompt || "Complete and submit your answer below."}
                          </p>
                          {a.trackId ? (
                            <p className="mt-1 text-xs text-muted-foreground">
                              Track {a.trackId}
                              {a.lessonId ? ` · lesson ${a.lessonId}` : ""}
                            </p>
                          ) : null}
                        </div>
                        <button
                          type="button"
                          onClick={() => setOpenId(isOpen ? null : a.id)}
                          className="shrink-0 rounded-full bg-ember-gradient px-4 py-1.5 text-xs font-semibold text-maroon-foreground shadow-ember-glow"
                        >
                          {isOpen ? "Close" : already ? "View / resubmit" : "Open & answer"}
                        </button>
                      </div>

                      {isOpen ? (
                        <form
                          onSubmit={(e) => void onSubmitAnswer(a, e)}
                          className="mt-4 space-y-3 border-t border-border/60 pt-4"
                        >
                          {already ? (
                            <p className="text-xs text-muted-foreground">
                              Last submission: {already.status}
                              {already.score != null ? ` · score ${already.score}` : ""}
                              {already.feedback ? ` · ${already.feedback}` : ""}
                            </p>
                          ) : null}
                          <textarea
                            value={drafts[a.id] ?? ""}
                            onChange={(e) =>
                              setDrafts((d) => ({ ...d, [a.id]: e.target.value }))
                            }
                            rows={5}
                            placeholder="Write your answer…"
                            className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                          />
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="submit"
                              disabled={savingId === a.id}
                              className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground disabled:opacity-60"
                            >
                              {savingId === a.id ? "Submitting…" : "Submit answer"}
                            </button>
                            {a.lessonId && a.trackId ? (
                              <Link
                                to="/learning/$trackId/lesson/$lessonId"
                                params={{ trackId: a.trackId, lessonId: a.lessonId }}
                                className="rounded-full border border-border px-4 py-2 text-sm font-medium text-muted-foreground hover:bg-accent"
                              >
                                Open lesson materials
                              </Link>
                            ) : null}
                          </div>
                        </form>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            )}

            <h2 className="mt-12 font-display text-xl font-semibold">Your submissions</h2>
            {items.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">
                No submissions yet. Open an assignment above and submit your answer.
              </p>
            ) : (
              <ul className="mt-4 space-y-3">
                {items.map((s) => (
                  <li
                    key={s.id}
                    className="rounded-2xl border border-border/70 bg-card px-5 py-4"
                  >
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <p className="font-display font-semibold text-foreground">
                        {s.assignmentId || s.lessonId}
                      </p>
                      <span className="text-xs font-semibold uppercase tracking-wide text-ember">
                        {s.status}
                      </span>
                    </div>
                    <p className="mt-2 line-clamp-3 text-sm text-muted-foreground">
                      {s.text || "(no text)"}
                    </p>
                    <p className="mt-2 text-xs text-muted-foreground">
                      {new Date(s.submittedAt).toLocaleString()}
                      {s.score != null ? ` · Score ${s.score}` : ""}
                      {s.feedback ? ` · ${s.feedback}` : ""}
                    </p>
                    {s.trackId && s.lessonId ? (
                      <Link
                        to="/learning/$trackId/lesson/$lessonId"
                        params={{ trackId: s.trackId, lessonId: s.lessonId }}
                        className="mt-3 inline-flex text-sm font-medium text-maroon hover:underline"
                      >
                        Open lesson
                      </Link>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </div>
  );
}
