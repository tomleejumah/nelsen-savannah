/**
 * Course group chats use chatRooms (NOT Reddit-style communities).
 * Membership is always derived from the primary LMS database.
 */
import admin from "../config/firebase.js";
import { dbAll, dbGet } from "../db/lmsDb.js";

const active = (status) => String(status || "").toLowerCase() === "active";
const enrolled = (status) => ["active", "in_progress", "completed", "enrolled"].includes(String(status || "").toLowerCase());

function httpError(message, status = 403) {
  return Object.assign(new Error(message), { status });
}

async function authorizedCourse(uid, trackId) {
  const track = await dbGet("SELECT track_id, title, school_id FROM tracks WHERE track_id = ?", [trackId]);
  if (!track) throw httpError("Course not found", 404);
  if (!track.school_id) throw httpError("Course has no school", 409);
  const mentor = await dbGet("SELECT uid FROM track_mentors WHERE track_id = ? AND uid = ?", [trackId, uid]);
  if (!mentor) throw httpError("Not an assigned course mentor");
  // A school membership check is intentionally required in addition to course assignment.
  const member = await dbGet("SELECT status FROM school_memberships WHERE school_id = ? AND uid = ?", [track.school_id, uid]);
  if (!member || !active(member.status)) throw httpError("Inactive school membership");
  return track;
}

async function eligibleMentees(trackId, schoolId) {
  const rows = await dbAll(
    `SELECT DISTINCT e.uid, u.display_name, e.status AS enrollment_status, sm.status AS membership_status
       FROM enrollments e
       JOIN school_memberships sm ON sm.uid = e.uid AND sm.school_id = ?
       LEFT JOIN users_mirror u ON u.uid = e.uid
      WHERE e.track_id = ? AND e.uid IS NOT NULL`,
    [schoolId, trackId],
  );
  return rows.filter((r) => r.uid && enrolled(r.enrollment_status) && active(r.membership_status));
}

export async function listMentorChatCourses(req, res) {
  try {
    const rows = await dbAll(`SELECT DISTINCT t.track_id AS trackId, t.title
      FROM tracks t JOIN track_mentors tm ON tm.track_id = t.track_id
      JOIN school_memberships sm ON sm.school_id = t.school_id AND sm.uid = tm.uid
      WHERE tm.uid = ? AND sm.status = 'active' ORDER BY t.title`, [req.user.uid]);
    return res.json({ courses: rows });
  } catch (err) {
    console.error("[course-chat-courses]", err);
    return res.status(500).json({ error: "Could not load assigned courses" });
  }
}

export async function getCourseChatCandidates(req, res) {
  try {
    const track = await authorizedCourse(req.user.uid, req.params.trackId);
    const members = await eligibleMentees(track.track_id, track.school_id);
    return res.json({ trackId: track.track_id, members });
  } catch (err) {
    return res.status(err.status || 500).json({ error: err.status ? err.message : "Could not load members" });
  }
}

async function courseParticipants(trackId, schoolId) {
  const mentors = await dbAll(`SELECT DISTINCT tm.uid, u.display_name
    FROM track_mentors tm
    JOIN school_memberships sm ON sm.uid = tm.uid AND sm.school_id = ?
    LEFT JOIN users_mirror u ON u.uid = tm.uid
    WHERE tm.track_id = ? AND sm.status = 'active'`, [schoolId, trackId]);
  const mentees = await eligibleMentees(trackId, schoolId);
  const userNames = {};
  const mentorIds = [];
  for (const mentor of mentors) {
    if (!mentor.uid) continue;
    mentorIds.push(mentor.uid);
    userNames[mentor.uid] = mentor.display_name || "Mentor";
  }
  for (const mentee of mentees) {
    if (mentee.uid && !userNames[mentee.uid]) userNames[mentee.uid] = mentee.display_name || "Mentee";
  }
  return { userIds: Object.keys(userNames), userNames, adminIds: [...new Set(mentorIds)] };
}

/** Refresh an existing room after enrollment or mentor assignment changes.
 * Never creates a chat implicitly; creation remains an assigned mentor action.
 */
export async function syncCourseChatMembership(trackId) {
  const track = await dbGet("SELECT track_id, title, school_id FROM tracks WHERE track_id = ?", [trackId]);
  if (!track?.school_id) return false;
  const room = admin.firestore().collection("chatRooms").doc(`course_${trackId}`);
  const { userIds, userNames, adminIds } = await courseParticipants(trackId, track.school_id);
  return admin.firestore().runTransaction(async tx => {
    const existing = await tx.get(room);
    if (!existing.exists) return false;
    if (existing.get("type") !== "group" || existing.get("courseId") !== trackId) {
      throw httpError("Course chat ID conflict", 409);
    }
    tx.update(room, { name: track.title || "Course chat", userIds, userNames, adminIds });
    return true;
  });
}

/** Periodic repair for missed membership events. No rooms are created. */
export async function reconcileExistingCourseChats() {
  const tracks = await dbAll("SELECT track_id FROM tracks WHERE school_id IS NOT NULL");
  let repaired = 0;
  let failed = 0;
  for (const track of tracks) {
    try {
      if (await syncCourseChatMembership(track.track_id)) repaired++;
    } catch (err) {
      failed++;
      console.warn("[course-chat] reconciliation failed", track.track_id, err.message);
    }
  }
  return { scanned: tracks.length, repaired, failed };
}

export async function createCourseGroupChat(req, res) {
  try {
    const uid = req.user.uid;
    const track = await authorizedCourse(uid, req.params.trackId);
    const { userIds, userNames, adminIds } = await courseParticipants(track.track_id, track.school_id);
    if (!adminIds.includes(uid)) throw httpError("Not an active course mentor");
    if (userIds.length > 400) throw httpError("Course exceeds group chat participant limit", 409);

    // One deterministic document per course. Every assigned mentor opens the same room.
    const roomId = `course_${track.track_id}`;
    const room = admin.firestore().collection("chatRooms").doc(roomId);
    const name = track.title || "Course chat";
    const created = await admin.firestore().runTransaction(async tx => {
      const old = await tx.get(room);
      if (old.exists) {
        if (old.get("courseId") !== track.track_id || old.get("type") !== "group") {
          throw httpError("Chat ID conflict", 409);
        }
        // Reconcile membership when any mentor opens the course chat. A separate
        // enrollment hook is still needed for immediate joins and removals.
        tx.update(room, { name, userIds, userNames, adminIds, schoolId: track.school_id });
        return false;
      }
      tx.create(room, {
        chatroomId: roomId, type: "group", name, courseId: track.track_id,
        schoolId: track.school_id, createdBy: uid, adminIds, userIds, userNames,
        lastMessage: "", lastMessageSenderId: "", unreadCount: {},
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        lastMessageTimestamp: admin.firestore.FieldValue.serverTimestamp(),
      });
      return true;
    });
    return res.status(created ? 201 : 200).json({ chatroomId: roomId, created, name, memberCount: userIds.length });
  } catch (err) {
    console.error("[course-group-chat]", err);
    return res.status(err.status || 500).json({ error: err.status ? err.message : "Could not create course chat" });
  }
}
