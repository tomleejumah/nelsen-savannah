package com.app.nisisiafrica.Worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.nisisiafrica.Constants
import com.app.nisisiafrica.Receivers.EventReminderReceiver
import com.app.nisisiafrica.Utils.Util
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import java.time.ZoneId

/**
 * Self-contained reminder worker (no injected dependencies, so the default
 * WorkManager factory can build it). It reads the signed-in user's upcoming
 * events and booked sessions from existing data and schedules a local alarm a
 * little before each one, which posts a notification via [EventReminderReceiver].
 */
class EventReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = Util.getState(Constants.CURRENT_USER_ID, "")
        if (userId.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()

        // 1. Upcoming events created via CreateEventActivity / bookMentor
        try {
            FirebaseRemoteDataSource.getUpcomingEvents(userId).forEach { event ->
                if (event.date <= now) return@forEach
                val triggerAt = (event.date - REMINDER_LEAD_MS).let { if (it < now) event.date else it }
                val title = event.title.ifEmpty { "Upcoming event" }
                val timeSuffix = if (event.startTime.isNotEmpty()) " at ${event.startTime}" else ""
                scheduleAlarm(
                    applicationContext,
                    event.eventId.hashCode(),
                    triggerAt,
                    title,
                    "Reminder: $title$timeSuffix",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "event reminders failed", e)
        }

        // 2. Booked sessions / schedules
        try {
            FirebaseRemoteDataSource.getBookedDates(userId).forEach { booking ->
                val whenMs = booking.toLocalDateTime()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                if (whenMs <= now) return@forEach
                val triggerAt = (whenMs - REMINDER_LEAD_MS).let { if (it < now) whenMs else it }
                scheduleAlarm(
                    applicationContext,
                    ("booking_" + booking.id).hashCode(),
                    triggerAt,
                    "Upcoming session",
                    "You have a session on ${booking.date} at ${booking.time}",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "booking reminders failed", e)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "EventReminderWorker"
        const val REMINDER_LEAD_MS = 30L * 60L * 1000L // 30 minutes before

        /** One-shot local reminder — callable from Java (e.g. after seat reserve). */
        @JvmStatic
        fun scheduleAlarm(
            context: Context,
            id: Int,
            triggerAtMs: Long,
            title: String,
            text: String,
        ) {
            val intent = Intent(context, EventReminderReceiver::class.java).apply {
                putExtra(EventReminderReceiver.EXTRA_ID, id)
                putExtra(EventReminderReceiver.EXTRA_TITLE, title)
                putExtra(EventReminderReceiver.EXTRA_TEXT, text)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()

            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }
}
