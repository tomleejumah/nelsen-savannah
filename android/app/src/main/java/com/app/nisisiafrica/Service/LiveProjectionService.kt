package com.app.nisisiafrica.Service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.app.nisisiafrica.R

/**
 * Keeps Android's MediaProjection authorization valid while a host shares
 * their screen. The encoder remains owned by GoLiveActivity so switching back
 * to camera does not reconnect the live session.
 */
class LiveProjectionService : Service() {
    companion object {
        private const val CHANNEL_ID = "nelsen_live_projection"
        private const val NOTIFICATION_ID = 4102
        private var readyCallback: (() -> Unit)? = null

        fun startWithCallback(context: Context, onReady: () -> Unit) {
            readyCallback = onReady
            ContextCompat.startForegroundService(
                context, Intent(context, LiveProjectionService::class.java),
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Live screen sharing",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Nelsen Live")
            .setContentText("Sharing your screen")
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        val callback = readyCallback
        readyCallback = null
        if (callback != null) Handler(Looper.getMainLooper()).post { callback() }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
