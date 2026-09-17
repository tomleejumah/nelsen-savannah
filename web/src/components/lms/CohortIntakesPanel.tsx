/**
 * Cohort walkthrough — create intakes, attach tracks, milestones, pricing, quizzes.
 */
import { useCallback, useEffect, useState } from "react";
import type { User } from "firebase/auth";

import {
  addCohortMember,
  authorLessonQuiz,
  createCohortRun,
  createMilestone,
  createSchoolCohort,
  fetchSchoolCohorts,
  setTrackPricing,
  type CohortDto,
  type MenteeProgressDto,
  type TrackCardDto,
} from "@/lib/lmsApi";

export function CohortIntakesPanel({
  user,
  schoolId,
  tracks,
  mentees,
}: {
  user: User;
  schoolId: string;
  tracks: TrackCardDto[];
  mentees: MenteeProgressDto[];
}) {
  const [cohorts, setCohorts] = useState<CohortDto[]>([]);
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

  const loadCohorts = useCallback(async () => {
    const token = await user.getIdToken();
    const result = await fetchSchoolCohorts(token, schoolId);
    if (result.ok) setCohorts(result.data?.cohorts || []);
  }, [user, schoolId]);

  useEffect(() => {
    void loadCohorts();
  }, [loadCohorts]);

  return (
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
              await loadCohorts();
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
              await loadCohorts();
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
  );
}
