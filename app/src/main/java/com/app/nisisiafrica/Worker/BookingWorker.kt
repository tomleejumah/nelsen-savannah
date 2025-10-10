package com.app.nisisiafrica.Worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.nisisiafrica.Constants
import com.app.nisisiafrica.Receivers.BookingsAlarmReceiver
import com.app.nisisiafrica.Utils.Util
import com.app.nisisiafrica.data.Model.Booking
import com.app.nisisiafrica.data.Repository.UserRepository
import java.time.LocalDateTime
import java.time.ZoneId

class BookingWorker (
    context: Context,
    params: WorkerParameters,
    private val userRepository: UserRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = Util.getState(Constants.CURRENT_USER_ID,"")
        val bookings = userRepository.getUserBookedDates(userId)


         val now = LocalDateTime.now()

        bookings.forEach { booking ->
            val bookingTime = booking.toLocalDateTime()

            if (bookingTime.isAfter(now)) {
                if (!isAlarmAlreadySet(booking.id)) {
                    scheduleAlarm(booking)
                }
            }
        }

        return Result.success()
    }
    private fun scheduleAlarm(booking: Booking) {
        val triggerTime = booking
            .toLocalDateTime()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val intent = Intent(applicationContext, BookingsAlarmReceiver::class.java).apply {
            putExtra("BOOKING_ID", booking.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            booking.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    private fun isAlarmAlreadySet(bookingId: Int): Boolean {
        val intent = Intent(applicationContext, BookingsAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            bookingId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return pendingIntent != null
    }
}