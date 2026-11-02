import admin from '../config/firebase.js';
import { sendFCMNotification } from '../services/fcmService.js';

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
    const group = room.type === "group";
    if (!group && (!receiverId || !participants.includes(receiverId) || receiverId === uid)) {
      return res.status(403).json({ error: "Invalid conversation recipient" });
    }
    // Never trust a caller-supplied recipient list for group fanout.
    const recipients = [...new Set(group ? participants.filter(id => id !== uid) : [receiverId])];
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

