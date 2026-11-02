import admin from "../config/firebase.js";
import { dbAll, dbGet } from "../db/lmsDb.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { isSuperAdmin, normalizeRole, ROLES } from "../constants/lmsRoles.js";

const roomId = id => `school_announcements_${id}`;
const validId = id => typeof id === "string" && /^[a-zA-Z0-9_-]{1,100}$/.test(id);
const fail = (message, status = 403) => Object.assign(new Error(message), { status });

async function participants(schoolId) {
  const school = await dbGet("SELECT school_id, name FROM schools WHERE school_id = ?", [schoolId]);
  if (!school) throw fail("School not found", 404);
  const members = await dbAll(
    `SELECT sm.uid, COALESCE(NULLIF(sm.display_name, ''), u.display_name) AS display_name, sm.role
     FROM school_memberships sm LEFT JOIN users_mirror u ON u.uid = sm.uid
     WHERE sm.school_id = ? AND sm.status = 'active'`, [schoolId]);
  const userIds = [...new Set(members.map(m => m.uid).filter(Boolean))];
  const userNames = Object.fromEntries(members.filter(m => m.uid).map(m => [m.uid, m.display_name || "Member"]));
  const adminIds = members.filter(m => normalizeRole(m.role) === ROLES.SchoolAdmin).map(m => m.uid);
  return { school, userIds, userNames, adminIds };
}

export async function syncSchoolAnnouncementMembership(schoolId) {
  if (!validId(schoolId)) throw fail("Invalid school ID", 400);
  const { school, userIds, userNames, adminIds } = await participants(schoolId);
  const ref = admin.firestore().collection("chatRooms").doc(roomId(schoolId));
  return admin.firestore().runTransaction(async tx => {
    const doc = await tx.get(ref);
    if (!doc.exists) return false;
    if (doc.get("type") !== "system" || doc.get("schoolId") !== schoolId)
      throw fail("Announcement room conflict", 409);
    tx.update(ref, { name: `${school.name} announcements`, userIds, userNames, adminIds });
    return true;
  });
}

export async function reconcileSchoolAnnouncements() {
  const schools = await dbAll("SELECT school_id FROM schools");
  let repaired = 0, failed = 0;
  for (const { school_id } of schools) {
    try { if (await syncSchoolAnnouncementMembership(school_id)) repaired++; }
    catch (err) { failed++; console.warn("[school-announcements] sync failed", school_id, err.message); }
  }
  return { scanned: schools.length, repaired, failed };
}

export async function listMySchoolAnnouncements(req, res) {
  try {
    const rows = await dbAll(
      `SELECT s.school_id AS schoolId, s.name FROM school_memberships sm
       JOIN schools s ON s.school_id = sm.school_id
       WHERE sm.uid = ? AND sm.status = 'active' ORDER BY s.name`, [req.user.uid]);
    return res.json({ schools: rows.map(s => ({ ...s, chatroomId: roomId(s.schoolId) })) });
  } catch (err) {
    console.error("[school-announcements] list failed", err);
    return res.status(500).json({ error: "Unable to list announcements" });
  }
}

export async function openSchoolAnnouncements(req, res) {
  const schoolId = req.params.schoolId;
  if (!validId(schoolId)) return res.status(400).json({ error: "Invalid school ID" });
  try {
    const member = await dbGet(
      "SELECT role FROM school_memberships WHERE school_id = ? AND uid = ? AND status = 'active'",
      [schoolId, req.user.uid]);
    if (!member) throw fail("Active school membership required");
    const { school, userIds, userNames, adminIds } = await participants(schoolId);
    const role = await loadUserRole(req.user.uid);
    if (normalizeRole(member.role) !== ROLES.SchoolAdmin && !isSuperAdmin(role))
      throw fail("School admin required to create announcements");
    const ref = admin.firestore().collection("chatRooms").doc(roomId(schoolId));
    const created = await admin.firestore().runTransaction(async tx => {
      const existing = await tx.get(ref);
      if (existing.exists) {
        if (existing.get("schoolId") !== schoolId || existing.get("type") !== "system")
          throw fail("Announcement room conflict", 409);
        tx.update(ref, { name: `${school.name} announcements`, userIds, userNames, adminIds });
        return false;
      }
      tx.create(ref, {
        chatroomId: roomId(schoolId), type: "system", schoolId,
        name: `${school.name} announcements`, createdBy: req.user.uid,
        userIds, userNames, adminIds, lastMessage: "", lastMessageSenderId: "",
        unreadCount: {}, createdAt: admin.firestore.FieldValue.serverTimestamp(),
        lastMessageTimestamp: admin.firestore.FieldValue.serverTimestamp(),
      });
      return true;
    });
    return res.status(created ? 201 : 200).json({ chatroomId: ref.id, created, memberCount: userIds.length });
  } catch (err) {
    console.error("[school-announcements] open failed", err);
    return res.status(err.status || 500).json({ error: err.status ? err.message : "Unable to open announcements" });
  }
}
