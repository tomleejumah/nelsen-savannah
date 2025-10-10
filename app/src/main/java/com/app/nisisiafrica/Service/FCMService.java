package com.app.nisisiafrica.Service;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class FCMService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();

            Map<String, String> data = remoteMessage.getData();
            String courseId = data.get("courseId");
            String senderId = data.get("senderId");

            Log.d(TAG, "onMessageReceived: "+title + body + courseId + senderId);

            //todo show notification
//            showNotification(title, body, courseId);
        }
    }

//    private void showNotification(String title, String body, String courseId) {
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "default")
//                .setSmallIcon(R.drawable.ic_notifications)
//                .setContentTitle(title)
//                .setContentBody(body)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setAutoCancel(true);
//
//        // Add intent to open course when clicked
//        Intent intent = new Intent(this, CourseActivity.class);
//        intent.putExtra("courseId", courseId);
//        PendingIntent pendingIntent = PendingIntent.getActivity(
//                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
//        );
//        builder.setContentIntent(pendingIntent);
//
//        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//        manager.notify(0, builder.build());
//    }
}

