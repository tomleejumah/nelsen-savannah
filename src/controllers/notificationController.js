import admin from "../config/firebase.js";
import { sendFCMNotification } from "../services/fcmService.js";

export const sendLikeNotification = async (req, res) => {
  const uid = req.user.uid;
  const { coursePublisher, postID, text } = req.body;

  if (!coursePublisher || !postID || !text) {
    return res.status(400).json({
      error: "Missing required fields: coursePublisher, postID, text",
    });
  }

  if (coursePublisher === uid) {
    return res.json({
      success: false,
      message: "Cannot notify yourself",
    });
  }

  try {
    const notificationData = {
      senderId: uid,
      text: text,
      courseID: postID,
      type: "like",
      timestamp: admin.database.ServerValue.TIMESTAMP,
      read: false,
    };

    const notificationRef = await admin
      .database()
      .ref(`Notifications/${coursePublisher}`)
      .push();

    await notificationRef.set(notificationData);

    const fcmResult = await sendFCMNotification(coursePublisher, {
      ...notificationData,
      notificationId: notificationRef.key,
    });

    res.json({
      success: true,
      notificationId: notificationRef.key,
      fcmSent: fcmResult?.success || false,
    });
  } catch (error) {
    console.error("Error creating notification:", error);
    res.status(500).json({ error: "Failed to send notification" });
  }
};

export const sendCommentNotification = async (req, res) => {
  const uid = req.user.uid;
  const { coursePublisher, postID, text, commentText } = req.body;

  if (!coursePublisher || !postID || !text) {
    return res.status(400).json({ error: "Missing required fields" });
  }

  if (coursePublisher === uid) {
    return res.json({ success: false, message: "Cannot notify yourself" });
  }

  try {
    const notificationData = {
      senderId: uid,
      text: text,
      courseID: postID,
      commentText: commentText || "",
      type: "comment",
      timestamp: admin.database.ServerValue.TIMESTAMP,
      read: false,
    };

    const notificationRef = await admin
      .database()
      .ref(`Notifications/${coursePublisher}`)
      .push();

    await notificationRef.set(notificationData);

    await sendFCMNotification(coursePublisher, {
      ...notificationData,
      notificationId: notificationRef.key,
    });

    res.json({ success: true, notificationId: notificationRef.key });
  } catch (error) {
    console.error("Error creating notification:", error);
    res.status(500).json({ error: "Failed to send notification" });
  }
};

export const getUserNotifications = async (req, res) => {
  const { userId } = req.params;
  const requestingUser = req.user.uid;

  if (userId !== requestingUser) {
    return res.status(403).json({ error: "Forbidden" });
  }

  try {
    const snapshot = await admin
      .database()
      .ref(`Notifications/${userId}`)
      .orderByChild("timestamp")
      .limitToLast(50)
      .once("value");

    const notifications = [];
    snapshot.forEach((child) => {
      notifications.push({
        id: child.key,
        ...child.val(),
      });
    });

    res.json({ notifications: notifications.reverse() });
  } catch (error) {
    console.error("Error fetching notifications:", error);
    res.status(500).json({ error: "Failed to fetch notifications" });
  }
};

export const markAsRead = async (req, res) => {
  const { userId, notificationId } = req.params;
  const requestingUser = req.user.uid;

  if (userId !== requestingUser) {
    return res.status(403).json({ error: "Forbidden" });
  }

  try {
    await admin
      .database()
      .ref(`Notifications/${userId}/${notificationId}/read`)
      .set(true);

    res.json({ success: true });
  } catch (error) {
    console.error("Error marking notification as read:", error);
    res.status(500).json({ error: "Failed to update notification" });
  }
};

export const deleteNotification = async (req, res) => {
  const { userId, notificationId } = req.params;
  const requestingUser = req.user.uid;

  if (userId !== requestingUser) {
    return res.status(403).json({ error: "Forbidden" });
  }

  try {
    await admin
      .database()
      .ref(`Notifications/${userId}/${notificationId}`)
      .remove();

    res.json({ success: true });
  } catch (error) {
    console.error("Error deleting notification:", error);
    res.status(500).json({ error: "Failed to delete notification" });
  }
};
