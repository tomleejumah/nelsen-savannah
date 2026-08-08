/** Progress math — mirrors nelsen-savanna lms-roadmap.js */

export const LESSON_WEIGHTS = {
  open: 10,
  content: 40,
  quiz: 20,
  assignment: 30,
};

export const PASS_THRESHOLD = 80;

function clamp(n) {
  return Math.max(0, Math.min(100, Number(n) || 0));
}

/**
 * @param {{
 *   opened?: boolean,
 *   contentPct?: number,
 *   quizPct?: number,
 *   assignmentPct?: number,
 *   hasQuiz?: boolean,
 *   hasAssignment?: boolean,
 * }} p
 */
export function lessonPercentComplete(p) {
  const w = { ...LESSON_WEIGHTS };
  if (!p.hasQuiz) {
    w.content += w.quiz;
    w.quiz = 0;
  }
  if (!p.hasAssignment) {
    w.content += w.assignment;
    w.assignment = 0;
  }
  const open = p.opened ? w.open : 0;
  const content = (clamp(p.contentPct) / 100) * w.content;
  const quiz = (clamp(p.quizPct) / 100) * w.quiz;
  const assignment = (clamp(p.assignmentPct) / 100) * w.assignment;
  return Math.round(open + content + quiz + assignment);
}

export function aggregatePercent(percents, weights) {
  if (!percents.length) return 0;
  if (!weights || weights.length !== percents.length) {
    return Math.round(percents.reduce((a, b) => a + b, 0) / percents.length);
  }
  const totalW = weights.reduce((a, b) => a + b, 0) || 1;
  const sum = percents.reduce((acc, pct, i) => acc + pct * weights[i], 0);
  return Math.round(sum / totalW);
}

export function lessonStatusFromPercent(pct, { opened } = {}) {
  if (pct >= PASS_THRESHOLD) return "passed";
  if (pct > 0 || opened) return "in_progress";
  return "available";
}

export function enrollmentStatusFromPercent(trackPercent) {
  if (trackPercent >= PASS_THRESHOLD) return "completed";
  if (trackPercent > 0) return "in_progress";
  return "not_started";
}
