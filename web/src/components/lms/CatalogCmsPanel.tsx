/**
 * Catalog CMS — chapter-centric course editor (manual Save).
 * Admins create new courses via dialog; mentors grow syllabus by chapters + lessons.
 */
import { useCallback, useEffect, useState } from "react";
import type { User } from "firebase/auth";

import {
  adminCreateLesson,
  adminCreateModule,
  adminCreateTrack,
  adminDeleteLesson,
  adminDeleteModule,
  adminUpdateLesson,
  adminUpdateModule,
  adminUpdateTrack,
  authorLessonQuiz,
  fetchAdminStats,
  fetchLmsModule,
  fetchLmsTrack,
  uploadLessonMedia,
  type AdminStatsDto,
  type LessonDto,
  type ModuleDto,
} from "@/lib/lmsApi";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  emptyQuestion,
  QuizQuestionsEditor,
  type QuizQuestionDraft,
} from "@/components/lms/QuizQuestionsEditor";

function slugId(prefix: string, title: string) {
  const base = title
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 40);
  const stamp = Date.now().toString(36).slice(-4);
  return `${prefix}-${base || "item"}-${stamp}`;
}

function lessonSlugPrefix(type: string) {
  if (type === "video") return "vid";
  if (type === "pdf") return "pdf";
  if (type === "text") return "txt";
  if (type === "code") return "lab";
  return "les";
}

