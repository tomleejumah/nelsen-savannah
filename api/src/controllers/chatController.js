import admin from '../config/firebase.js';
import { sendFCMNotification } from '../services/fcmService.js';

export const sendChatMessageNotification = async (req, res) => {
  const uid = req.user.uid;
  const { receiverId, messageText, conversationId } = req.body;

  if (!receiverId || !messageText || !conversationId) {
    return res.status(400).json({ 
      error: 'Missing required fields: receiverId, messageText, conversationId' 
    });
  }

  if (receiverId === uid) {
    return res.json({ success: false, message: 'Cannot message yourself' });
  }

  try {
    const notificationData = {
      senderId: uid,
      text: 'sent you a message',
      messagePreview: messageText.substring(0, 100),
      conversationId: conversationId,
      type: 'chat',
      timestamp: admin.database.ServerValue.TIMESTAMP,
      read: false
    };

    const notificationRef = await admin.database()
      .ref(`ChatNotifications/${receiverId}`)
      .push();
    
    await notificationRef.set(notificationData);

    await sendFCMNotification(receiverId, {
      ...notificationData,
      notificationId: notificationRef.key
    });

    res.json({ success: true, notificationId: notificationRef.key });
  } catch (error) {
    console.error('Error sending chat notification:', error);
    res.status(500).json({ error: 'Failed to send chat notification' });
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

