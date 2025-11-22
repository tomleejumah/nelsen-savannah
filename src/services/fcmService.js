import admin from "../config/firebase.js";

export async function sendFCMNotification(receiverId, notification) {
  try {
    const tokenSnap = await admin
      .database()
      .ref(`/Tokens/${receiverId}`)
      .once("value");

    const token = tokenSnap.val();
    if (!token) {
      console.log("No FCM token for user:", receiverId);
      return { success: false, reason: "no_token" };
    }

    const senderSnap = await admin
      .database()
      .ref(`/users/${notification.senderId}/lastName`)
      .once("value");

    const senderName = senderSnap.val() || "Someone";

    let title = "New Notification";
    let body = `${senderName} ${notification.text}`;

    if (notification.type === "like") {
      title = "New Like";
    } else if (notification.type === "comment") {
      title = "New Comment";
    } else if (notification.type === "chat") {
      title = `${senderName}`;
      body = notification.messagePreview || "Sent you a message";
    }

    // NEW FCM V1 API
    const message = {
      token: token,
      notification: {
        title: title,
        body: body,
      },
      data: {
        senderId: notification.senderId || "",
        type: notification.type || "",
        notificationId: notification.notificationId || "",
        ...(notification.courseID && { courseId: notification.courseID }),
        ...(notification.conversationId && {
          conversationId: notification.conversationId,
        }),
      },
      android: {
        priority: "high",
      },
    };

    const response = await admin.messaging().send(message);
    console.log("FCM sent successfully:", response);

    return { success: true, response };
  } catch (error) {
    console.error("Error sending FCM:", error);

    // Handle invalid token
    if (
      error.code === "messaging/invalid-registration-token" ||
      error.code === "messaging/registration-token-not-registered"
    ) {
      console.log("Removing invalid token for:", receiverId);
      await admin.database().ref(`/Tokens/${receiverId}`).remove();
      return { success: false, reason: "invalid_token" };
    }

    return { success: false, error: error.message };
  }
}
