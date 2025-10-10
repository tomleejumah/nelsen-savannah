package com.app.nisisiafrica.Service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.app.nisisiafrica.R;

public class BookingsAlarmService extends Service {
    private static final long ALARM_DURATION_MS = 60 * 1000; // 1 minute
    private static final int NOTIFICATION_ID = 1;
    private PendingIntent pendingIntent;
    private Notification notification;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //todo add songs for alarm also alarm time
        //todo use alarmId to fetch mentee or mentor info from database

//        String songResource = intent.getStringExtra("SONG_RESOURCE_ID");
        int alarmId = intent.getIntExtra("BOOKING_ID", -1);
//        int alarmHour = intent.getIntExtra("ALARM_HOUR", -1);
//        int alarmMinute = intent.getIntExtra("ALARM_MINUTE", -1);
//        String alarmTime = String.format(Locale.getDefault(), "%02d:%02d", alarmHour, alarmMinute);


//        Intent alarmActivityIntent = new Intent(this, AlarmFullScreenNotificationActivity.class);
//        alarmActivityIntent.putExtra("ALARM_ID", alarmId);
//        alarmActivityIntent.putExtra("ALARM_TIME", alarmTime);
//        alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        pendingIntent = PendingIntent.getActivity(this, 0, alarmActivityIntent, PendingIntent.FLAG_IMMUTABLE);

//        buildNotification(alarmTime);
        showNotifications();

//        MediaPlayerManager.getInstance().play(this, songResource);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        //stopping audio  after 1 minute
//        new Handler(Looper.getMainLooper()).postDelayed(this::stopSelf, ALARM_DURATION_MS);
        return START_STICKY;
    }
    private void buildNotification() {

//        PendingIntent snoozeButton = PendingIntent.getBroadcast(this, 0,
//                new Intent(this, AlarmNotificationReceiver.class).setAction("ACTION_SNOOZE"),
//                PendingIntent.FLAG_IMMUTABLE);
//
//        PendingIntent cancelButton = PendingIntent.getBroadcast(this, 1,
//                new Intent(this, AlarmNotificationReceiver.class).setAction("ACTION_CANCEL"),
//                PendingIntent.FLAG_IMMUTABLE);
//
//        RemoteViews compactLayout = new RemoteViews(getPackageName(), R.layout.notification_alarm_compact);
//        RemoteViews expandedLayout = new RemoteViews(getPackageName(), R.layout.notification_alarm);
//
//        // Set time in both layouts
//        compactLayout.setTextViewText(R.id.alarm_time_text, alarmTime);
//        expandedLayout.setTextViewText(R.id.alarm_time_text, alarmTime);
//
//        // Set buttons for both layouts
//        compactLayout.setOnClickPendingIntent(R.id.snoozeBtn, snoozeButton);
//        compactLayout.setOnClickPendingIntent(R.id.canceltn, cancelButton);
//        expandedLayout.setOnClickPendingIntent(R.id.snoozeBtn, snoozeButton);
//        expandedLayout.setOnClickPendingIntent(R.id.canceltn, cancelButton);

//        notification = new NotificationCompat.Builder(this, "alarms")
//                .setSmallIcon(R.drawable.league)
//                .setCustomContentView(compactLayout)
//                .setCustomBigContentView(expandedLayout)
//                .setContentIntent(pendingIntent)
//                .setPriority(NotificationCompat.PRIORITY_MAX)
//                .setCategory(NotificationCompat.CATEGORY_ALARM)
//                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//                .setOngoing(true)
//                .setFullScreenIntent(pendingIntent, true)
//                .build();

    }
    public void showNotifications() {
        //todo send notification to notification activity/class and firebase also have counter increased for notification
        Bitmap icon = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                R.drawable.ic_notifications);

        Notification notification = new NotificationCompat.Builder(this, "BookingAlarm")
                .setSmallIcon(R.drawable.ic_notifications)
                .setLargeIcon(icon)
                .setContentTitle("Reminder")
                .setContentText("Reminder to attend your Booking")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Tap to cancel"))
                .setAutoCancel(true)
                .setTimeoutAfter(36000000)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);

        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
//        MediaPlayerManager.getInstance().stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}