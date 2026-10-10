import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { CatalogCmsPanel } from "@/components/lms/CatalogCmsPanel";
import { CohortIntakesPanel } from "@/components/lms/CohortIntakesPanel";
import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { SchoolAdminChrome } from "@/components/lms/schoolAdmin/SchoolAdminChrome";
import { useSchoolAdmin } from "@/components/lms/schoolAdmin/useSchoolAdmin";
import { adminDeleteUnusedTracks, type MeDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/school/coursework")({
  head: () => ({
    meta: [{ title: "Courses — School admin" }],
  }),
  component: CourseworkPage,
});

function CourseworkPage() {
  return (
    <RoleShellPage
      shell="school"
      title="Courses"
      blurb="Catalog → course price → cohorts and cohort tools."
      wide
    >
      {({ user, me }) => <CourseworkConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function CourseworkConsole({ user, me }: { user: User; me: MeDto }) {
  const a = useSchoolAdmin(user, me);
  const [selectedForRemoval, setSelectedForRemoval] = useState<string[]>([]);
  const [removing, setRemoving] = useState(false);
  const schoolTracks = a.tracks.filter((track) => track.schoolId === a.schoolId);

  async function removeUnusedCourses() {
    if (removing || !selectedForRemoval.length) return;
    if (!window.confirm(
      `Permanently remove up to ${selectedForRemoval.length} selected unused courses?\n\n` +
      "Only courses WITHOUT lessons, enrollments, submissions, mentors, media, payment or live-event records can be deleted. Others will be preserved. This cannot be undone.",
    )) return;
    setRemoving(true);
    a.setMsg(null);
    try {
      const token = await user.getIdToken();
      const result = await adminDeleteUnusedTracks(token, selectedForRemoval);
      if (!result.ok || !result.data) {
        a.setMsg(result.error || "Course removal failed");
        return;
      }
      const { deleted, blocked } = result.data;
      const unsynced = deleted.filter((item) => !item.mirrorSynced).length;
      a.setMsg(
        `Removed ${deleted.length}; preserved ${blocked.length} with existing records.` +
        (unsynced ? ` ${unsynced} removed courses need RTDB mirror reconciliation.` : "") +
        (blocked.length ? ` First blocker: ${blocked[0].reason}.` : ""),
      );
      setSelectedForRemoval(blocked.map((item) => item.trackId));
      await a.load();
    } catch (error) {
      a.setMsg(error instanceof Error ? error.message : "Course removal failed");
    } finally {
      setRemoving(false);
    }
  }

  return (
    <SchoolAdminChrome
      title="Courses"
      blurb="Create courses, set price, then nest cohort tools under a selected course."
      msg={a.msg}
    >
      {a.error ? (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {a.error}
        </p>
      ) : null}

      {schoolTracks.length > 0 ? (
        <section className="rounded-2xl border border-border p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-semibold">Clean up accidental courses</h2>
              <p className="text-xs text-muted-foreground">
                Select one or more courses. Deletion is blocked automatically
                for any course with learning, mentor, media or payment records.
              </p>
            </div>
            <button
              type="button"
              disabled={!selectedForRemoval.length || removing}
              onClick={() => void removeUnusedCourses()}
              className="rounded-lg border border-destructive/50 px-3 py-2 text-sm text-destructive disabled:cursor-not-allowed disabled:opacity-40"
            >
              {removing ? "Checking courses…" : `Delete unused selected (${selectedForRemoval.length})`}
            </button>
          </div>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            {schoolTracks.map((track) => (
              <label key={track.trackId} className="flex min-w-0 cursor-pointer items-center gap-2 rounded-lg border border-border/60 px-3 py-2 text-sm">
                <input
                  type="checkbox"
                  aria-label={`Select ${track.courseTitle}`}
                  checked={selectedForRemoval.includes(track.trackId)}
                  onChange={(event) => setSelectedForRemoval((previous) =>
                    event.target.checked
                      ? [...new Set([...previous, track.trackId])]
                      : previous.filter((id) => id !== track.trackId)
                  )}
                />
                <span className="min-w-0 truncate" title={track.courseTitle}>{track.courseTitle}</span>
              </label>
            ))}
          </div>
        </section>
      ) : null}

      <section>
        <CatalogCmsPanel user={user} schoolId={a.schoolId} allowCreateTrack onChanged={a.load} />
      </section>

      <CohortIntakesPanel
        user={user}
        schoolId={a.schoolId}
        tracks={a.tracks}
        mentees={a.mentees}
      />
    </SchoolAdminChrome>
  );
}
