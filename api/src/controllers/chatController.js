import admin from '../config/firebase.js';
import { sendFCMNotification } from '../services/fcmService.js';
import { dbAll } from '../db/lmsDb.js';

export const sendChatMessageNotification = async (req, res) => {
  const uid = req.user.uid;
  const { receiverId, messageText, conversationId } = req.body || {};
  if (typeof messageText !== "string" || !messageText.trim() || messageText.length > 4000 ||
      typeof conversationId !== "string" || !conversationId || conversationId.length > 250) {
    return res.status(400).json({ error: "Invalid messageText or conversationId" });
  }
  try {
    const roomSnap = await admin.firestore().collection("chatRooms").doc(conversationId).get();
    if (!roomSnap.exists) return res.status(404).json({ error: "Conversation not found" });
    const room = roomSnap.data();
    const participants = Array.isArray(room.userIds) ? room.userIds : [];
    if (!participants.includes(uid)) return res.status(403).json({ error: "Not a participant" });
    const group = room.type === "group" || (room.type === "system" && Boolean(room.schoolId));
    if (room.type === "system" && room.schoolId && !(room.adminIds || []).includes(uid)) {
      return res.status(403).json({ error: "Only school announcement admins can notify members" });
    }
    if (!group && (!receiverId || !participants.includes(receiverId) || receiverId === uid)) {
      return res.status(403).json({ error: "Invalid conversation recipient" });
    }
    // Never trust a caller-supplied recipient list for group fanout.
    let recipients = [...new Set(group ? participants.filter(id => id !== uid) : [receiverId])];
    // Firestore room membership can lag behind school suspension. Never notify
    // a suspended member, even before the reconciliation worker catches up.
    if (room.type === "system" && room.schoolId) {
      const active = await dbAll(
        "SELECT uid, role FROM school_memberships WHERE school_id = ? AND status = 'active'",
        [room.schoolId]);
      const allowed = new Set(active.map(m => m.uid));
      if (!allowed.has(uid)) return res.status(403).json({ error: "Inactive school membership" });
      if (!active.some(m => m.uid === uid && ["schooladmin", "school_admin"].includes(String(m.role).toLowerCase())))
        return res.status(403).json({ error: "School admin membership required" });
      recipients = recipients.filter(id => allowed.has(id));
    }
    // A caller must not be able to generate push notifications without
    // first writing the corresponding message to Firestore.
    const recent = await admin.firestore().collection("chatRooms").doc(conversationId)
      .collection("messages").orderBy("timestamp", "desc").limit(25).get();
    const now = Date.now();
    const matchingMessage = recent.docs.find(doc => {
      const m = doc.data();
      const sentAt = m.timestamp?.toMillis?.();
      return m.senderId === uid && m.message === messageText &&
        m.deleted !== true && typeof sentAt === "number" &&
        sentAt <= now + 30000 && sentAt >= now - 120000;
    });
    if (!matchingMessage) {
      return res.status(409).json({ error: "No recent matching message to notify" });
    }
    // Claim the message exactly once across retries and concurrent requests.
    const claimRef = admin.firestore().collection("chatNotificationClaims")
      .doc(conversationId + "_" + matchingMessage.id);
    const claimed = await admin.firestore().runTransaction(async tx => {
      const existing = await tx.get(claimRef);
      if (existing.exists) return false;
      tx.create(claimRef, {
        conversationId, messageId: matchingMessage.id, senderId: uid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return true;
    });
    if (!claimed) return res.json({ success: true, duplicate: true, recipients: 0, stored: 0, pushed: 0 });
    const preview = messageText.trim().slice(0, 100);
    const outcomes = await Promise.allSettled(recipients.map(async id => {
      const notificationData = {
        senderId: uid, text: group ? "sent a group message" : "sent you a message",
        messagePreview: preview, conversationId, type: "chat",
        ...(group ? { title: room.name || "Course group" } : {}),
        timestamp: admin.database.ServerValue.TIMESTAMP, read: false,
      };
      const notificationRef = admin.database().ref(`ChatNotifications/${id}`).push();
      await notificationRef.set(notificationData);
      const delivery = await sendFCMNotification(id, { ...notificationData, notificationId: notificationRef.key });
      return { delivered: delivery.success === true };
    }));
    const stored = outcomes.filter(o => o.status === "fulfilled").length;
    const pushed = outcomes.filter(o => o.status === "fulfilled" && o.value.delivered).length;
    return res.json({ success: true, recipients: recipients.length, stored, pushed });
  } catch (error) {
    console.error("[chat-notification]", error);
    return res.status(500).json({ error: "Failed to send chat notifications" });
  }
};

export const getConversations = async (req, res) => {
  const { userId } = req.params;
  const requestingUser = req.user.uid;

  if (userId !== requestingUser) {
    return res.status(403).json({ error: 'Forbidden' });
  }

  try {
    const snapshot = await admin.database()
      .ref(`ChatNotifications/${userId}`)
      .orderByChild('timestamp')
      .limitToLast(50)
      .once('value');

    const messages = [];
    snapshot.forEach((child) => {
      messages.push({
        id: child.key,
        ...child.val()
      });
    });

    res.json({ messages: messages.reverse() });
  } catch (error) {
    console.error('Error fetching chat notifications:', error);
    res.status(500).json({ error: 'Failed to fetch chat notifications' });
  }
};

export const markChatAsRead = async (req, res) => {
  const { conversationId } = req.params;
  const userId = req.user.uid;

  try {
    const snapshot = await admin.database()
      .ref(`ChatNotifications/${userId}`)
      .orderByChild('conversationId')
      .equalTo(conversationId)
      .once('value');

    const updates = {};
    snapshot.forEach((child) => {
      updates[`ChatNotifications/${userId}/${child.key}/read`] = true;
    });

    await admin.database().ref().update(updates);

    res.json({ success: true });
  } catch (error) {
    console.error('Error marking chat as read:', error);
    res.status(500).json({ error: 'Failed to mark chat as read' });
  }
};

