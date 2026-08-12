import { useCallback, useEffect, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { LogIn } from "lucide-react";

import { getFirebaseAuth } from "@/lib/firebase";
import { fetchMySubmissions, type SubmissionDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/learning/coursework")({
  head: () => ({
    meta: [
      { title: "My coursework — Nelsen Savannah" },
      {
        name: "description",
        content: "Your LMS assignment submissions and marking status.",
      },
    ],
  }),
  component: CourseworkPage,
});

function CourseworkPage() {
  const [user, setUser] = useState<User | null>(null);
  const [items, setItems] = useState<SubmissionDto[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (u: User) => {
    setBusy(true);
    setError(null);
    try {
      const token = await u.getIdToken();
      const envelope = await fetchMySubmissions(token);
      if (!envelope.ok || !envelope.data) {
        setItems([]);
        setError(envelope.error || "Could not load coursework");
        return;
      }
      setItems(envelope.data.submissions || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
      setItems([]);
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
          Assignments you’ve submitted. Mentors mark these in Teach (L2).
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
        ) : items.length === 0 ? (
          <p className="mt-10 text-sm text-muted-foreground">
            No submissions yet. Open a lesson with an assignment and submit from there.
          </p>
        ) : (
          <ul className="mt-10 space-y-3">
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
      </div>
    </div>
  );
}
