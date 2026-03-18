package com.app.nisisiafrica.Service;

import android.util.Log;

import androidx.annotation.NonNull;

import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Util;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class FCMService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "nisisi_notifications";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage.getNotification() != null) {
            Map<String, String> data = remoteMessage.getData();
            String type = data.get("type");

            String title, body;

            if (remoteMessage.getNotification() != null) {
                title = remoteMessage.getNotification().getTitle();
                body = remoteMessage.getNotification().getBody();
            } else {
                // data-only message (chat)
                title = data.get("senderName") != null ? data.get("senderName") : "New Message";
                body = data.get("messagePreview") != null ? data.get("messagePreview") : "sent you a message";
            }

            String courseId = data.get("courseId");
            String senderId = data.get("senderId");
            String conversationId = data.get("conversationId");

            showNotification(title, body, courseId, senderId, type);
        }
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

    private void showNotification(String title, String body, String courseId,
                                  String senderId, String type) {

        // Create intent to open app when notification is clicked
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("courseId", courseId);
        intent.putExtra("senderId", senderId);
        intent.putExtra("type", type);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }
}
