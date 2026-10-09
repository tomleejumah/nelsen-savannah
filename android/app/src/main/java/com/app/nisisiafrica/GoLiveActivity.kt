package com.app.nisisiafrica

import android.Manifest
import android.app.PictureInPictureParams
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.TextView
import android.widget.ScrollView
import com.google.android.material.textfield.TextInputEditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.app.nisisiafrica.Service.LiveProjectionService
import com.app.nisisiafrica.data.Model.LmsModels
import com.app.nisisiafrica.data.remote.ApiClient
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.pedro.common.ConnectChecker
import com.pedro.common.socket.base.SocketType
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.generic.GenericStream
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GoLiveActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {

    companion object {
        const val EXTRA_INGEST_URL = "extra_ingest_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_YOUTUBE_URL = "extra_youtube_url"
    }

    private lateinit var preview: SurfaceView
    private lateinit var status: TextView
    private lateinit var viewers: TextView
    private lateinit var attendance: TextView
    private var attendanceRows: List<LmsModels.LivePresenceAttendee> = emptyList()
    private lateinit var chat: TextView
    private lateinit var chatScroll: ScrollView
    private var lastChatSnapshot = ""
    private lateinit var hostChatInput: TextInputEditText
    private lateinit var hostChatSend: MaterialButton
    private var hostChatSending = false
    private val telemetryHandler = Handler(Looper.getMainLooper())
    private val telemetryPoll = object : Runnable {
        override fun run() {
            refreshTelemetry()
            refreshAttendance()
            telemetryHandler.postDelayed(this, 5_000L)
        }
    }
    private lateinit var endButton: MaterialButton
    private lateinit var switchButton: MaterialButton
    private lateinit var micButton: MaterialButton
    private lateinit var cameraButton: MaterialButton
    private lateinit var screenButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var stream: GenericStream
    private var mediaProjection: MediaProjection? = null
    private var micMuted = false
    private var cameraPaused = false
    private var sharingScreen = false
    private var projectionConsentPending = false

    private var ingestUrl = ""
    private var eventId = ""
    private var youtubeUrl = ""
    private var surfaceReady = false
    private var prepared = false
    private var finishingLive = false
    private var wentLive = false
    private var endedSent = false

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            projectionConsentPending = false
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                screenButton.isEnabled = true
                stopService(Intent(this, LiveProjectionService::class.java))
                return@registerForActivityResult
            }
            try {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, LiveProjectionService::class.java),
                )
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection?.stop()
                mediaProjection = manager.getMediaProjection(result.resultCode, result.data!!)
                val projection = mediaProjection ?: return@registerForActivityResult
                stream.changeVideoSource(ScreenSource(applicationContext, projection))
                stream.getGlInterface().setCameraOrientation(0)
                sharingScreen = true
                cameraPaused = false
                screenButton.contentDescription = "Stop sharing screen"
                cameraButton.contentDescription = "Turn camera off"
                switchButton.isEnabled = false
                status.text = "LIVE · Sharing screen"
                screenButton.isEnabled = true
            } catch (e: Exception) {
                screenButton.isEnabled = true
                mediaProjection?.stop()
                mediaProjection = null
                stopService(Intent(this, LiveProjectionService::class.java))
                Toast.makeText(this, e.message ?: "Could not share screen", Toast.LENGTH_LONG).show()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.CAMERA] == true &&
                result[Manifest.permission.RECORD_AUDIO] == true
            if (granted) {
                maybeStartLive()
            } else {
                Toast.makeText(
                    this,
                    "Camera and microphone permission are required to go live",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_go_live)

        ingestUrl = intent.getStringExtra(EXTRA_INGEST_URL).orEmpty()
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        youtubeUrl = intent.getStringExtra(EXTRA_YOUTUBE_URL).orEmpty()
        if (!ingestUrl.startsWith("rtmps://", ignoreCase = true) &&
            !ingestUrl.startsWith("rtmp://", ignoreCase = true)
        ) {
            Toast.makeText(this, "The live service did not return a valid endpoint", Toast.LENGTH_LONG)
                .show()
            finish()
            return
        }

        preview = findViewById(R.id.livePreview)
        status = findViewById(R.id.tvLiveStatus)
        viewers = findViewById(R.id.tvLiveViewers)
        attendance = findViewById(R.id.tvHostAttendance)
        attendance.setOnClickListener { showAttendanceDialog() }
        chat = findViewById(R.id.tvLiveChat)
        chatScroll = findViewById(R.id.hostChatScroll)
        hostChatInput = findViewById(R.id.inputHostLiveChat)
        hostChatSend = findViewById(R.id.btnHostSendLiveChat)
        hostChatSend.setOnClickListener { sendHostChat() }
        endButton = findViewById(R.id.btnEndLive)
        switchButton = findViewById(R.id.btnSwitchCamera)
        micButton = findViewById(R.id.btnToggleMic)
        cameraButton = findViewById(R.id.btnToggleCamera)
        screenButton = findViewById(R.id.btnShareScreen)
        shareButton = findViewById(R.id.btnShareLive)
        findViewById<TextView>(R.id.tvLiveTitle).text =
            intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Nelsen Live" }

        stream = GenericStream(applicationContext, this)
        // RootEncoder's Ktor TLS socket can collide with another Ktor ABI in the
        // app at runtime (ByteChannel NoSuchMethodError). RTMPS works with the
        // library's Java socket implementation and avoids that dependency path.
        stream.getStreamClient().setSocketType(SocketType.JAVA)
        preview.holder.addCallback(this)

        endButton.setOnClickListener { confirmEndLive() }
        switchButton.setOnClickListener {
            try {
                (stream.videoSource as? Camera2Source)?.switchCamera()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not switch camera", Toast.LENGTH_SHORT).show()
            }
        }
        micButton.setOnClickListener { toggleMicrophone() }
        cameraButton.setOnClickListener { toggleCamera() }
        screenButton.setOnClickListener { toggleScreenShare() }
        shareButton.setOnClickListener { shareLive() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmEndLive()
        })

        requestPermissionsOrStart()
    }

    private fun requestPermissionsOrStart() {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (camera && mic) {
            maybeStartLive()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    private fun maybeStartLive() {
        if (!surfaceReady || isFinishing || finishingLive) return

        if (!prepared) {
            status.text = "Preparing camera and microphone…"
            prepared = try {
                stream.prepareVideo(1280, 720, 2_500_000, rotation = 90) &&
                    stream.prepareAudio(44_100, true, 128_000)
            } catch (e: Exception) {
                false
            }
            if (!prepared) {
                status.text = "This device could not prepare the live encoder."
                Toast.makeText(this, "Could not prepare live video", Toast.LENGTH_LONG).show()
                return
            }
        }

        try {
            if (!stream.isOnPreview) {
                stream.startPreview(preview)
            }
            if (!stream.isStreaming) {
                status.text = "Connecting to live…"
                stream.startStream(ingestUrl)
            }
        } catch (e: Exception) {
            status.text = "Could not start live stream"
            Toast.makeText(this, e.message ?: "Could not start live", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleMicrophone() {
        val microphone = stream.audioSource as? MicrophoneSource ?: return
        try {
            if (micMuted) microphone.unMute() else microphone.mute()
            micMuted = !micMuted
            micButton.contentDescription = if (micMuted) "Unmute microphone" else "Mute microphone"
        } catch (e: Exception) {
            Toast.makeText(this, "Could not change microphone", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreCamera() {
        mediaProjection?.stop()
        mediaProjection = null
        stopService(Intent(this, LiveProjectionService::class.java))
        stream.changeVideoSource(Camera2Source(applicationContext))
        stream.getGlInterface().setCameraOrientation(90)
        sharingScreen = false
        cameraPaused = false
        screenButton.contentDescription = "Share screen"
        cameraButton.contentDescription = "Turn camera off"
        switchButton.isEnabled = true
        if (wentLive) status.text = "LIVE · Streaming"
    }

    private fun toggleCamera() {
        try {
            if (sharingScreen) {
                restoreCamera()
                return
            }
            if (cameraPaused) {
                restoreCamera()
            } else {
                stream.changeVideoSource(NoVideoSource())
                cameraPaused = true
                cameraButton.contentDescription = "Turn camera on"
                switchButton.isEnabled = false
                if (wentLive) status.text = "LIVE · Camera paused"
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not change camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleScreenShare() {
        if (sharingScreen) {
            try {
                restoreCamera()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not restore camera", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (projectionConsentPending) return
        projectionConsentPending = true
        screenButton.isEnabled = false
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
        } catch (e: Exception) {
            projectionConsentPending = false
            screenButton.isEnabled = true
            Toast.makeText(this, "Could not request screen sharing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLive() {
        if (eventId.isBlank()) return
        val link = "https://nelsen-savannah.co.ke/live/$eventId"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                },
                "Share live session",
            ),
        )
    }


    private fun refreshTelemetry() {
        if (!wentLive || eventId.isBlank()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { token ->
            ApiClient.getLmsService()
                .liveState("Bearer ${token.token}", eventId)
                .enqueue(object : Callback<LmsModels.LiveStateEnvelope> {
                    override fun onResponse(
                        call: Call<LmsModels.LiveStateEnvelope>,
                        response: Response<LmsModels.LiveStateEnvelope>,
                    ) {
                        val data = response.body()?.data ?: return
                        viewers.text = "${data.concurrentViewers} watching"
                        val messages = data.chat.orEmpty()
                        val snapshot = if (messages.isEmpty()) "Live · waiting for chat" else
                            messages.joinToString("\n") { "${it.author}: ${it.message}" }
                        if (snapshot != lastChatSnapshot) {
                            val wasAtBottom = !chatScroll.canScrollVertically(1)
                            lastChatSnapshot = snapshot
                            chat.text = snapshot
                            if (wasAtBottom) chatScroll.post { chatScroll.fullScroll(android.view.View.FOCUS_DOWN) }
                        }
                    }
                    override fun onFailure(
                        call: Call<LmsModels.LiveStateEnvelope>,
                        t: Throwable,
                    ) = Unit
                })
        }
    }

    private fun refreshAttendance() {
        if (!wentLive || eventId.isBlank()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { token ->
            ApiClient.getLmsService()
                .liveAttendance("Bearer ${token.token}", eventId)
                .enqueue(object : Callback<LmsModels.LiveAttendanceEnvelope> {
                    override fun onResponse(
                        call: Call<LmsModels.LiveAttendanceEnvelope>,
                        response: Response<LmsModels.LiveAttendanceEnvelope>,
                    ) {
                        if (!response.isSuccessful) return
                        val data = response.body()?.data ?: return
                        val cutoff = System.currentTimeMillis() - 30_000L
                        attendanceRows = data.attendees.orEmpty()
                        val active = attendanceRows.filter { it.lastSeenAt >= cutoff }
                        val names = active.take(3).joinToString(", ") { it.displayName ?: "Viewer" }
                        attendance.text = "Attendees · ${active.size} active / ${data.uniqueAttendees} total · tap to view" +
                            if (names.isNotBlank()) "\n$names" else ""
                    }
                    override fun onFailure(
                        call: Call<LmsModels.LiveAttendanceEnvelope>,
                        t: Throwable,
                    ) = Unit
                })
        }
    }

    private fun showAttendanceDialog() {
        val now = System.currentTimeMillis()
        val rows = attendanceRows.sortedWith(
            compareByDescending<LmsModels.LivePresenceAttendee> { it.lastSeenAt >= now - 30_000L }
                .thenByDescending { it.lastSeenAt },
        )
        val content = if (rows.isEmpty()) "No attendees yet" else rows.joinToString("\n\n") {
            val name = it.displayName?.takeIf { name -> name.isNotBlank() } ?: "Viewer"
            val minutes = it.watchSeconds / 60
            val seconds = it.watchSeconds % 60
            val presence = if (it.lastSeenAt >= now - 30_000L) "Watching now" else "Left"
            "$name · $presence · ${minutes}m ${seconds}s"
        }
        val scroll = ScrollView(this)
        val details = TextView(this).apply {
            text = content
            setPadding(48, 24, 48, 24)
            textSize = 14f
        }
        scroll.addView(details)
        AlertDialog.Builder(this)
            .setTitle("Live attendees (${rows.size})")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun sendHostChat() {
        if (!wentLive || finishingLive || eventId.isBlank() || hostChatSending) return
        val message = hostChatInput.text?.toString()?.trim().orEmpty()
        if (message.isBlank() || message.length > 500) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        hostChatSending = true
        hostChatSend.isEnabled = false
        user.getIdToken(false).addOnSuccessListener { token ->
            ApiClient.getLmsService()
                .postLiveChat("Bearer ${token.token}", eventId, mapOf("message" to message))
                .enqueue(object : Callback<LmsModels.MapEnvelope> {
                    override fun onResponse(call: Call<LmsModels.MapEnvelope>, response: Response<LmsModels.MapEnvelope>) {
                        hostChatSending = false
                        hostChatSend.isEnabled = true
                        if (response.isSuccessful) {
                            if (hostChatInput.text?.toString()?.trim() == message) hostChatInput.setText("")
                            refreshTelemetry()
                        } else {
                            Toast.makeText(this@GoLiveActivity, "Message not sent (${response.code()})", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<LmsModels.MapEnvelope>, t: Throwable) {
                        hostChatSending = false
                        hostChatSend.isEnabled = true
                        Toast.makeText(this@GoLiveActivity, "Could not send message", Toast.LENGTH_SHORT).show()
                    }
                })
        }.addOnFailureListener {
            hostChatSending = false
            hostChatSend.isEnabled = true
        }
    }

    private fun confirmEndLive() {
        if (finishingLive) return
        AlertDialog.Builder(this)
            .setTitle("End live?")
            .setMessage("This stops your camera stream and ends the live session.")
            .setNegativeButton("Keep live", null)
            .setPositiveButton("End live") { _, _ -> stopAndFinish() }
            .show()
    }

    private fun stopAndFinish() {
        finishingLive = true
        status.text = "Ending live…"
        try {
            if (stream.isStreaming) stream.stopStream()
            if (stream.isOnPreview) stream.stopPreview()
        } catch (_: Exception) {
        }
        if (wentLive) {
            markNelsenLiveStatus("ended")
            endedSent = true
        }
        finish()
    }

    private fun markNelsenLiveStatus(liveStatus: String) {
        if (eventId.isBlank()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { token ->
            val body = LmsModels.LiveStatusBody(
                liveStatus,
                if (youtubeUrl.isBlank()) null else youtubeUrl,
            )
            ApiClient.getLmsService()
                .updateHubLiveStatus("Bearer ${token.token}", eventId, body)
                .enqueue(object : Callback<LmsModels.HubEventEnvelope> {
                    override fun onResponse(
                        call: Call<LmsModels.HubEventEnvelope>,
                        response: Response<LmsModels.HubEventEnvelope>,
                    ) = Unit

                    override fun onFailure(
                        call: Call<LmsModels.HubEventEnvelope>,
                        t: Throwable,
                    ) = Unit
                })
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        maybeStartLive()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        try {
            stream.getGlInterface().setPreviewResolution(width, height)
        } catch (_: Exception) {
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        if (!stream.isStreaming) {
            try {
                if (stream.isOnPreview) stream.stopPreview()
            } catch (_: Exception) {
            }
        }
    }

    override fun onConnectionStarted(url: String) {
        runOnUiThread { status.text = "Connecting to live…" }
    }

    override fun onConnectionSuccess() {
        wentLive = true
        markNelsenLiveStatus("live")
        telemetryHandler.removeCallbacks(telemetryPoll)
        telemetryHandler.post(telemetryPoll)
        runOnUiThread {
            status.text = "LIVE · Streaming"
            endButton.isEnabled = true
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        // Connection status is enough for the host UI; bitrate remains internal.
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            status.text = "Live connection failed"
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDisconnect() {
        runOnUiThread {
            if (!finishingLive) status.text = "Disconnected from live"
        }
    }

    override fun onAuthError() {
        runOnUiThread { status.text = "Live stream authorization failed" }
    }

    override fun onAuthSuccess() {
        // The live provider uses the stream endpoint/key rather than RTMP user/password auth.
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !isFinishing &&
            wentLive &&
            stream.isStreaming &&
            !isInPictureInPictureMode
        ) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(9, 16))
                        .build(),
                )
            } catch (_: Exception) {
            }
        }
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wentLive && stream.isStreaming) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(9, 16))
                        .build(),
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        telemetryHandler.removeCallbacks(telemetryPoll)
        try {
            if (stream.isStreaming) stream.stopStream()
            mediaProjection?.stop()
            mediaProjection = null
            stopService(Intent(this, LiveProjectionService::class.java))
            stream.release()
        } catch (_: Exception) {
        }
        if (wentLive && !endedSent && !isChangingConfigurations) {
            endedSent = true
            markNelsenLiveStatus("ended")
        }
        super.onDestroy()
    }
}
