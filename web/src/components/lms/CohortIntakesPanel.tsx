/**
 * Courses → Cohorts — pick a track, set price, then manage tools inside a cohort.
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
import {
  emptyQuestion,
  QuizQuestionsEditor,
  type QuizQuestionDraft,
} from "@/components/lms/QuizQuestionsEditor";

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
  const [selectedTrackId, setSelectedTrackId] = useState("");
  const [selectedCohortId, setSelectedCohortId] = useState("");
  const [cohortName, setCohortName] = useState("");
  const [memberUid, setMemberUid] = useState("");
  const [runId, setRunId] = useState("");
  const [mileLessonId, setMileLessonId] = useState("");
  const [mileRelease, setMileRelease] = useState("");
  const [mileDue, setMileDue] = useState("");
  const [mileRequiresPrevious, setMileRequiresPrevious] = useState(true);
  const [priceAmount, setPriceAmount] = useState("0");
  const [quizLessonId, setQuizLessonId] = useState("");
  const [quizQuestions, setQuizQuestions] = useState<QuizQuestionDraft[]>([
    emptyQuestion(0),
  ]);
  const [materialsMsg, setMaterialsMsg] = useState<string | null>(null);

  const selectedTrack = tracks.find((t) => t.trackId === selectedTrackId) || null;
  const selectedCohort =
    cohorts.find((c) => c.cohortId === selectedCohortId) || null;

  const loadCohorts = useCallback(async () => {
    const token = await user.getIdToken();
    const result = await fetchSchoolCohorts(token, schoolId);
    if (result.ok) setCohorts(result.data?.cohorts || []);
  }, [user, schoolId]);

  useEffect(() => {
    void loadCohorts();
  }, [loadCohorts]);

  useEffect(() => {
    if (!selectedTrack) {
      setPriceAmount("0");
      return;
    }
    const minor = selectedTrack.price?.amountMinor ?? 0;
    setPriceAmount(String(minor / 100));
  }, [selectedTrack]);

  useEffect(() => {
    setRunId("");
    setMemberUid("");
    setMileLessonId("");
    setQuizLessonId("");
  }, [selectedCohortId]);

  const learnerOptions = [
    ...new Map(mentees.map((m) => [m.uid, m])).values(),
  ];

  return (
    <section id="materials" className="space-y-5">
      <div>
        <h2 className="font-display text-xl font-semibold">Courses → Cohorts</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Pick a course, set its price, then open a cohort to add learners,
          attach this course run, schedule milestones, and publish quizzes.
        </p>
      </div>

      {materialsMsg ? (
        <p className="rounded-xl border border-border/60 bg-background/80 px-4 py-3 text-sm text-muted-foreground">
          {materialsMsg}
        </p>
      ) : null}

      <div className="space-y-2">
        <h3 className="font-medium">1. Select course</h3>
        {tracks.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No courses yet — create one in Catalog CMS above.
          </p>
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2">
            {tracks.map((t) => {
              const active = t.trackId === selectedTrackId;
              const priceLabel =
                t.price?.isPaid && (t.price.amountMinor || 0) > 0
                  ? `${t.price.currency || "KES"} ${(t.price.amountMinor / 100).toLocaleString()}`
                  : "Free";
              return (
                <li key={t.trackId}>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedTrackId(t.trackId);
                      setSelectedCohortId("");
                      setMaterialsMsg(null);
                    }}
                    className={`w-full rounded-2xl border px-4 py-3 text-left text-sm transition ${
                      active
                        ? "border-ember/50 bg-ember/5"
                        : "border-border/70 bg-card/40 hover:border-ember/30"
                    }`}
                  >
                    <p className="font-display font-semibold text-foreground">
                      {t.courseTitle}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {priceLabel}
                    </p>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {selectedTrack ? (
        <div className="space-y-6 rounded-2xl border border-border/70 bg-card/40 p-5">
          <div>
            <p className="text-xs uppercase tracking-wide text-muted-foreground">
              Course
            </p>
            <h3 className="font-display text-lg font-semibold">
              {selectedTrack.courseTitle}
            </h3>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              void (async () => {
                setMaterialsMsg(null);
                const token = await user.getIdToken();
                const cents = Math.round(Number(priceAmount || 0) * 100);
                const result = await setTrackPricing(
                  token,
                  schoolId,
                  selectedTrack.trackId,
                  { amountMinor: cents, currency: "KES" },
                );
                setMaterialsMsg(
                  result.ok
                    ? cents
                      ? "Price set — enroll will paywall (web + Android)."
                      : "Track is free."
                    : result.error || "Failed",
                );
              })();
            }}
            className="space-y-2"
          >
            <h4 className="font-medium">Track price</h4>
            <p className="text-xs text-muted-foreground">
              Amount in KES (0 = free). Android and web both read{" "}
              <code className="text-[0.7rem]">amountMinor</code>.
            </p>
            <div className="flex flex-wrap gap-2">
              <input
                value={priceAmount}
                onChange={(e) => setPriceAmount(e.target.value)}
                inputMode="decimal"
                placeholder="KES amount (0 = free)"
                className="w-40 rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
              <button
                type="submit"
                className="rounded-full border border-border px-4 py-2 text-sm"
              >
                Save price
              </button>
            </div>
          </form>

          <div className="space-y-3 border-t border-border/60 pt-5">
            <h4 className="font-medium">2. Cohorts for this school</h4>
            <p className="text-xs text-muted-foreground">
              Create an intake, then open it to attach{" "}
              <span className="font-medium text-foreground">
                {selectedTrack.courseTitle}
              </span>{" "}
              and run cohort tools.
            </p>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                void (async () => {
                  setMaterialsMsg(null);
                  const token = await user.getIdToken();
                  const result = await createSchoolCohort(token, schoolId, {
                    name: cohortName.trim(),
                  });
                  setMaterialsMsg(
                    result.ok ? "Cohort created." : result.error || "Failed",
                  );
                  if (result.ok && result.data?.cohort.cohortId) {
                    setSelectedCohortId(result.data.cohort.cohortId);
                    setCohortName("");
                    await loadCohorts();
                  }
                })();
              }}
              className="flex flex-wrap gap-2"
            >
              <input
                required
                value={cohortName}
                onChange={(e) => setCohortName(e.target.value)}
                placeholder="New cohort name (e.g. May 2026)"
                className="min-w-[12rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
              <button
                type="submit"
                className="rounded-full bg-ember-gradient px-4 py-2 text-sm font-semibold text-maroon-foreground"
              >
                Create cohort
              </button>
            </form>

            {cohorts.length === 0 ? (
              <p className="text-sm text-muted-foreground">No cohorts yet.</p>
            ) : (
              <ul className="space-y-2">
                {cohorts.map((c) => {
                  const active = c.cohortId === selectedCohortId;
                  return (
                    <li key={c.cohortId}>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedCohortId(c.cohortId);
                          setMaterialsMsg(null);
                        }}
                        className={`w-full rounded-xl border px-4 py-3 text-left text-sm transition ${
                          active
                            ? "border-ember/50 bg-ember/5"
                            : "border-border/60 bg-background/60 hover:border-ember/30"
                        }`}
                      >
                        <span className="font-medium text-foreground">
                          {c.name}
                        </span>
                        <span className="mt-0.5 block text-xs text-muted-foreground">
                          {c.memberCount || 0} members · {c.runCount || 0} runs
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {selectedCohort ? (
            <div className="space-y-5 border-t border-border/60 pt-5">
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">
                  Cohort
                </p>
                <h4 className="font-display text-lg font-semibold">
                  {selectedCohort.name}
                </h4>
                <p className="text-xs text-muted-foreground">
                  Tools below apply to this cohort only.
                </p>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void (async () => {
                    setMaterialsMsg(null);
                    const token = await user.getIdToken();
                    const result = await addCohortMember(
                      token,
                      schoolId,
                      selectedCohort.cohortId,
                      memberUid,
                    );
                    setMaterialsMsg(
                      result.ok
                        ? "Learner added to cohort."
                        : result.error || "Failed",
                    );
                  })();
                }}
                className="space-y-2"
              >
                <h5 className="font-medium">Add learner</h5>
                <div className="flex flex-wrap gap-2">
                  <select
                    required
                    value={memberUid}
                    onChange={(e) => setMemberUid(e.target.value)}
                    className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
                  >
                    <option value="">Select learner</option>
                    {learnerOptions.map((m) => (
                      <option key={m.uid} value={m.uid}>
                        {m.displayName || m.uid}
                      </option>
                    ))}
                  </select>
                  <button
                    type="submit"
                    className="rounded-full border border-border px-4 py-2 text-sm"
                  >
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
                    const result = await createCohortRun(
                      token,
                      schoolId,
                      selectedCohort.cohortId,
                      { trackId: selectedTrack.trackId },
                    );
                    setMaterialsMsg(
                      result.ok
                        ? "Course attached — run ready for milestones/quizzes."
                        : result.error || "Failed",
                    );
                    if (result.ok && result.data?.run.runId) {
                      setRunId(result.data.run.runId);
                      await loadCohorts();
                    }
                  })();
                }}
                className="space-y-2"
              >
                <h5 className="font-medium">Attach this course</h5>
                <p className="text-xs text-muted-foreground">
                  Binds <span className="font-medium">{selectedTrack.courseTitle}</span>{" "}
                  to {selectedCohort.name} and creates a runId.
                </p>
                <button
                  type="submit"
                  className="rounded-full border border-border px-4 py-2 text-sm"
                >
                  Attach course to cohort
                </button>
                {runId ? (
                  <p className="text-xs text-muted-foreground">
                    Active run: <code className="text-[0.7rem]">{runId}</code>
                  </p>
                ) : null}
              </form>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void (async () => {
                    setMaterialsMsg(null);
                    if (!runId.trim()) {
                      setMaterialsMsg("Attach the course first to get a runId.");
                      return;
                    }
                    const token = await user.getIdToken();
                    const result = await createMilestone(
                      token,
                      schoolId,
                      runId,
                      {
                        lessonId: mileLessonId.trim(),
                        releaseAt: new Date(mileRelease).getTime(),
                        ...(mileDue
                          ? { dueAt: new Date(mileDue).getTime() }
                          : {}),
                        requiresPreviousCompletion: mileRequiresPrevious,
                      },
                    );
                    setMaterialsMsg(
                      result.ok
                        ? "Milestone scheduled."
                        : result.error || "Failed",
                    );
                  })();
                }}
                className="space-y-2"
              >
                <h5 className="font-medium">Schedule milestone</h5>
                <div className="flex flex-wrap gap-2">
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
                      onChange={(e) =>
                        setMileRequiresPrevious(e.target.checked)
                      }
                    />
                    Require previous
                  </label>
                  <button
                    type="submit"
                    className="rounded-full border border-border px-4 py-2 text-sm"
                  >
                    Schedule
                  </button>
                </div>
              </form>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void (async () => {
                    setMaterialsMsg(null);
                    if (!runId.trim()) {
                      setMaterialsMsg("Attach the course first to get a runId.");
                      return;
                    }
                    const token = await user.getIdToken();
                    const questions = quizQuestions
                      .map((q, i) => ({
                        id: q.id || `q${i + 1}`,
                        prompt: q.prompt.trim(),
                        options: q.options
                          .map((o) => ({ id: o.id, text: o.text.trim() }))
                          .filter((o) => o.text),
                        correctOptionId: q.correctOptionId,
                      }))
                      .filter((q) => q.prompt && q.options.length >= 2);
                    if (!questions.length) {
                      setMaterialsMsg(
                        "Add at least one question with A/B/C options.",
                      );
                      return;
                    }
                    const firstPrompt = questions[0]?.prompt;
                    const result = await authorLessonQuiz(
                      token,
                      schoolId,
                      quizLessonId.trim(),
                      {
                        questions,
                        prompt:
                          questions.length === 1 && firstPrompt
                            ? firstPrompt
                            : `Quiz (${questions.length} questions)`,
                        runId,
                      },
                    );
                    setMaterialsMsg(
                      result.ok
                        ? `Quiz v${result.data?.quiz.version} published (${questions.length} question${questions.length === 1 ? "" : "s"}).`
                        : result.error || "Failed",
                    );
                    if (result.ok) setQuizQuestions([emptyQuestion(0)]);
                  })();
                }}
                className="space-y-2"
              >
                <h5 className="font-medium">Quiz for this cohort lesson</h5>
                <p className="text-xs text-muted-foreground">
                  Uses the runId from attach above. New versions affect future
                  attempts only.
                </p>
                <input
                  required
                  value={quizLessonId}
                  onChange={(e) => setQuizLessonId(e.target.value)}
                  placeholder="lessonId"
                  className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                />
                <QuizQuestionsEditor
                  questions={quizQuestions}
                  onChange={setQuizQuestions}
                />
                <button
                  type="submit"
                  className="rounded-full border border-border px-4 py-2 text-sm"
                >
                  Publish quiz version
                </button>
              </form>
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
