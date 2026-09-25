import { useMemo } from "react";

export type QuizOptionDraft = { id: string; text: string };
export type QuizQuestionDraft = {
  id: string;
  prompt: string;
  options: QuizOptionDraft[];
  correctOptionId: string;
};

const LETTERS = ["a", "b", "c", "d", "e", "f"] as const;

export function emptyQuestion(index = 0): QuizQuestionDraft {
  return {
    id: `q${index + 1}`,
    prompt: "",
    options: [
      { id: "a", text: "" },
      { id: "b", text: "" },
      { id: "c", text: "" },
    ],
    correctOptionId: "a",
  };
}

export function QuizQuestionsEditor({
  questions,
  onChange,
}: {
  questions: QuizQuestionDraft[];
  onChange: (next: QuizQuestionDraft[]) => void;
}) {
  const canAddQuestion = questions.length < 20;

  function updateQuestion(qi: number, patch: Partial<QuizQuestionDraft>) {
    onChange(
      questions.map((q, i) => (i === qi ? { ...q, ...patch } : q)),
    );
  }

  function updateOption(qi: number, oi: number, text: string) {
    onChange(
      questions.map((q, i) => {
        if (i !== qi) return q;
        const options = q.options.map((o, j) =>
          j === oi ? { ...o, text } : o,
        );
        return { ...q, options };
      }),
    );
  }

  function addOption(qi: number) {
    onChange(
      questions.map((q, i) => {
        if (i !== qi || q.options.length >= LETTERS.length) return q;
        const id = LETTERS[q.options.length];
        return { ...q, options: [...q.options, { id, text: "" }] };
      }),
    );
  }

  function removeOption(qi: number, oi: number) {
    onChange(
      questions.map((q, i) => {
        if (i !== qi || q.options.length <= 2) return q;
        const options = q.options.filter((_, j) => j !== oi);
        const correctOptionId = options.some((o) => o.id === q.correctOptionId)
          ? q.correctOptionId
          : options[0].id;
        return { ...q, options, correctOptionId };
      }),
    );
  }

  const hint = useMemo(
    () => "Add A/B/C (or more) choices per question. Mark the correct letter.",
    [],
  );

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">{hint}</p>
      {questions.map((q, qi) => (
        <div
          key={q.id}
          className="space-y-2 rounded-xl border border-border/70 bg-background/60 p-3"
        >
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Question {qi + 1}
            </span>
            {questions.length > 1 ? (
              <button
                type="button"
                className="text-xs text-muted-foreground underline"
                onClick={() => onChange(questions.filter((_, i) => i !== qi))}
              >
                Remove
              </button>
            ) : null}
          </div>
          <textarea
            required
            rows={2}
            value={q.prompt}
            onChange={(e) => updateQuestion(qi, { prompt: e.target.value })}
            placeholder="Question prompt"
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
          />
          <div className="space-y-2">
            {q.options.map((opt, oi) => (
              <div key={opt.id} className="flex flex-wrap items-center gap-2">
                <span className="w-6 text-center text-xs font-semibold uppercase text-muted-foreground">
                  {opt.id}
                </span>
                <input
                  required
                  value={opt.text}
                  onChange={(e) => updateOption(qi, oi, e.target.value)}
                  placeholder={`Option ${opt.id.toUpperCase()}`}
                  className="min-w-[8rem] flex-1 rounded-lg border border-border bg-background px-3 py-2 text-sm"
                />
                {q.options.length > 2 ? (
                  <button
                    type="button"
                    className="text-xs text-muted-foreground"
                    onClick={() => removeOption(qi, oi)}
                  >
                    ×
                  </button>
                ) : null}
              </div>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="text-xs text-muted-foreground">Correct</label>
            <select
              value={q.correctOptionId}
              onChange={(e) =>
                updateQuestion(qi, { correctOptionId: e.target.value })
              }
              className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
            >
              {q.options.map((opt) => (
                <option key={opt.id} value={opt.id}>
                  {opt.id.toUpperCase()} is correct
                </option>
              ))}
            </select>
            {q.options.length < LETTERS.length ? (
              <button
                type="button"
                className="rounded-full border border-border px-3 py-1.5 text-xs"
                onClick={() => addOption(qi)}
              >
                + Option
              </button>
            ) : null}
          </div>
        </div>
      ))}
      {canAddQuestion ? (
        <button
          type="button"
          className="rounded-full border border-border px-4 py-2 text-sm"
          onClick={() => onChange([...questions, emptyQuestion(questions.length)])}
        >
          + Add question
        </button>
      ) : null}
    </div>
  );
}
