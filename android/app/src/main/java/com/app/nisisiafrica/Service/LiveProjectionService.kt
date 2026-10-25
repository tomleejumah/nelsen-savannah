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
import android.media.projection.MediaProjection
import androidx.core.app.NotificationCompat
import com.app.nisisiafrica.R
import com.pedro.common.ConnectChecker
import com.pedro.library.generic.GenericStream
import java.lang.ref.WeakReference

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
        private var activeStream: GenericStream? = null
        private var activeProjection: MediaProjection? = null

        @Synchronized
        fun holdProjection(projection: MediaProjection) {
            activeProjection?.takeIf { it !== projection }?.stop()
            activeProjection = projection
        }

        @Synchronized
        fun releaseProjection() {
            activeProjection?.stop()
            activeProjection = null
        }
        private var listener = WeakReference<ConnectChecker>(null)
        private val checker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) { listener.get()?.onConnectionStarted(url) }
            override fun onConnectionSuccess() { listener.get()?.onConnectionSuccess() }
            override fun onConnectionFailed(reason: String) { listener.get()?.onConnectionFailed(reason) }
            override fun onNewBitrate(bitrate: Long) { listener.get()?.onNewBitrate(bitrate) }
            override fun onDisconnect() { listener.get()?.onDisconnect() }
            override fun onAuthError() { listener.get()?.onAuthError() }
            override fun onAuthSuccess() { listener.get()?.onAuthSuccess() }
        }

        @Synchronized
        fun attachStream(context: Context, observer: ConnectChecker): GenericStream {
            listener = WeakReference(observer)
            return activeStream ?: GenericStream(context.applicationContext, checker)
                .also { activeStream = it }
        }

        @Synchronized
        fun detachObserver(observer: ConnectChecker) {
            if (listener.get() === observer) listener.clear()
        }

        @Synchronized
        fun releaseStream() {
            listener.clear()
            activeStream?.let { stream ->
                try {
                    if (stream.isStreaming) stream.stopStream()
                    stream.release()
                } catch (error: Exception) {
                    android.util.Log.w("NelsenLive", "Stream cleanup failed", error)
                }
            }
            activeStream = null
            releaseProjection()
        }


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

    override fun onDestroy() {
        releaseStream()
        readyCallback = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
