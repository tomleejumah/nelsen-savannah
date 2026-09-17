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

export function CatalogCmsPanel({
  user,
  schoolId,
  selectedTrackId,
  selectedTrackTitle,
  /** Only Admin / SchoolAdmin may create whole new courses. */
  allowCreateTrack = false,
}: {
  user: User;
  schoolId?: string;
  selectedTrackId?: string;
  selectedTrackTitle?: string;
  allowCreateTrack?: boolean;
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

  const lockedCourse = Boolean(selectedTrackId);
  const courseLabel = selectedTrackTitle?.trim() || selectedTrackId || "";

  useEffect(() => {
    if (selectedTrackId) {
      setEditTrackId(selectedTrackId);
      setModuleTrackId(selectedTrackId);
      setLessonTrackId(selectedTrackId);
    }
  }, [selectedTrackId]);

  useEffect(() => {
    if (selectedTrackTitle) {
      setEditTitle(selectedTrackTitle);
    }
  }, [selectedTrackTitle]);

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
    const result = await adminCreateModule(token, {
      moduleId: moduleId.trim(),
      trackId: moduleTrackId.trim(),
      title: moduleTitle.trim(),
    });
    setMsg(result.ok ? `Module ${moduleId} created` : result.error || "Failed");
    if (result.ok) {
      setLessonModuleId(moduleId.trim());
      setLessonTrackId(moduleTrackId.trim());
    }
  }

  async function createLesson(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await adminCreateLesson(token, {
      lessonId: lessonId.trim(),
      moduleId: lessonModuleId.trim(),
      trackId: lessonTrackId.trim(),
      title: lessonTitle.trim(),
      type: lessonType,
      hasQuiz: lessonType === "quiz",
      hasAssignment: lessonType === "assignment",
    });
    setMsg(result.ok ? `Lesson ${lessonId} created` : result.error || "Failed");
    if (result.ok && lessonType === "video") setMediaLessonId(lessonId.trim());
  }

  async function uploadMedia(e: React.FormEvent) {
    e.preventDefault();
    if (!mediaFile) return;
    setUploadMsg(null);
    setUploadPct(0);
    try {
      const token = await user.getIdToken();
      const media = await uploadLessonMedia(token, {
        lessonId: mediaLessonId.trim(),
        file: mediaFile,
        onProgress: setUploadPct,
      });
      setUploadMsg(
        `Attached ${media.mediaId} to ${mediaLessonId.trim()} (${Math.round(media.sizeBytes / 1024 / 1024)} MB)`,
      );
      setMediaFile(null);
    } catch (err) {
      setUploadMsg(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setUploadPct(null);
    }
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

      {lockedCourse ? (
        <div className="rounded-xl border border-border/70 bg-background/60 px-4 py-3">
          <p className="text-xs text-muted-foreground">Course</p>
          <p className="font-display text-lg font-semibold">{courseLabel}</p>
          <p className="mt-0.5 font-mono text-xs text-muted-foreground">{editTrackId}</p>
        </div>
      ) : null}

      <form onSubmit={(e) => void updateTrack(e)} className="space-y-2">
        <h3 className="font-medium">Update track</h3>
        <div className="flex flex-wrap gap-2">
          {lockedCourse ? null : (
            <input
              required
              value={editTrackId}
              onChange={(e) => setEditTrackId(e.target.value)}
              placeholder="trackId"
              className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
          )}
          <input
            value={editTitle}
            onChange={(e) => setEditTitle(e.target.value)}
            placeholder="Course title"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={editBlurb}
            onChange={(e) => setEditBlurb(e.target.value)}
            placeholder="Blurb"
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
            value={moduleId}
            onChange={(e) => setModuleId(e.target.value)}
            placeholder="moduleId"
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          {lockedCourse ? null : (
            <input
              required
              value={moduleTrackId}
              onChange={(e) => setModuleTrackId(e.target.value)}
              placeholder="trackId"
              className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
          )}
          <input
            required
            value={moduleTitle}
            onChange={(e) => setModuleTitle(e.target.value)}
            placeholder="Title"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
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
            value={lessonId}
            onChange={(e) => setLessonId(e.target.value)}
            placeholder="lessonId"
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            required
            value={lessonModuleId}
            onChange={(e) => setLessonModuleId(e.target.value)}
            placeholder="moduleId"
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          {lockedCourse ? null : (
            <input
              required
              value={lessonTrackId}
              onChange={(e) => setLessonTrackId(e.target.value)}
              placeholder="trackId"
              className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
            />
          )}
          <input
            required
            value={lessonTitle}
            onChange={(e) => setLessonTitle(e.target.value)}
            placeholder="Title"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <select
            value={lessonType}
            onChange={(e) => setLessonType(e.target.value)}
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="read">read</option>
            <option value="video">video</option>
            <option value="quiz">quiz</option>
            <option value="assignment">assignment</option>
          </select>
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
          <input
            required
            value={mediaLessonId}
            onChange={(e) => setMediaLessonId(e.target.value)}
            placeholder="lessonId"
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
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
      </form>
    </div>
  );
}
