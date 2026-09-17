/**
 * Catalog CMS — mentors update existing tracks; admins create new courses via dialog.
 */
import { useEffect, useState } from "react";
import type { User } from "firebase/auth";

import {
  adminCreateLesson,
  adminCreateModule,
  adminCreateTrack,
  adminUpdateTrack,
  fetchAdminStats,
  fetchLmsModule,
  fetchLmsTrack,
  uploadLessonMedia,
  type AdminStatsDto,
} from "@/lib/lmsApi";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";

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
  if (type === "quiz") return "quiz";
  if (type === "assignment") return "asgn";
  return "read";
}

type AttachedMedia = {
  filename: string;
  mediaId: string;
  lessonId: string;
};

export function CatalogCmsPanel({
  user,
  schoolId,
  selectedTrackId,
  /** Only Admin / SchoolAdmin may create whole new courses. */
  allowCreateTrack = false,
  lessons,
  modules,
}: {
  user: User;
  schoolId?: string;
  selectedTrackId?: string;
  allowCreateTrack?: boolean;
  lessons?: { lessonId: string; title: string }[];
  modules?: { moduleId: string; title: string }[];
}) {
  const [msg, setMsg] = useState<string | null>(null);
  const [stats, setStats] = useState<AdminStatsDto | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [advancedIds, setAdvancedIds] = useState(false);
  const [trackId, setTrackId] = useState("");
  const [trackTitle, setTrackTitle] = useState("");
  const [editTrackId, setEditTrackId] = useState("");
  const [editTitle, setEditTitle] = useState("");
  const [editBlurb, setEditBlurb] = useState("");
  const [editPublished, setEditPublished] = useState(true);
  const [moduleId, setModuleId] = useState("");
  const [moduleTrackId, setModuleTrackId] = useState("");
  const [moduleTitle, setModuleTitle] = useState("");
  const [lessonId, setLessonId] = useState("");
  const [lessonModuleId, setLessonModuleId] = useState("");
  const [lessonTrackId, setLessonTrackId] = useState("");
  const [lessonTitle, setLessonTitle] = useState("");
  const [lessonType, setLessonType] = useState("read");
  const [mediaLessonId, setMediaLessonId] = useState("");
  const [mediaFile, setMediaFile] = useState<File | null>(null);
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const [uploadMsg, setUploadMsg] = useState<string | null>(null);
  const [attachedMedia, setAttachedMedia] = useState<AttachedMedia[]>([]);
  const [fetchedModules, setFetchedModules] = useState<
    { moduleId: string; title: string }[]
  >([]);
  const [fetchedLessons, setFetchedLessons] = useState<
    { lessonId: string; title: string }[]
  >([]);

  const moduleOptions = modules?.length ? modules : fetchedModules;
  const lessonOptions = lessons?.length ? lessons : fetchedLessons;

  useEffect(() => {
    if (selectedTrackId) {
      setEditTrackId(selectedTrackId);
      setModuleTrackId(selectedTrackId);
      setLessonTrackId(selectedTrackId);
    }
  }, [selectedTrackId]);

  useEffect(() => {
    const tid = selectedTrackId || editTrackId;
    if (!tid || (lessons && lessons.length > 0 && modules && modules.length > 0)) {
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const token = await user.getIdToken();
        const envelope = await fetchLmsTrack(token, tid);
        if (cancelled || !envelope.ok || !envelope.data) return;
        const mods = envelope.data.modules || [];
        if (!modules?.length) {
          setFetchedModules(
            mods.map((m) => ({ moduleId: m.moduleId, title: m.title })),
          );
        }
        if (!lessons?.length) {
          const rows: { lessonId: string; title: string }[] = [];
          await Promise.all(
            mods.map(async (m) => {
              const modEnv = await fetchLmsModule(token, m.moduleId);
              for (const l of modEnv.data?.lessons || []) {
                rows.push({ lessonId: l.lessonId, title: l.title });
              }
            }),
          );
          if (!cancelled) setFetchedLessons(rows);
        }
      } catch {
        /* picker falls back to text inputs */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedTrackId, editTrackId, user, lessons, modules]);

  function onModuleTitleChange(title: string) {
    setModuleTitle(title);
    if (!advancedIds) setModuleId(slugId("mod", title));
  }

  function onLessonTitleChange(title: string) {
    setLessonTitle(title);
    if (!advancedIds) setLessonId(slugId(lessonSlugPrefix(lessonType), title));
  }

  function onLessonTypeChange(type: string) {
    setLessonType(type);
    if (!advancedIds && lessonTitle.trim()) {
      setLessonId(slugId(lessonSlugPrefix(type), lessonTitle));
    }
  }

  async function loadStats() {
    const token = await user.getIdToken();
    const envelope = await fetchAdminStats(token);
    if (envelope.ok && envelope.data) setStats(envelope.data);
  }

  async function createTrack(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await adminCreateTrack(token, {
      trackId: trackId.trim(),
      title: trackTitle.trim(),
      ...(schoolId ? { schoolId } : {}),
    });
    setMsg(result.ok ? `Track ${trackId} published` : result.error || "Failed");
    if (result.ok) {
      setModuleTrackId(trackId.trim());
      setLessonTrackId(trackId.trim());
      setEditTrackId(trackId.trim());
      setTrackId("");
      setTrackTitle("");
      setCreateOpen(false);
      void loadStats();
    }
  }

  async function updateTrack(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const body: { title?: string; blurb?: string; published: boolean } = {
      published: editPublished,
    };
    if (editTitle.trim()) body.title = editTitle.trim();
    if (editBlurb.trim()) body.blurb = editBlurb.trim();
    const result = await adminUpdateTrack(token, editTrackId.trim(), body);
    setMsg(result.ok ? `Track ${editTrackId} updated` : result.error || "Failed");
    if (result.ok) void loadStats();
  }

  async function createModule(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const id = moduleId.trim() || slugId("mod", moduleTitle);
    const result = await adminCreateModule(token, {
      moduleId: id,
      trackId: moduleTrackId.trim(),
      title: moduleTitle.trim(),
    });
    setMsg(result.ok ? `Module ${id} created` : result.error || "Failed");
    if (result.ok) {
      setLessonModuleId(id);
      setLessonTrackId(moduleTrackId.trim());
    }
  }

  async function createLesson(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const id = lessonId.trim() || slugId(lessonSlugPrefix(lessonType), lessonTitle);
    const result = await adminCreateLesson(token, {
      lessonId: id,
      moduleId: lessonModuleId.trim(),
      trackId: lessonTrackId.trim(),
      title: lessonTitle.trim(),
      type: lessonType,
      hasQuiz: lessonType === "quiz",
      hasAssignment: lessonType === "assignment",
    });
    setMsg(result.ok ? `Lesson ${id} created` : result.error || "Failed");
    if (result.ok && lessonType === "video") setMediaLessonId(id);
  }

  async function uploadMedia(e: React.FormEvent) {
    e.preventDefault();
    if (!mediaFile) return;
    setUploadMsg(null);
    setUploadPct(0);
    const filename = mediaFile.name;
    const lesson = mediaLessonId.trim();
    try {
      const token = await user.getIdToken();
      const media = await uploadLessonMedia(token, {
        lessonId: lesson,
        file: mediaFile,
        onProgress: setUploadPct,
      });
      setUploadMsg(
        `Attached ${media.mediaId} to ${lesson} (${Math.round(media.sizeBytes / 1024 / 1024)} MB)`,
      );
      setAttachedMedia((prev) => [
        { filename, mediaId: media.mediaId, lessonId: lesson },
        ...prev,
      ]);
      setMediaFile(null);
    } catch (err) {
      setUploadMsg(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setUploadPct(null);
    }
  }

  function modulePicker() {
    if (moduleOptions.length > 0) {
      return (
        <select
          required
          value={lessonModuleId}
          onChange={(e) => setLessonModuleId(e.target.value)}
          className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
        >
          <option value="">Select module</option>
          {moduleOptions.map((m) => (
            <option key={m.moduleId} value={m.moduleId}>
              {m.title}
            </option>
          ))}
        </select>
      );
    }
    if (advancedIds) {
      return (
        <input
          required
          value={lessonModuleId}
          onChange={(e) => setLessonModuleId(e.target.value)}
          placeholder="moduleId"
          className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
      );
    }
    return (
      <p className="text-xs text-muted-foreground">
        Add a module first, then pick it for the lesson.
      </p>
    );
  }

  function lessonPicker() {
    if (lessonOptions.length > 0) {
      return (
        <select
          required
          value={mediaLessonId}
          onChange={(e) => setMediaLessonId(e.target.value)}
          className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
        >
          <option value="">Select lesson</option>
          {lessonOptions.map((l) => (
            <option key={l.lessonId} value={l.lessonId}>
              {l.title}
            </option>
          ))}
        </select>
      );
    }
    return (
      <input
        required
        value={mediaLessonId}
        onChange={(e) => setMediaLessonId(e.target.value)}
        placeholder="lessonId"
        className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
      />
    );
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-display text-xl font-semibold">
          {allowCreateTrack ? "Catalog CMS" : "Update course"}
        </h2>
        <div className="flex flex-wrap gap-2">
          {allowCreateTrack ? (
            <Dialog open={createOpen} onOpenChange={setCreateOpen}>
              <DialogTrigger asChild>
                <button
                  type="button"
                  className="rounded-full bg-ember-gradient px-4 py-1.5 text-sm font-semibold text-maroon-foreground"
                >
                  New course
                </button>
              </DialogTrigger>
              <DialogContent className="sm:max-w-md">
                <DialogHeader>
                  <DialogTitle>Publish a new course</DialogTitle>
                  <DialogDescription>
                    Creates an empty track shell. Mentors fill modules and lessons
                    later from Teach — they cannot create whole courses.
                  </DialogDescription>
                </DialogHeader>
                <form onSubmit={(e) => void createTrack(e)} className="mt-4 space-y-3">
                  <input
                    required
                    value={trackId}
                    onChange={(e) => setTrackId(e.target.value)}
                    placeholder="trackId (e.g. track-machine-learning)"
                    className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                  />
                  <input
                    required
                    value={trackTitle}
                    onChange={(e) => setTrackTitle(e.target.value)}
                    placeholder="Course title"
                    className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                  />
                  <button
                    type="submit"
                    className="w-full rounded-full bg-ember-gradient px-4 py-2 text-sm font-semibold text-maroon-foreground"
                  >
                    Publish course
                  </button>
                </form>
              </DialogContent>
            </Dialog>
          ) : null}
          <button
            type="button"
            onClick={() => void loadStats()}
            className="rounded-full border border-border px-4 py-1.5 text-sm"
          >
            Refresh stats
          </button>
        </div>
      </div>
      {msg ? <p className="text-sm text-ember">{msg}</p> : null}
      {stats ? (
        <p className="text-sm text-muted-foreground">
          Enrollments {stats.enrollmentsTotal} · Avg {stats.avgTrackPercent}% ·
          Completions (30d) {stats.completions30d}
        </p>
      ) : null}
      {!allowCreateTrack ? (
        <p className="text-xs text-muted-foreground">
          New whole courses are created by admins. Here you update an existing
          track and add modules, lessons, and media.
        </p>
      ) : null}

      <label className="inline-flex items-center gap-2 text-sm text-muted-foreground">
        <input
          type="checkbox"
          checked={advancedIds}
          onChange={(e) => setAdvancedIds(e.target.checked)}
        />
        Advanced: edit ids
      </label>

      <form onSubmit={(e) => void updateTrack(e)} className="space-y-2">
        <h3 className="font-medium">Update track</h3>
        <div className="flex flex-wrap gap-2">
          {advancedIds ? (
            <input
              required
              value={editTrackId}
              onChange={(e) => setEditTrackId(e.target.value)}
              placeholder="trackId"
              className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
          ) : null}
          <input
            value={editTitle}
            onChange={(e) => setEditTitle(e.target.value)}
            placeholder="New title"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={editBlurb}
            onChange={(e) => setEditBlurb(e.target.value)}
            placeholder="Description"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <label className="inline-flex items-center gap-2 text-sm text-muted-foreground">
            <input
              type="checkbox"
              checked={editPublished}
              onChange={(e) => setEditPublished(e.target.checked)}
            />
            Published
          </label>
          <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
            Save
          </button>
        </div>
      </form>

      <form onSubmit={(e) => void createModule(e)} className="space-y-2">
        <h3 className="font-medium">New module</h3>
        <div className="flex flex-wrap gap-2">
          <input
            required
            value={moduleTitle}
            onChange={(e) => onModuleTitleChange(e.target.value)}
            placeholder="Title"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          {advancedIds ? (
            <>
              <input
                value={moduleId}
                onChange={(e) => setModuleId(e.target.value)}
                placeholder="moduleId"
                className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
              <input
                required
                value={moduleTrackId}
                onChange={(e) => setModuleTrackId(e.target.value)}
                placeholder="trackId"
                className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
            </>
          ) : null}
          <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
            Add module
          </button>
        </div>
      </form>

      <form onSubmit={(e) => void createLesson(e)} className="space-y-2">
        <h3 className="font-medium">New lesson</h3>
        <div className="flex flex-wrap gap-2">
          <input
            required
            value={lessonTitle}
            onChange={(e) => onLessonTitleChange(e.target.value)}
            placeholder="Title"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          {modulePicker()}
          <select
            value={lessonType}
            onChange={(e) => onLessonTypeChange(e.target.value)}
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="read">read</option>
            <option value="video">video</option>
            <option value="quiz">quiz</option>
            <option value="assignment">assignment</option>
          </select>
          {advancedIds ? (
            <>
              <input
                value={lessonId}
                onChange={(e) => setLessonId(e.target.value)}
                placeholder="lessonId"
                className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
              {!moduleOptions.length ? (
                <input
                  required
                  value={lessonModuleId}
                  onChange={(e) => setLessonModuleId(e.target.value)}
                  placeholder="moduleId"
                  className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
                />
              ) : null}
              <input
                required
                value={lessonTrackId}
                onChange={(e) => setLessonTrackId(e.target.value)}
                placeholder="trackId"
                className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
              />
            </>
          ) : null}
          <button type="submit" className="rounded-full border border-border px-4 py-2 text-sm">
            Add lesson
          </button>
        </div>
      </form>

      <form onSubmit={(e) => void uploadMedia(e)} className="space-y-2">
        <h3 className="font-medium">Lesson media</h3>
        <p className="text-sm text-muted-foreground">
          The file goes straight to private storage. Learners only ever receive a
          short-lived signed link.
        </p>
        <div className="flex flex-wrap items-center gap-2">
          {lessonPicker()}
          <input
            required
            type="file"
            accept="video/*,audio/*,application/pdf"
            onChange={(e) => setMediaFile(e.target.files?.[0] ?? null)}
            className="min-w-[12rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={uploadPct !== null || !mediaFile}
            className="rounded-full border border-border px-4 py-2 text-sm disabled:opacity-50"
          >
            {uploadPct !== null ? `Uploading ${uploadPct}%` : "Upload media"}
          </button>
        </div>
        {uploadPct !== null ? (
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-border">
            <div
              className="h-full bg-ember transition-all"
              style={{ width: `${uploadPct}%` }}
            />
          </div>
        ) : null}
        {uploadMsg ? <p className="text-sm">{uploadMsg}</p> : null}
        {attachedMedia.length > 0 ? (
          <ul className="space-y-1 rounded-xl border border-border/60 bg-background/50 px-3 py-2 text-xs text-muted-foreground">
            {attachedMedia.map((row, i) => (
              <li key={`${row.mediaId}-${i}`}>
                <span className="font-medium text-foreground">{row.filename}</span>
                {` · ${row.mediaId} · lesson ${row.lessonId}`}
              </li>
            ))}
          </ul>
        ) : null}
      </form>
    </div>
  );
}
