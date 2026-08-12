/**
 * Shared catalog CMS forms for School Admin + Super Admin (L5).
 */
import { useState } from "react";
import type { User } from "firebase/auth";

import {
  adminCreateLesson,
  adminCreateModule,
  adminCreateTrack,
  fetchAdminStats,
  type AdminStatsDto,
} from "@/lib/lmsApi";

export function CatalogCmsPanel({
  user,
  schoolId,
}: {
  user: User;
  schoolId?: string;
}) {
  const [msg, setMsg] = useState<string | null>(null);
  const [stats, setStats] = useState<AdminStatsDto | null>(null);
  const [trackId, setTrackId] = useState("");
  const [trackTitle, setTrackTitle] = useState("");
  const [moduleId, setModuleId] = useState("");
  const [moduleTrackId, setModuleTrackId] = useState("");
  const [moduleTitle, setModuleTitle] = useState("");
  const [lessonId, setLessonId] = useState("");
  const [lessonModuleId, setLessonModuleId] = useState("");
  const [lessonTrackId, setLessonTrackId] = useState("");
  const [lessonTitle, setLessonTitle] = useState("");
  const [lessonType, setLessonType] = useState("read");

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
      schoolId,
    });
    setMsg(result.ok ? `Track ${trackId} published` : result.error || "Failed");
    if (result.ok) {
      setModuleTrackId(trackId.trim());
      setLessonTrackId(trackId.trim());
      void loadStats();
    }
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
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-display text-xl font-semibold">Catalog CMS</h2>
        <button
          type="button"
          onClick={() => void loadStats()}
          className="rounded-full border border-border px-4 py-1.5 text-sm"
        >
          Refresh stats
        </button>
      </div>
      {msg ? <p className="text-sm text-ember">{msg}</p> : null}
      {stats ? (
        <p className="text-sm text-muted-foreground">
          Enrollments {stats.enrollmentsTotal} · Avg {stats.avgTrackPercent}% ·
          Completions (30d) {stats.completions30d}
        </p>
      ) : null}

      <form onSubmit={(e) => void createTrack(e)} className="space-y-2">
        <h3 className="font-medium">New track</h3>
        <div className="flex flex-wrap gap-2">
          <input
            required
            value={trackId}
            onChange={(e) => setTrackId(e.target.value)}
            placeholder="trackId"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            required
            value={trackTitle}
            onChange={(e) => setTrackTitle(e.target.value)}
            placeholder="Title"
            className="min-w-[8rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
          <button
            type="submit"
            className="rounded-full bg-ember-gradient px-4 py-2 text-sm font-semibold text-maroon-foreground"
          >
            Publish
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
          <input
            required
            value={moduleTrackId}
            onChange={(e) => setModuleTrackId(e.target.value)}
            placeholder="trackId"
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
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
          <input
            required
            value={lessonTrackId}
            onChange={(e) => setLessonTrackId(e.target.value)}
            placeholder="trackId"
            className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
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
    </div>
  );
}
