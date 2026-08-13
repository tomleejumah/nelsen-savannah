import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import {
  fetchMyAssignments,
  fetchMySubmissions,
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

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto max-w-3xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Student</p>
        <h1 className="mt-4 font-display text-4xl font-bold">My coursework</h1>
        <p className="mt-3 text-muted-foreground">
          Assigned work from mentors, plus submissions you’ve turned in.
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
            {assigned.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">No open assignments.</p>
            ) : (
              <ul className="mt-4 space-y-3">
                {assigned.map((a) => (
                  <li
                    key={a.id}
                    className="rounded-2xl border border-border/70 bg-card px-5 py-4"
                  >
                    <p className="font-display font-semibold">{a.title}</p>
                    <p className="mt-2 text-sm text-muted-foreground">
                      {a.prompt || "Complete and submit from the linked lesson."}
                    </p>
                    {a.lessonId && a.trackId ? (
                      <Link
                        to="/learning/$trackId/lesson/$lessonId"
                        params={{ trackId: a.trackId, lessonId: a.lessonId }}
                        className="mt-3 inline-flex text-sm font-medium text-maroon hover:underline"
                      >
                        Open lesson
                      </Link>
                    ) : a.trackId ? (
                      <Link
                        to="/learning/$trackId"
                        params={{ trackId: a.trackId }}
                        className="mt-3 inline-flex text-sm font-medium text-maroon hover:underline"
                      >
                        Open track
                      </Link>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}

            <h2 className="mt-12 font-display text-xl font-semibold">Your submissions</h2>
            {items.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">
                No submissions yet. Open a lesson with an assignment and submit from there.
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
                        {s.lessonId}
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
                    <Link
                      to="/learning/$trackId/lesson/$lessonId"
                      params={{ trackId: s.trackId, lessonId: s.lessonId }}
                      className="mt-3 inline-flex text-sm font-medium text-maroon hover:underline"
                    >
                      Open lesson
                    </Link>
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
