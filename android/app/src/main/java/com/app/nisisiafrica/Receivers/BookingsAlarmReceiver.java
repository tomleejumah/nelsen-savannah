package com.app.nisisiafrica.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.app.nisisiafrica.Service.BookingsAlarmService;

public class BookingsAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "MyApp:MyWakelockTag");
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);

        int alarmId = intent.getIntExtra("BOOKING_ID", -1);

        // todo check time to event/trigger then reschedule a trigger at that time

        if (alarmId != -1) {
            Intent serviceIntent = new Intent(context, BookingsAlarmService.class);
            serviceIntent.putExtra("ALARM_ID", alarmId);
            context.startForegroundService(serviceIntent);

            Log.d(TAG, "run: Alarm Ended! ");
        }
    }
}
