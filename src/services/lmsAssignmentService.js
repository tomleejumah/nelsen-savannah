/**
 * L3 — mentor/school-admin assigned work.
 */

import crypto from "crypto";
import {
  dbAll,
  dbGet,
  dbRun,
  getPrimaryEngine,
} from "../db/lmsDb.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { canMarkAssignments, DEFAULT_SCHOOL_ID } from "../constants/lmsRoles.js";
import { getActorSchoolId } from "./lmsSchoolService.js";

function newAssignmentId() {
  return `asg_${crypto.randomBytes(8).toString("hex")}`;
}

function mapAssignment(row) {
  return {
    id: row.assignment_id,
    schoolId: row.school_id || null,
    trackId: row.track_id || null,
    lessonId: row.lesson_id || null,
    title: row.title || "",
    prompt: row.prompt || "",
    assignedBy: row.assigned_by,
    assigneeUid: row.assignee_uid || null,
    cohort: row.cohort || null,
    dueAt: row.due_at ? Number(row.due_at) : null,
    createdAt: Number(row.created_at),
  };
}

export async function createAssignment(profile, body = {}) {
  const role = await loadUserRole(profile.uid);
  if (!canMarkAssignments(role)) {
    const err = new Error("Mentor or school admin required");
    err.status = 403;
    throw err;
  }
  const title = String(body.title || "").trim();
  if (!title) {
    const err = new Error("title required");
    err.status = 400;
    throw err;
  }
  if (!body.assigneeUid && !body.trackId && !body.cohort) {
    const err = new Error("assigneeUid, trackId, or cohort required");
    err.status = 400;
    throw err;
  }
  if (body.lessonId) {
    const lesson = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [
      body.lessonId,
    ]);
    if (!lesson) {
      const err = new Error("Lesson not found");
      err.status = 404;
      throw err;
    }
  }

  const schoolId = (await getActorSchoolId(profile.uid)) || DEFAULT_SCHOOL_ID;
  const now = Date.now();
  const assignmentId = newAssignmentId();
  const trackId = body.trackId || null;
  const lessonId = body.lessonId || null;
  const prompt = body.prompt || "";
  const assigneeUid = body.assigneeUid || null;
  const cohort = body.cohort || null;
  const dueAt = body.dueAt ? Number(body.dueAt) : null;

  await dbRun(
    `INSERT INTO assignments (
      assignment_id, school_id, track_id, lesson_id, title, prompt,
      assigned_by, assignee_uid, cohort, due_at, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      assignmentId,
      schoolId,
      trackId,
      lessonId,
      title,
      prompt,
      profile.uid,
      assigneeUid,
      cohort,
      dueAt,
      now,
    ],
  );

  const row = await dbGet("SELECT * FROM assignments WHERE assignment_id = ?", [
    assignmentId,
  ]);
  return {
    source: getPrimaryEngine(),
    data: { assignment: mapAssignment(row) },
  };
}

/** Student inbox: direct assignee OR enrolled on assigned track. */
export async function listMyAssignments(uid) {
  const direct = await dbAll(
    `SELECT * FROM assignments WHERE assignee_uid = ?
     ORDER BY created_at DESC`,
    [uid],
  );
  const enrolled = await dbAll(
    `SELECT a.* FROM assignments a
     JOIN enrollments e ON e.track_id = a.track_id AND e.uid = ?
     WHERE a.assignee_uid IS NULL AND a.track_id IS NOT NULL
     ORDER BY a.created_at DESC`,
    [uid],
  );
  const byId = new Map();
  for (const row of [...direct, ...enrolled]) {
    byId.set(row.assignment_id, mapAssignment(row));
  }
  return {
    source: getPrimaryEngine(),
    data: { assignments: [...byId.values()] },
  };
}

/** Mentor outbox. */
export async function listAssignedByMe(uid) {
  const role = await loadUserRole(uid);
  if (!canMarkAssignments(role)) {
    const err = new Error("Mentor or school admin required");
    err.status = 403;
    throw err;
  }
  const rows = await dbAll(
    `SELECT * FROM assignments WHERE assigned_by = ?
     ORDER BY created_at DESC LIMIT 200`,
    [uid],
  );
  return {
    source: getPrimaryEngine(),
    data: { assignments: rows.map(mapAssignment) },
  };
}
