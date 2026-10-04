import admin from "../config/firebase.js";
import { dbAll } from "../db/lmsDb.js";
import { sendFCMNotification } from "./fcmService.js";

async function loadRecipients(event) {
  const scope = event.audienceScope || "";
  let rows = [];

  if (scope === "course") {
    rows = await dbAll(
      `SELECT DISTINCT uid
       FROM enrollments
       WHERE track_id = ?
         AND uid IS NOT NULL
         AND uid != ''
         AND lower(COALESCE(status, 'in_progress')) NOT IN ('cancelled', 'dropped', 'suspended')`,
      [event.trackId],
    );
  } else if (scope === "school") {
    rows = await dbAll(
      `SELECT DISTINCT uid
       FROM school_memberships
       WHERE school_id = ?
         AND status = 'active'
         AND uid IS NOT NULL
         AND uid != ''`,
      [event.schoolId],
    );
  } else if (scope === "platform") {
    rows = await dbAll(
      `SELECT DISTINCT uid
       FROM users_mirror
       WHERE uid IS NOT NULL AND uid != ''`,
    );
  }

  const creator = String(event.createdBy || "");
  return [...new Set(rows.map((row) => String(row.uid || "")).filter(Boolean))]
    .filter((uid) => uid !== creator);
}

async function notifyOne(uid, event) {
  const notificationData = {
    senderId: event.createdBy || "",
    text: "is live now",
    eventId: event.eventId,
    eventTitle: event.title || "Live session",
    type: "live",
    audienceScope: event.audienceScope || "",
    schoolId: event.schoolId || "",
    trackId: event.trackId || "",
    timestamp: admin.database.ServerValue.TIMESTAMP,
    read: false,
  };

  const ref = admin.database().ref(`Notifications/${uid}`).push();
  await ref.set(notificationData);

  return sendFCMNotification(uid, {
    ...notificationData,
    notificationId: ref.key,
    title: "Live now",
    body: event.title ? `${event.title} is live now` : "A Nelsen live session has started",
  });
}

export async function notifyLiveStarted(event) {
  const recipients = await loadRecipients(event);
  let sent = 0;
  let missingToken = 0;
  let failed = 0;

  for (let i = 0; i < recipients.length; i += 25) {
    const batch = recipients.slice(i, i + 25);
    const results = await Promise.allSettled(batch.map((uid) => notifyOne(uid, event)));
    for (const result of results) {
      if (result.status === "rejected") {
        failed += 1;
        continue;
      }
      if (result.value?.success) sent += 1;
      else if (result.value?.reason === "no_token") missingToken += 1;
      else failed += 1;
    }
  }

  return {
    audienceScope: event.audienceScope,
    recipients: recipients.length,
    sent,
    missingToken,
    failed,
  };
}
