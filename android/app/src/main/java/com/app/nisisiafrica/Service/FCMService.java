package com.app.nisisiafrica.Service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.LiveViewerActivity;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.NotificationsActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Util;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class FCMService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "nisisi_notifications";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        String type = value(data.get("type"));

        RemoteMessage.Notification notification = remoteMessage.getNotification();
        String title;
        String body;
        if (notification != null) {
            title = value(notification.getTitle());
            body = value(notification.getBody());
        } else {
            // Data-only messages are used so live alerts take the same custom
            // PendingIntent path in foreground, background and cold-start cases.
            title = value(data.get("title"));
            if (title.isEmpty()) title = value(data.get("senderName"));
            if (title.isEmpty()) title = "Nelsen Savannah";
            body = value(data.get("body"));
            if (body.isEmpty()) body = value(data.get("messagePreview"));
            if (body.isEmpty()) body = "You have a new notification";
        }

        if ("live".equals(type)) {
            if (title.isEmpty()) title = "Live now";
            if (body.isEmpty()) {
                String eventTitle = value(data.get("eventTitle"));
                body = eventTitle.isEmpty() ? "A live session has started" : eventTitle + " is live now";
            }
        }

        showNotification(title, body, data);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        String userId = Util.getState(Constants.CURRENT_USER_ID, "");
        if (userId != null && !userId.isEmpty()) {
            FirebaseDatabase.getInstance()
                    .getReference("Tokens")
                    .child(userId)
                    .setValue(token);
        }
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        String type = value(data.get("type"));
        Intent destination;

        if ("live".equals(type) && !value(data.get("youtubeUrl")).isEmpty()) {
            destination = new Intent(this, LiveViewerActivity.class)
                    .putExtra(LiveViewerActivity.EXTRA_TITLE, value(data.get("eventTitle")))\n                    .putExtra(LiveViewerActivity.EXTRA_EVENT_ID, value(data.get("eventId")))
                    .putExtra(LiveViewerActivity.EXTRA_YOUTUBE_URL, value(data.get("youtubeUrl")))
                    .putExtra(LiveViewerActivity.EXTRA_LIVE_STATUS,
                            value(data.get("liveStatus")).isEmpty() ? "live" : value(data.get("liveStatus")));
        } else {
            destination = new Intent(this, NotificationsActivity.class)
                    .putExtra("courseId", value(data.get("courseId")))
                    .putExtra("senderId", value(data.get("senderId")))
                    .putExtra("type", type);
        }

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntent(new Intent(this, MainActivity.class));
        stackBuilder.addNextIntent(destination);

        String key = value(data.get("eventId"));
        if (key.isEmpty()) key = value(data.get("notificationId"));
        int requestCode = key.isEmpty() ? (int) (System.currentTimeMillis() & 0x7fffffff) : key.hashCode();

        PendingIntent pendingIntent = stackBuilder.getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title.isEmpty() ? "Nelsen Savannah" : title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = key.isEmpty()
                ? (int) (System.currentTimeMillis() & 0x7fffffff)
                : key.hashCode();
        notificationManager.notify(notificationId, builder.build());
    }

    private static String value(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