function toLocalInput(ms: number | null | undefined) {
  if (!ms) return "";
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInput(value: string) {
  const t = new Date(value).getTime();
  return Number.isFinite(t) ? t : NaN;
}

type ChapterState = ModuleDto & {
  lessons: LessonDto[];
};

function chapterRowMeta(c: ChapterState) {
  const n = c.lessons.length;
  const lessons = `${n} lesson${n === 1 ? "" : "s"}`;
  const hasDates = Boolean(c.releaseAt && c.dueAt);
  return hasDates ? lessons : `${lessons} · dates missing`;
}

export function CatalogCmsPanel({
  user,
  schoolId,
  selectedTrackId,
  allowCreateTrack = false,
  onChanged,
}: {
  user: User;
  schoolId?: string;
  selectedTrackId?: string;
  allowCreateTrack?: boolean;
  lessons?: { lessonId: string; title: string }[];
  modules?: { moduleId: string; title: string }[];
  onChanged?: () => void;
}) {
  const [msg, setMsg] = useState<string | null>(null);
  const [stats, setStats] = useState<AdminStatsDto | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [trackId, setTrackId] = useState("");
  const [trackTitle, setTrackTitle] = useState("");
  const [editTrackId, setEditTrackId] = useState("");
  const [editTitle, setEditTitle] = useState("");
  const [editBlurb, setEditBlurb] = useState("");
  const [editPublished, setEditPublished] = useState(true);
  const [chapters, setChapters] = useState<ChapterState[]>([]);
  const [selectedChapterId, setSelectedChapterId] = useState<string | null>(
    null,
  );
  const [busy, setBusy] = useState(false);

  // Add chapter form
  const [newChapterTitle, setNewChapterTitle] = useState("");
  const [newChapterDoes, setNewChapterDoes] = useState("");
  const [newChapterStart, setNewChapterStart] = useState("");
  const [newChapterEnd, setNewChapterEnd] = useState("");

  // Edit selected chapter
  const [chTitle, setChTitle] = useState("");
  const [chDoes, setChDoes] = useState("");
  const [chStart, setChStart] = useState("");
  const [chEnd, setChEnd] = useState("");

  // Add lesson / optional first lesson on new chapter
  const [lesTitle, setLesTitle] = useState("");
  const [lesDoes, setLesDoes] = useState("");
  const [lesType, setLesType] = useState<"text" | "video" | "pdf" | "code">("text");
  const [labLanguage, setLabLanguage] = useState("python");
  const [labStarter, setLabStarter] = useState("print('hello')\n");
  const [labExpected, setLabExpected] = useState("");
  const [lesFile, setLesFile] = useState<File | null>(null);
  const [quizEnabled, setQuizEnabled] = useState(false);
  const [quizQuestions, setQuizQuestions] = useState<QuizQuestionDraft[]>([
    emptyQuestion(0),
  ]);
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  /** Accordion: at most one of add-form or a chapter editor is open. */
  const [addingChapter, setAddingChapter] = useState(false);
  const [includeFirstLesson, setIncludeFirstLesson] = useState(false);

  const selected = chapters.find((c) => c.moduleId === selectedChapterId) || null;

  function resetLessonForm() {
    setLesTitle("");
    setLesDoes("");
    setLesFile(null);
    setQuizEnabled(false);
    setQuizQuestions([emptyQuestion(0)]);
    setUploadPct(null);
    setLesType("text");
    setLabLanguage("python");
    setLabStarter("print('hello')\n");
    setLabExpected("");
    setIncludeFirstLesson(false);
  }

  function openAddChapter() {
    setSelectedChapterId(null);
    resetLessonForm();
    setAddingChapter((open) => !open);
  }

  function openChapter(moduleId: string) {
    setAddingChapter(false);
    resetLessonForm();
    setSelectedChapterId((prev) => (prev === moduleId ? null : moduleId));
  }

  const loadSyllabus = useCallback(async () => {
    const tid = selectedTrackId || editTrackId;
    if (!tid) {
      setChapters([]);
      return;
    }
    setBusy(true);
    try {
      const token = await user.getIdToken();
      const envelope = await fetchLmsTrack(token, tid);
      if (!envelope.ok || !envelope.data) return;
      const track = envelope.data.track;
      setEditTrackId(tid);
      setEditTitle(track.courseTitle || "");
      setEditBlurb(track.does || "");
      setEditPublished(true);
      const mods = envelope.data.modules || [];
      const loaded: ChapterState[] = [];
      for (const m of mods) {
        const modEnv = await fetchLmsModule(token, m.moduleId);
        loaded.push({
          ...m,
          lessons: modEnv.data?.lessons || [],
        });
      }
      setChapters(loaded);
      if (
        selectedChapterId &&
        !loaded.some((c) => c.moduleId === selectedChapterId)
      ) {
        setSelectedChapterId(null);
      }
    } catch {
      setMsg("Failed to load syllabus");
    } finally {
      setBusy(false);
    }
  }, [selectedTrackId, editTrackId, user, selectedChapterId]);

  useEffect(() => {
    if (selectedTrackId) setEditTrackId(selectedTrackId);
  }, [selectedTrackId]);

  useEffect(() => {
    void loadSyllabus();
  }, [selectedTrackId, user]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selected) {
      setChTitle("");
      setChDoes("");
      setChStart("");
      setChEnd("");
      return;
    }
    setChTitle(selected.title);
    setChDoes(selected.does || "");
    setChStart(toLocalInput(selected.releaseAt));
    setChEnd(toLocalInput(selected.dueAt));
  }, [selected]);

  async function loadStats() {
    const token = await user.getIdToken();
    const envelope = await fetchAdminStats(token);
    if (envelope.ok && envelope.data) setStats(envelope.data);
  }

  async function createTrack(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const id = trackId.trim() || slugId("track", trackTitle);
    const result = await adminCreateTrack(token, {
      trackId: id,
      title: trackTitle.trim(),
      ...(schoolId ? { schoolId } : {}),
    });
    setMsg(result.ok ? `Track ${id} published` : result.error || "Failed");
    if (result.ok) {
      setEditTrackId(id);
      setTrackId("");
      setTrackTitle("");
      setCreateOpen(false);
      void loadStats();
      onChanged?.();
    }
  }

  async function saveTrack(e: React.FormEvent) {
    e.preventDefault();
    if (!editTrackId.trim()) return;
    setMsg(null);
    const token = await user.getIdToken();
    const result = await adminUpdateTrack(token, editTrackId.trim(), {
      ...(editTitle.trim() ? { title: editTitle.trim() } : {}),
      blurb: editBlurb,
      published: editPublished,
    });
    setMsg(result.ok ? "Course saved" : result.error || "Failed");
    if (result.ok) {
      void loadStats();
      onChanged?.();
    }
  }

  async function addChapter(e: React.FormEvent) {
    e.preventDefault();
    const tid = (selectedTrackId || editTrackId).trim();
    if (!tid) {
      setMsg("Select a course first");
      return;
    }
    const releaseAt = fromLocalInput(newChapterStart);
    const dueAt = fromLocalInput(newChapterEnd);
    if (!newChapterTitle.trim() || !Number.isFinite(releaseAt) || !Number.isFinite(dueAt)) {
      setMsg("Chapter needs title, start, and end");
      return;
    }
    if (includeFirstLesson) {
      if (!lesTitle.trim()) {
        setMsg("First lesson needs a title (or uncheck optional media)");
        return;
      }
      if ((lesType === "video" || lesType === "pdf") && !lesFile) {
        setMsg("Choose a file for video/PDF, or pick Text");
        return;
      }
    }
    setMsg(null);
    const token = await user.getIdToken();
    const id = slugId("mod", newChapterTitle);
    const result = await adminCreateModule(token, {
      moduleId: id,
      trackId: tid,
      title: newChapterTitle.trim(),
      does: newChapterDoes.trim(),
      releaseAt,
      dueAt,
    });
    if (!result.ok) {
      setMsg(result.error || "Failed");
      return;
    }
    setNewChapterTitle("");
    setNewChapterDoes("");
    setNewChapterStart("");
    setNewChapterEnd("");
    if (includeFirstLesson) {
      const ok = await persistLesson(token, id, tid);
      if (!ok) {
        setSelectedChapterId(id);
        setAddingChapter(false);
        await loadSyllabus();
        onChanged?.();
        return;
      }
    }
    setMsg(includeFirstLesson ? "Chapter + first lesson saved" : "Chapter saved");
    resetLessonForm();
    setAddingChapter(false);
    setSelectedChapterId(id);
    await loadSyllabus();
    onChanged?.();
  }

  async function saveChapter(e: React.FormEvent) {
    e.preventDefault();
    if (!selected) return;
    const releaseAt = fromLocalInput(chStart);
    const dueAt = fromLocalInput(chEnd);
    if (!chTitle.trim() || !Number.isFinite(releaseAt) || !Number.isFinite(dueAt)) {
      setMsg("Chapter needs title, start, and end");
      return;
    }
    setMsg(null);
    const token = await user.getIdToken();
    const result = await adminUpdateModule(token, selected.moduleId, {
      title: chTitle.trim(),
      does: chDoes,
      releaseAt,
      dueAt,
    });
    setMsg(result.ok ? "Chapter saved" : result.error || "Failed");
    if (result.ok) {
      await loadSyllabus();
      onChanged?.();
    }
  }

  async function deleteChapter() {
    if (!selected) return;
    if (!confirm(`Delete chapter “${selected.title}” and its lessons?`)) return;
    setMsg(null);
    const token = await user.getIdToken();
    const result = await adminDeleteModule(token, selected.moduleId);
    setMsg(result.ok ? "Chapter deleted" : result.error || "Failed");
    if (result.ok) {
      setSelectedChapterId(null);
      setAddingChapter(false);
      await loadSyllabus();
      onChanged?.();
    }
  }

  /** Shared create + optional upload/quiz for a lesson inside a module. */
  async function persistLesson(
    token: string,
    moduleId: string,
    trackId: string,
  ): Promise<boolean> {
    if (!lesTitle.trim()) {
      setMsg("Lesson title required");
      return false;
    }
    if ((lesType === "video" || lesType === "pdf") && !lesFile) {
      setMsg("Choose a file for video/PDF lessons");
      return false;
    }
    const id = slugId(lessonSlugPrefix(lesType), lesTitle);
    const create = await adminCreateLesson(token, {
      lessonId: id,
      moduleId,
      trackId,
      title: lesTitle.trim(),
      type: lesType,
      does: lesDoes.trim(),
      hasQuiz: quizEnabled && (lesType === "pdf" || lesType === "video"),
      lab:
        lesType === "code"
          ? {
              language: labLanguage,
              starter: labStarter,
              expectedStdout: labExpected.trim() || null,
            }
          : undefined,
    });
    if (!create.ok) {
      setMsg(create.error || "Failed to create lesson");
      return false;
    }
    try {
      if (lesFile && (lesType === "video" || lesType === "pdf")) {
        setUploadPct(0);
        await uploadLessonMedia(token, {
          lessonId: id,
          file: lesFile,
          onProgress: setUploadPct,
        });
      }
      if (quizEnabled && (lesType === "pdf" || lesType === "video") && schoolId) {
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
        if (questions.length > 0) {
          await authorLessonQuiz(token, schoolId, id, {
            questions,
            prompt:
              questions.length === 1
                ? questions[0].prompt
                : `Quiz (${questions.length} questions)`,
          });
          await adminUpdateLesson(token, id, { hasQuiz: true });
        }
      }
      return true;
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Lesson save failed");
      setUploadPct(null);
      return false;
    }
  }

  async function addLesson(e: React.FormEvent) {
    e.preventDefault();
    if (!selected) {
      setMsg("Select a chapter first");
      return;
    }
    setMsg(null);
    const token = await user.getIdToken();
    const ok = await persistLesson(token, selected.moduleId, selected.trackId);
    if (!ok) return;
    setMsg(`Lesson “${lesTitle.trim()}” saved`);
    resetLessonForm();
    await loadSyllabus();
    onChanged?.();
  }

  async function deleteLesson(lessonId: string, title: string) {
    if (!confirm(`Delete lesson “${title}”?`)) return;
    const token = await user.getIdToken();
    const result = await adminDeleteLesson(token, lessonId);
    setMsg(result.ok ? "Lesson deleted" : result.error || "Failed");
    if (result.ok) {
      await loadSyllabus();
      onChanged?.();
    }
  }

  function renderLessonFields(opts?: { optional?: boolean }) {
    const show = !opts?.optional || includeFirstLesson;
    return (
      <div className="space-y-3">
        {opts?.optional ? (
          <label className="flex items-center gap-2 text-xs">
            <input
              type="checkbox"
              checked={includeFirstLesson}
              onChange={(e) => setIncludeFirstLesson(e.target.checked)}
            />
            Add first lesson / media (optional)
          </label>
        ) : null}
        {show ? (
          <>
            <input
              className="w-full rounded-lg border border-border bg-background px-3 py-2"
              placeholder="Lesson title"
              value={lesTitle}
              onChange={(e) => setLesTitle(e.target.value)}
              required={!opts?.optional || includeFirstLesson}
            />
            <label className="block space-y-1 text-xs">
              <span className="text-muted-foreground">Media type</span>
              <select
                className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                value={lesType}
                onChange={(e) =>
                  setLesType(e.target.value as "text" | "video" | "pdf" | "code")
                }
              >
                <option value="text">Text (write & submit)</option>
                <option value="video">Video (watch time)</option>
                <option value="pdf">PDF (doc + questions)</option>
                <option value="code">Code lab (in-browser IDE)</option>
              </select>
            </label>
            <textarea
              className="w-full rounded-lg border border-border bg-background px-3 py-2"
              rows={3}
              placeholder={
                lesType === "text"
                  ? "Prompts — what mentees should write / submit"
                  : "Description"
              }
              value={lesDoes}
              onChange={(e) => setLesDoes(e.target.value)}
            />
            {lesType === "code" ? (
              <>
                <label className="block space-y-1 text-xs">
                  <span className="text-muted-foreground">Language</span>
                  <select
                    className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                    value={labLanguage}
                    onChange={(e) => {
                      const lang = e.target.value;
                      setLabLanguage(lang);
                      if (!labStarter.trim() || labStarter === "print('hello')\n") {
                        setLabStarter(
                          lang === "javascript"
                            ? "console.log('hello');\n"
                            : lang === "html"
                              ? "<h1>hello</h1>\n"
                              : "print('hello')\n",
                        );
                      }
                    }}
                  >
                    <option value="python">Python</option>
                    <option value="javascript">JavaScript</option>
                    <option value="html">HTML</option>
                    <option value="java">Java</option>
                    <option value="c">C</option>
                    <option value="cpp">C++</option>
                  </select>
                </label>
                <textarea
                  className="w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-xs"
                  rows={8}
                  placeholder="Starter code"
                  value={labStarter}
                  onChange={(e) => setLabStarter(e.target.value)}
                />
                <input
                  className="w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm"
                  placeholder="Expected stdout (optional — auto-grade)"
                  value={labExpected}
                  onChange={(e) => setLabExpected(e.target.value)}
                />
              </>
            ) : null}
            {lesType === "video" || lesType === "pdf" ? (
              <>
                <input
                  type="file"
                  accept={lesType === "video" ? "video/*" : "application/pdf"}
                  onChange={(e) => setLesFile(e.target.files?.[0] || null)}
                />
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={quizEnabled}
                    onChange={(e) => setQuizEnabled(e.target.checked)}
                  />
                  Add quiz questions (A/B/C…, multiple OK)
                </label>
                {quizEnabled ? (
                  <QuizQuestionsEditor
                    questions={quizQuestions}
                    onChange={setQuizQuestions}
                  />
                ) : null}
              </>
            ) : null}
            {uploadPct != null ? (
              <p className="text-xs text-muted-foreground">Upload {uploadPct}%</p>
            ) : null}
          </>
        ) : null}
      </div>
    );
  }

  const activeTrackId = selectedTrackId || editTrackId;

  return (
    <div className="space-y-6 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-display text-lg font-semibold">
            {allowCreateTrack && !selectedTrackId ? "Catalog CMS" : "Edit course"}
          </h3>
          <p className="text-muted-foreground">
            Tap a chapter to expand it; + Add opens a new chapter form. Only one
            form is open at a time.
          </p>
        </div>
        {allowCreateTrack ? (
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger asChild>
              <button
                type="button"
                className="rounded-full border border-border px-4 py-1.5 text-sm font-medium hover:bg-secondary"
              >
                New course
              </button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>New course</DialogTitle>
                <DialogDescription>
                  Creates an empty track shell. Add chapters after.
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={createTrack} className="space-y-3">
                <input
                  className="w-full rounded-lg border border-border bg-background px-3 py-2"
                  placeholder="Title"
                  value={trackTitle}
                  onChange={(e) => {
                    setTrackTitle(e.target.value);
                    setTrackId(slugId("track", e.target.value));
                  }}
                  required
                />
                <button
                  type="submit"
                  className="rounded-full bg-ember px-4 py-2 text-sm font-medium text-white"
                >
                  Create
                </button>
              </form>
            </DialogContent>
          </Dialog>
        ) : null}
      </div>

      {msg ? (
        <p className="rounded-lg bg-secondary/60 px-3 py-2 text-xs">{msg}</p>
      ) : null}

      {stats ? (
        <p className="text-xs text-muted-foreground">
          Enrollments {stats.enrollmentsTotal} · Avg {stats.avgTrackPercent}% ·
          Completions (30d) {stats.completions30d}
          <button
            type="button"
            className="ml-2 underline"
            onClick={() => void loadStats()}
          >
            Refresh stats
          </button>
        </p>
      ) : (
        <button
          type="button"
          className="text-xs underline text-muted-foreground"
          onClick={() => void loadStats()}
        >
          Load stats
        </button>
      )}

      {!activeTrackId ? (
        <p className="text-muted-foreground">
          Open a course from Teach, or create one above.
        </p>
      ) : (
        <>
          <form onSubmit={saveTrack} className="space-y-3 rounded-2xl border border-border/70 p-4">
            <h4 className="font-medium">Course</h4>
            <input
              className="w-full rounded-lg border border-border bg-background px-3 py-2"
              placeholder="Title"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
            />
            <textarea
              className="w-full rounded-lg border border-border bg-background px-3 py-2"
              placeholder="Description"
              rows={2}
              value={editBlurb}
              onChange={(e) => setEditBlurb(e.target.value)}
            />
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={editPublished}
                onChange={(e) => setEditPublished(e.target.checked)}
              />
              Published
            </label>
            <button
              type="submit"
              className="rounded-full bg-ember px-4 py-1.5 text-sm font-medium text-white"
            >
              Save
            </button>
          </form>

          <div className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <h4 className="font-medium">Chapters</h4>
              <button
                type="button"
                onClick={openAddChapter}
                className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                  addingChapter
                    ? "bg-ember text-white"
                    : "border border-border hover:bg-secondary"
                }`}
                aria-expanded={addingChapter}
              >
                + Add
              </button>
            </div>

            {busy ? (
              <p className="text-xs text-muted-foreground">Loading…</p>
            ) : (
              <ul className="space-y-2">
                {chapters.length === 0 && !addingChapter ? (
                  <li className="text-xs text-muted-foreground">None yet.</li>
                ) : null}

                {chapters.map((c) => {
                  const expanded = selectedChapterId === c.moduleId;
                  return (
                    <li
                      key={c.moduleId}
                      className="overflow-hidden rounded-xl border border-border/70"
                    >
                      <button
                        type="button"
                        onClick={() => openChapter(c.moduleId)}
                        className={`flex w-full items-baseline justify-between gap-3 px-3 py-2.5 text-left hover:bg-secondary/40 ${
                          expanded ? "bg-secondary/40" : ""
                        }`}
                        aria-expanded={expanded}
                      >
                        <span className="min-w-0 font-medium text-sm">
                          {c.title}
                        </span>
                        <span className="shrink-0 text-xs text-muted-foreground">
                          {chapterRowMeta(c)}
                        </span>
                      </button>

                      {expanded && selected ? (
                        <div className="space-y-4 border-t border-border/60 bg-background/60 p-4">
                          <form onSubmit={saveChapter} className="space-y-3">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <h4 className="font-medium">Edit chapter</h4>
                              <button
                                type="button"
                                onClick={() => void deleteChapter()}
                                className="text-xs text-destructive underline"
                              >
                                Delete chapter
                              </button>
                            </div>
                            <input
                              className="w-full rounded-lg border border-border bg-background px-3 py-2"
                              value={chTitle}
                              onChange={(e) => setChTitle(e.target.value)}
                              required
                            />
                            <textarea
                              className="w-full rounded-lg border border-border bg-background px-3 py-2"
                              rows={2}
                              placeholder="Description"
                              value={chDoes}
                              onChange={(e) => setChDoes(e.target.value)}
                            />
                            <div className="grid gap-2 sm:grid-cols-2">
                              <label className="space-y-1 text-xs">
                                <span className="text-muted-foreground">Start</span>
                                <input
                                  type="datetime-local"
                                  className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                                  value={chStart}
                                  onChange={(e) => setChStart(e.target.value)}
                                  required
                                />
                              </label>
                              <label className="space-y-1 text-xs">
                                <span className="text-muted-foreground">End</span>
                                <input
                                  type="datetime-local"
                                  className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                                  value={chEnd}
                                  onChange={(e) => setChEnd(e.target.value)}
                                  required
                                />
                              </label>
                            </div>
                            <button
                              type="submit"
                              className="rounded-full bg-ember px-4 py-1.5 font-medium text-white"
                            >
                              Save
                            </button>
                          </form>

                          <div className="space-y-2">
                            <h5 className="font-medium">Lessons in this chapter</h5>
                            {selected.lessons.length === 0 ? (
                              <p className="text-xs text-muted-foreground">
                                None yet.
                              </p>
                            ) : (
                              <ul className="space-y-2">
                                {selected.lessons.map((l) => (
                                  <li
                                    key={l.lessonId}
                                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border/50 px-3 py-2"
                                  >
                                    <span>
                                      <span className="font-medium">{l.title}</span>
                                      <span className="ml-2 text-xs uppercase text-muted-foreground">
                                        {l.type === "read" ? "text" : l.type}
                                      </span>
                                    </span>
                                    <button
                                      type="button"
                                      className="text-xs text-destructive underline"
                                      onClick={() =>
                                        void deleteLesson(l.lessonId, l.title)
                                      }
                                    >
                                      Delete
                                    </button>
                                  </li>
                                ))}
                              </ul>
                            )}
                          </div>

                          <form
                            onSubmit={addLesson}
                            className="space-y-3 border-t border-border/60 pt-4"
                          >
                            <h5 className="font-medium">Add lesson</h5>
                            {renderLessonFields()}
                            <button
                              type="submit"
                              className="rounded-full border border-border px-4 py-1.5 font-medium hover:bg-secondary"
                            >
                              Save lesson
                            </button>
                          </form>
                        </div>
                      ) : null}
                    </li>
                  );
                })}

                {addingChapter ? (
                  <li className="list-none">
                    <form
                      onSubmit={addChapter}
                      className="space-y-3 rounded-2xl border border-dashed border-border p-4"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <h4 className="font-medium">Add chapter</h4>
                        <button
                          type="button"
                          className="text-xs text-muted-foreground underline"
                          onClick={() => {
                            setAddingChapter(false);
                            resetLessonForm();
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                      <input
                        className="w-full rounded-lg border border-border bg-background px-3 py-2"
                        placeholder="Title (e.g. Introduction)"
                        value={newChapterTitle}
                        onChange={(e) => setNewChapterTitle(e.target.value)}
                        required
                      />
                      <textarea
                        className="w-full rounded-lg border border-border bg-background px-3 py-2"
                        placeholder="Description"
                        rows={2}
                        value={newChapterDoes}
                        onChange={(e) => setNewChapterDoes(e.target.value)}
                      />
                      <div className="grid gap-2 sm:grid-cols-2">
                        <label className="space-y-1 text-xs">
                          <span className="text-muted-foreground">Start</span>
                          <input
                            type="datetime-local"
                            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                            value={newChapterStart}
                            onChange={(e) => setNewChapterStart(e.target.value)}
                            required
                          />
                        </label>
                        <label className="space-y-1 text-xs">
                          <span className="text-muted-foreground">End</span>
                          <input
                            type="datetime-local"
                            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                            value={newChapterEnd}
                            onChange={(e) => setNewChapterEnd(e.target.value)}
                            required
                          />
                        </label>
                      </div>
                      <div className="border-t border-border/60 pt-3">
                        {renderLessonFields({ optional: true })}
                      </div>
                      <button
                        type="submit"
                        className="rounded-full border border-border px-4 py-1.5 font-medium hover:bg-secondary"
                      >
                        Save chapter
                      </button>
                    </form>
                  </li>
                ) : null}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  );
}
