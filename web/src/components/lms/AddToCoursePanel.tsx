/**
 * Mentor "Add to course" — gradual video / PDF / course assignment / milestone.
 */
import { useEffect, useState } from "react";
import type { User } from "firebase/auth";

import {
  adminCreateLesson,
  adminCreateModule,
  adminUpdateModule,
  createAssignment,
  createMilestone,
  fetchLmsModule,
  fetchLmsTrack,
  uploadLessonMedia,
  uploadLmsAttachment,
  type LessonDto,
  type ModuleDto,
  type TrackCardDto,
} from "@/lib/lmsApi";

type Kind = "video" | "pdf" | "document" | "assignment" | "milestone" | "timeline";

function toLocalDateTime(timestamp?: number | null) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
}

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

export function AddToCoursePanel({
  user,
  schoolId,
  tracks,
  runId,
  initialTrackId,
  lockTrack = false,
  onDone,
}: {
  user: User;
  schoolId: string;
  tracks: TrackCardDto[];
  runId?: string;
  initialTrackId?: string;
  /** When true, course is fixed to initialTrackId (no dropdown). */
  lockTrack?: boolean;
  onDone?: () => void;
}) {
  const [kind, setKind] = useState<Kind>("video");
  const [trackId, setTrackId] = useState(initialTrackId || "");
  const [modules, setModules] = useState<ModuleDto[]>([]);
  const [lessons, setLessons] = useState<LessonDto[]>([]);
  const [moduleId, setModuleId] = useState("");
  const [newModuleTitle, setNewModuleTitle] = useState("");
  const [title, setTitle] = useState("");
  const [prompt, setPrompt] = useState("");
  const [modelAnswer, setModelAnswer] = useState("");
  const [lessonIdForMile, setLessonIdForMile] = useState("");
  const [releaseAt, setReleaseAt] = useState("");
  const [dueAt, setDueAt] = useState("");
  const [timelineModuleId, setTimelineModuleId] = useState("");
  const [timelineReleaseAt, setTimelineReleaseAt] = useState("");
  const [timelineDueAt, setTimelineDueAt] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [assignmentFiles, setAssignmentFiles] = useState<File[]>([]);
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (initialTrackId) setTrackId(initialTrackId);
  }, [initialTrackId]);

  useEffect(() => {
    if (!trackId) {
      setModules([]);
      setLessons([]);
      setModuleId("");
      setTimelineModuleId("");
      setTimelineReleaseAt("");
      setTimelineDueAt("");
      return;
    }
    let cancelled = false;
    void (async () => {
      const token = await user.getIdToken();
      const envelope = await fetchLmsTrack(token, trackId);
      if (cancelled) return;
      const list = envelope.data?.modules || [];
      setModules(list);
      setModuleId((prev) => (prev && list.some((m) => m.moduleId === prev) ? prev : ""));
      const selectedTimelineId = list.some((m) => m.moduleId === timelineModuleId)
        ? timelineModuleId
        : list[0]?.moduleId || "";
      setTimelineModuleId(selectedTimelineId);
      const selectedTimelineModule = list.find((m) => m.moduleId === selectedTimelineId);
      setTimelineReleaseAt(toLocalDateTime(selectedTimelineModule?.releaseAt));
      setTimelineDueAt(toLocalDateTime(selectedTimelineModule?.dueAt));
      const lessonLists = await Promise.all(
        list.map(async (m) => {
          const modEnv = await fetchLmsModule(token, m.moduleId);
          return modEnv.data?.lessons || [];
        }),
      );
      if (cancelled) return;
      setLessons(lessonLists.flat());
    })();
    return () => {
      cancelled = true;
    };
  }, [trackId, user]);

  async function ensureModule(token: string): Promise<string> {
    if (moduleId && moduleId !== "__new__") return moduleId;
    const modTitle = newModuleTitle.trim() || "New section";
    const id = slugId("mod", modTitle);
    const releaseAt = Date.now();
    const dueAt = releaseAt + 7 * 24 * 60 * 60 * 1000;
    const result = await adminCreateModule(token, {
      moduleId: id,
      trackId,
      title: modTitle,
      releaseAt,
      dueAt,
    });
    if (!result.ok) throw new Error(result.error || "Could not create module");
    setModuleId(id);
    setModules((prev) => [
      ...prev,
      {
        moduleId: id,
        trackId,
        title: modTitle,
        does: "",
        estimatedMinutes: 0,
        lessonCount: 0,
        releaseAt,
        dueAt,
      },
    ]);
    return id;
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    if (!trackId) {
      setMsg("Pick a course (track) first.");
      return;
    }
    setBusy(true);
    setUploadPct(null);
    try {
      const token = await user.getIdToken();

      if (kind === "assignment") {
        if (!title.trim()) throw new Error("Title required");
        const assignBody: {
          title: string;
          trackId: string;
          prompt?: string;
          modelAnswer?: string;
          attachmentMediaIds?: string[];
        } = { title: title.trim(), trackId };
        if (prompt.trim()) assignBody.prompt = prompt.trim();
        if (modelAnswer.trim()) assignBody.modelAnswer = modelAnswer.trim();
        const uploaded = [];
        for (const attachment of assignmentFiles) uploaded.push(await uploadLmsAttachment(token, attachment));
        assignBody.attachmentMediaIds = uploaded.map((attachment) => attachment.mediaId);
        const result = await createAssignment(token, assignBody);
        if (!result.ok) throw new Error(result.error || "Assign failed");
        setMsg(`Course assignment published for ${trackId}.`);
        setTitle("");
        setPrompt("");
        setModelAnswer("");
        setAssignmentFiles([]);
        onDone?.();
        return;
      }

      if (kind === "timeline") {
        const selected = modules.find((module) => module.moduleId === timelineModuleId);
        if (!selected) throw new Error("Select a module / unit first");
        if (!timelineReleaseAt || !timelineDueAt) throw new Error("Set both the unlock and close dates");
        const nextReleaseAt = new Date(timelineReleaseAt).getTime();
        const nextDueAt = new Date(timelineDueAt).getTime();
        if (!Number.isFinite(nextReleaseAt) || !Number.isFinite(nextDueAt) || nextDueAt < nextReleaseAt) {
          throw new Error("The close date must be on or after the unlock date");
        }
        const result = await adminUpdateModule(token, selected.moduleId, {
          releaseAt: nextReleaseAt,
          dueAt: nextDueAt,
        });
        if (!result.ok) throw new Error(result.error || "Could not update module timeline");
        setModules((prev) => prev.map((module) => module.moduleId === selected.moduleId
          ? { ...module, releaseAt: nextReleaseAt, dueAt: nextDueAt }
          : module));
        setMsg(`Unlock timeline updated for ${selected.title}.`);
        onDone?.();
        return;
      }

      if (kind === "milestone") {
        if (!runId) throw new Error("Attach a cohort run first (Cohort walkthrough below).");
        if (!lessonIdForMile.trim()) throw new Error("Lesson id required");
        if (!releaseAt) throw new Error("Release date required");
        const body: {
          lessonId: string;
          releaseAt: number;
          dueAt?: number;
          requiresPreviousCompletion: boolean;
        } = {
          lessonId: lessonIdForMile.trim(),
          releaseAt: new Date(releaseAt).getTime(),
          requiresPreviousCompletion: true,
        };
        if (dueAt) body.dueAt = new Date(dueAt).getTime();
        const result = await createMilestone(token, schoolId, runId, body);
        if (!result.ok) throw new Error(result.error || "Milestone failed");
        setMsg("Milestone scheduled on this course run.");
        onDone?.();
        return;
      }

      // video | pdf → module + lesson + media
      if (!file) throw new Error("Choose a file to upload");
      if (!title.trim()) throw new Error("Lesson title required");
      if (moduleId === "__new__" && !newModuleTitle.trim()) {
        throw new Error("Name the new section / module");
      }
      if (!moduleId) throw new Error("Pick or create a module");

      const mid = await ensureModule(token);
      const lid = slugId(kind === "video" ? "vid" : kind === "pdf" ? "pdf" : "doc", title);
      const lessonType = kind === "video" ? "video" : "read";
      const created = await adminCreateLesson(token, {
        lessonId: lid,
        moduleId: mid,
        trackId,
        title: title.trim(),
        type: lessonType,
      });
      if (!created.ok) throw new Error(created.error || "Lesson create failed");

      setUploadPct(0);
      const media = await uploadLessonMedia(token, {
        lessonId: lid,
        file,
        onProgress: setUploadPct,
      });
      setMsg(
        `${kind === "video" ? "Video" : kind === "pdf" ? "PDF" : "Document"} lesson added to ${trackId} (${media.mediaId}).`,
      );
      setTitle("");
      setFile(null);
      setUploadPct(null);
      onDone?.();
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Failed");
      setUploadPct(null);
    } finally {
      setBusy(false);
    }
  }

  const kinds: { id: Kind; label: string; blurb: string }[] = [
    { id: "video", label: "Video", blurb: "Lesson + upload on this track" },
    { id: "pdf", label: "PDF", blurb: "PDF reading material for the course" },
    { id: "document", label: "Document", blurb: "Word, Excel, PowerPoint, text and image resources" },
    { id: "assignment", label: "Assignment", blurb: "Course-wide — all enrolled mentees" },
    { id: "milestone", label: "Milestone", blurb: "Release / due date on a lesson in a cohort run" },
    { id: "timeline", label: "Unit timeline", blurb: "Change when a whole module / unit unlocks and closes" },
  ];

  return (
    <section id="add" className="space-y-4 rounded-2xl border border-border/70 bg-card/40 p-5">
      <div>
        <h2 className="font-display text-xl font-semibold">Add to course</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Grow a track gradually — video, documents, course assignments, or calendar milestones.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {kinds.map((k) => (
          <button
            key={k.id}
            type="button"
            onClick={() => {
              setKind(k.id);
              setMsg(null);
            }}
            className={
              kind === k.id
                ? "rounded-full bg-ember-gradient px-4 py-1.5 text-sm font-semibold text-maroon-foreground"
                : "rounded-full border border-border px-4 py-1.5 text-sm font-medium hover:bg-secondary"
            }
          >
            {k.label}
          </button>
        ))}
      </div>
      <p className="text-xs text-muted-foreground">{kinds.find((k) => k.id === kind)?.blurb}</p>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-3">
        {lockTrack && trackId ? (
          <div className="rounded-xl border border-border/70 bg-background/60 px-4 py-3">
            <p className="text-xs text-muted-foreground">Course</p>
            <p className="font-display font-semibold">
              {tracks.find((t) => t.trackId === trackId)?.courseTitle || trackId}
            </p>
          </div>
        ) : (
          <label className="block text-sm">
            <span className="text-muted-foreground">Course (track)</span>
            <select
              required
              value={trackId}
              onChange={(e) => setTrackId(e.target.value)}
              className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Select track</option>
              {tracks.map((t) => (
                <option key={t.trackId} value={t.trackId}>
                  {t.courseTitle}
                </option>
              ))}
            </select>
          </label>
        )}

        {(kind === "video" || kind === "pdf" || kind === "document") && (
          <>
            <label className="block text-sm">
              <span className="text-muted-foreground">Module / section</span>
              <select
                required
                value={moduleId}
                onChange={(e) => setModuleId(e.target.value)}
                className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Select module</option>
                {modules.map((m) => (
                  <option key={m.moduleId} value={m.moduleId}>
                    {m.title}
                  </option>
                ))}
                <option value="__new__">+ New section…</option>
              </select>
            </label>
            {moduleId === "__new__" ? (
              <input
                required
                value={newModuleTitle}
                onChange={(e) => setNewModuleTitle(e.target.value)}
                placeholder="New section title"
                className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
            ) : null}
            <input
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Lesson title"
              className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              required
              type="file"
              accept={kind === "video" ? "video/*" : kind === "pdf" ? "application/pdf" : ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.png,.jpg,.jpeg,.webp"}
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            {uploadPct !== null ? (
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-border">
                <div
                  className="h-full bg-ember transition-all"
                  style={{ width: `${uploadPct}%` }}
                />
              </div>
            ) : null}
          </>
        )}

        {kind === "assignment" ? (
          <>
            <input
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Assignment title"
              className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Instructions for mentees on this course"
              rows={3}
              className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <textarea
              value={modelAnswer}
              onChange={(e) => setModelAnswer(e.target.value)}
              placeholder="Model answer (mentors only)"
              rows={2}
              className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
            <label className="block text-sm">
              <span className="text-muted-foreground">Attach assignment documents (PDF, Word, Excel, PowerPoint, text or images)</span>
              <input type="file" multiple accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.png,.jpg,.jpeg,.webp" onChange={(e) => setAssignmentFiles(Array.from(e.target.files || []))} className="mt-1 block w-full rounded-xl border border-border bg-background px-3 py-2 text-sm" />
            </label>
            {assignmentFiles.length ? <p className="text-xs text-muted-foreground">{assignmentFiles.map((f) => f.name).join(" · ")}</p> : null}
          </>
        ) : null}

        {kind === "timeline" ? (
          <>
            <label className="block text-sm">
              <span className="text-muted-foreground">Module / unit</span>
              <select
                required
                value={timelineModuleId}
                onChange={(e) => {
                  const nextId = e.target.value;
                  setTimelineModuleId(nextId);
                  const selected = modules.find((module) => module.moduleId === nextId);
                  setTimelineReleaseAt(toLocalDateTime(selected?.releaseAt));
                  setTimelineDueAt(toLocalDateTime(selected?.dueAt));
                }}
                className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Select module / unit</option>
                {modules.map((module) => (
                  <option key={module.moduleId} value={module.moduleId}>{module.title}</option>
                ))}
              </select>
            </label>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm">
                <span className="text-muted-foreground">Unlock date</span>
                <input required type="datetime-local" value={timelineReleaseAt} onChange={(e) => setTimelineReleaseAt(e.target.value)} className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-sm" />
              </label>
              <label className="block text-sm">
                <span className="text-muted-foreground">Close date</span>
                <input required type="datetime-local" value={timelineDueAt} onChange={(e) => setTimelineDueAt(e.target.value)} className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-sm" />
              </label>
            </div>
            <p className="text-xs text-muted-foreground">Changing these dates affects every lesson in this unit. Set the unlock date to now or an earlier date to open it immediately.</p>
          </>
        ) : null}

        {kind === "milestone" ? (
          <>
            {!runId ? (
              <p className="text-sm text-ember">
                Create / attach a cohort run in Cohort walkthrough first — milestones need a runId.
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">Using run {runId}</p>
            )}
            {lessons.length > 0 ? (
              <select
                required
                value={lessonIdForMile}
                onChange={(e) => setLessonIdForMile(e.target.value)}
                className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Select lesson</option>
                {lessons.map((l) => (
                  <option key={l.lessonId} value={l.lessonId}>
                    {l.title}
                  </option>
                ))}
              </select>
            ) : (
              <input
                required
                value={lessonIdForMile}
                onChange={(e) => setLessonIdForMile(e.target.value)}
                placeholder="lessonId (add a lesson first)"
                className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
            )}
            <div className="flex flex-wrap gap-2">
              <input
                required
                type="datetime-local"
                value={releaseAt}
                onChange={(e) => setReleaseAt(e.target.value)}
                aria-label="Release at"
                className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
              <input
                type="datetime-local"
                value={dueAt}
                onChange={(e) => setDueAt(e.target.value)}
                aria-label="Due at"
                className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </>
        ) : null}

        <button
          type="submit"
          disabled={busy}
          className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground disabled:opacity-50"
        >
          {busy
            ? uploadPct !== null
              ? `Uploading ${uploadPct}%…`
              : "Saving…"
            : kind === "assignment"
              ? "Publish assignment"
              : kind === "milestone"
                ? "Schedule milestone"
                : kind === "timeline"
                  ? "Update unlock dates"
                  : "Add to course"}
        </button>
        {msg ? <p className="text-sm text-muted-foreground">{msg}</p> : null}
      </form>
    </section>
  );
}
