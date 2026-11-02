/**
 * Course group chats use chatRooms (NOT Reddit-style communities).
 * Membership is always derived from the primary LMS database.
 */
import admin from "../config/firebase.js";
import { dbAll, dbGet } from "../db/lmsDb.js";

const active = (status) => !["cancelled", "canceled", "removed", "revoked", "withdrawn"].includes(String(status || "").toLowerCase());

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
  const member = await dbGet("SELECT status FROM school_members WHERE school_id = ? AND uid = ?", [track.school_id, uid]);
  if (!member || !active(member.status)) throw httpError("Inactive school membership");
  return track;
}

async function eligibleMentees(trackId, schoolId) {
  const rows = await dbAll(
    `SELECT DISTINCT e.uid, u.display_name
       FROM enrollments e
       JOIN school_members sm ON sm.uid = e.uid AND sm.school_id = ?
       LEFT JOIN users_mirror u ON u.uid = e.uid
      WHERE e.track_id = ? AND e.uid IS NOT NULL`,
    [schoolId, trackId],
  );
  return rows.filter((r) => r.uid);
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

export async function createCourseGroupChat(req, res) {
  try {
    const uid = req.user.uid;
    const track = await authorizedCourse(uid, req.params.trackId);
    const name = String(req.body?.name || `${track.title} chat`).trim();
    if (!name || name.length > 100) throw httpError("Group name must be 1–100 characters", 400);
    const requested = req.body?.memberIds;
    if (requested !== undefined && (!Array.isArray(requested) || requested.length > 400 || requested.some(x => typeof x !== "string"))) {
      throw httpError("Invalid memberIds", 400);
    }
    const members = await eligibleMentees(track.track_id, track.school_id);
    const eligible = new Set(members.map(m => m.uid));
    const selected = requested === undefined ? [...eligible] : [...new Set(requested)];
    if (selected.some(id => !eligible.has(id))) throw httpError("Some members are not enrolled in this course");
    const userIds = [...new Set([uid, ...selected])];
    if (userIds.length < 2) throw httpError("Select at least one enrolled mentee", 400);
    // Stable ID makes retries safe. One course chat per mentor; no duplicate on repeated taps.
    const roomId = `course_${track.track_id}_${uid}`;
    const room = admin.firestore().collection("chatRooms").doc(roomId);
    const created = await admin.firestore().runTransaction(async tx => {
      const old = await tx.get(room);
      if (old.exists) {
        if (old.get("createdBy") !== uid || old.get("courseId") !== track.track_id) throw httpError("Chat ID conflict", 409);
        return false;
      }
      tx.create(room, {
        chatroomId: roomId, type: "group", name, courseId: track.track_id,
        schoolId: track.school_id, createdBy: uid, adminIds: [uid], userIds,
        userNames: Object.fromEntries([[uid, req.user.displayName || "Mentor"], ...members.filter(m => selected.includes(m.uid)).map(m => [m.uid, m.display_name || "Mentee"])]),
        lastMessage: "", lastMessageSenderId: "", unreadCount: {},
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        lastMessageTimestamp: admin.firestore.FieldValue.serverTimestamp(),
      });
      return true;
    });
    return res.status(created ? 201 : 200).json({ chatroomId: roomId, created });
  } catch (err) {
    console.error("[course-group-chat]", err);
    return res.status(err.status || 500).json({ error: err.status ? err.message : "Could not create course chat" });
  }
}
