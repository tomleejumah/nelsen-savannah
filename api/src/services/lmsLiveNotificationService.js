import admin from "../config/firebase.js";
import { dbAll } from "../db/lmsDb.js";
import { sendFCMNotification } from "./fcmService.js";
import { liveRecipientQuery } from "./lmsLiveRecipientQueries.js";

async function loadRecipients(event) {
  const query = liveRecipientQuery(event);
  if (!query) {
    console.warn("[live-notify] audience has no valid school/course scope", {
      eventId: event.eventId,
      scope: event.audienceScope,
    });
    return [];
  }
  const rows = await dbAll(query.sql, query.params);
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
    youtubeUrl: event.meetingLink || "",
    liveStatus: event.liveStatus || "live",
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
  console.log("[live-notify] fanout", {
    eventId: event.eventId,
    scope: event.audienceScope,
    schoolId: event.schoolId || "",
    trackId: event.trackId || "",
    creator: event.createdBy || "",
    recipients: recipients.length,
  });
  let sent = 0;
  let missingToken = 0;
  let failed = 0;

  for (let i = 0; i < recipients.length; i += 25) {
    const batch = recipients.slice(i, i + 25);
    const results = await Promise.allSettled(batch.map((uid) => notifyOne(uid, event)));
    for (let index = 0; index < results.length; index += 1) {
      const result = results[index];
      if (result.status === "rejected") {
        // Never put recipient UIDs, push tokens or per-device FCM payloads in
        // fanout logs. The final counters are sufficient for operational QA.
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
