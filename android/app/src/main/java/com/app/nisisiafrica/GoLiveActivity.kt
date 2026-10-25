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
import com.app.nisisiafrica.Utils.LiveChatTimeline
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
    private val chatTimeline = LiveChatTimeline()
    private lateinit var olderChatButton: MaterialButton
    private var olderChatLoading = false
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
    @Volatile private var finishingLive = false
    @Volatile private var wentLive = false
    private var endedSent = false
    private var reconnectPromptVisible = false
    @Volatile private var reconnectAttemptsExhausted = false
    @Volatile private var reconnectInProgress = false
    @Volatile private var encoderRetryScheduled = false
    @Volatile private var streamDisposed = false

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            projectionConsentPending = false
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                screenButton.isEnabled = true
                stopService(Intent(this, LiveProjectionService::class.java))
                return@registerForActivityResult
            }
            try {
                // Foreground service must acknowledge startForeground before
                // Android 14+ permits obtaining the MediaProjection token.
                val consent = result.data!!
                val resultCode = result.resultCode
                LiveProjectionService.startWithCallback(this) {
                    if (isFinishing || isDestroyed) {
                        stopService(Intent(this, LiveProjectionService::class.java))
                        return@startWithCallback
                    }
                    try {
                        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjection?.stop()
                        mediaProjection = manager.getMediaProjection(resultCode, consent)
                        val projection = mediaProjection ?: return@startWithCallback
                        LiveProjectionService.holdProjection(projection)
                        stream.changeVideoSource(ScreenSource(applicationContext, projection))
                        stream.getGlInterface().setCameraOrientation(0)
                        sharingScreen = true
                        cameraPaused = false
                        screenButton.isActivated = true
                        cameraButton.isActivated = false
                        screenButton.contentDescription = "Stop sharing screen"
                        cameraButton.contentDescription = "Turn camera off"
                        switchButton.isEnabled = false
                        status.text = "LIVE · Sharing screen"
                    } catch (e: Exception) {
                        mediaProjection?.stop()
                        mediaProjection = null
                        stopService(Intent(this, LiveProjectionService::class.java))
                        Toast.makeText(this, e.message ?: "Could not share screen", Toast.LENGTH_LONG).show()
                    } finally {
                        screenButton.isEnabled = true
                    }
                }
                return@registerForActivityResult
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
        // If the host dismisses the reconnect prompt, the status remains an
        // explicit way to recover without leaving or ending the broadcast.
        status.setOnClickListener {
            if (reconnectAttemptsExhausted) showReconnectPrompt()
        }
        viewers = findViewById(R.id.tvLiveViewers)
        attendance = findViewById(R.id.tvHostAttendance)
        attendance.setOnClickListener { showAttendanceDialog() }
        chat = findViewById(R.id.tvLiveChat)
        chatScroll = findViewById(R.id.hostChatScroll)
        olderChatButton = findViewById(R.id.btnLiveChatEarlier)
        olderChatButton.setOnClickListener { loadOlderHostChat() }
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

        stream = LiveProjectionService.attachStream(applicationContext, this)
        if (stream.isStreaming) {
            prepared = true
            wentLive = true
            sharingScreen = stream.videoSource is ScreenSource
        }
        // RootEncoder's Ktor TLS socket can collide with another Ktor ABI in the
        // app at runtime (ByteChannel NoSuchMethodError). RTMPS works with the
        // library's Java socket implementation and avoids that dependency path.
        stream.getStreamClient().setSocketType(SocketType.JAVA)
        // A brief mobile-data/Wi-Fi interruption should not terminate the
        // broadcast. RootEncoder reconnects the RTMPS socket without creating
        // a second Nelsen live session or restarting the camera encoder.
        stream.getStreamClient().setReTries(6)
        preview.holder.addCallback(this)
        if (wentLive) {
            status.text = "LIVE · Reconnected to studio"
            telemetryHandler.post(telemetryPoll)
        }

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
        if (!surfaceReady || isFinishing || finishingLive || stream.isStreaming) return

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
            micButton.isActivated = micMuted
            Toast.makeText(this, if (micMuted) "Microphone muted" else "Microphone on", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not change microphone", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreCamera() {
        stream.changeVideoSource(Camera2Source(applicationContext))
        LiveProjectionService.releaseProjection()
        mediaProjection = null
        stopService(Intent(this, LiveProjectionService::class.java))
        stream.getGlInterface().setCameraOrientation(90)
        sharingScreen = false
        cameraPaused = false
        screenButton.isActivated = false
        cameraButton.isActivated = false
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
                cameraButton.isActivated = true
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
                        if (streamDisposed || isFinishing || isDestroyed) return
                        viewers.text = "${data.concurrentViewers} watching"
                        if (chatTimeline.appendLatest(data)) renderHostChat(false)
                        refreshOlderHostButton()
                    }
                    override fun onFailure(
                        call: Call<LmsModels.LiveStateEnvelope>,
                        t: Throwable,
                    ) = Unit
                })
        }
    }

    private fun refreshOlderHostButton() {
        if (!::olderChatButton.isInitialized) return
        olderChatButton.visibility =
            if (chatTimeline.hasOlder()) android.view.View.VISIBLE else android.view.View.GONE
        olderChatButton.isEnabled = !olderChatLoading
        olderChatButton.text =
            if (olderChatLoading) "Loading messages…" else "Load earlier messages"
    }

    private fun renderHostChat(historyPrepended: Boolean) {
        if (!::chatScroll.isInitialized || !::chat.isInitialized) return
        val snapshot = chatTimeline.displayText()
        if (snapshot == lastChatSnapshot) return
        val previousHeight = chatScroll.getChildAt(0)?.height ?: 0
        val previousY = chatScroll.scrollY
        val nearBottom = !chatScroll.canScrollVertically(1)
        lastChatSnapshot = snapshot
        chat.text = snapshot
        chatScroll.post {
            if (streamDisposed || isFinishing || isDestroyed) return@post
            val newHeight = chatScroll.getChildAt(0)?.height ?: return@post
            if (historyPrepended) {
                chatScroll.scrollTo(0, previousY + (newHeight - previousHeight).coerceAtLeast(0))
            } else if (nearBottom) {
                chatScroll.scrollTo(0, newHeight)
            }
        }
    }

    private fun loadOlderHostChat() {
        if (olderChatLoading || !chatTimeline.hasOlder() ||
            streamDisposed || finishingLive || eventId.isBlank()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val cursor = chatTimeline.nextCursor()
        olderChatLoading = true
        refreshOlderHostButton()
        user.getIdToken(false).addOnSuccessListener { token ->
            if (streamDisposed || isFinishing || isDestroyed) {
                olderChatLoading = false
                return@addOnSuccessListener
            }
            ApiClient.getLmsService()
                .liveChatHistory("Bearer ${token.token}", eventId, cursor, 50)
                .enqueue(object : Callback<LmsModels.LiveChatHistoryEnvelope> {
                    override fun onResponse(
                        call: Call<LmsModels.LiveChatHistoryEnvelope>,
                        response: Response<LmsModels.LiveChatHistoryEnvelope>,
                    ) {
                        if (streamDisposed || isFinishing || isDestroyed) return
                        olderChatLoading = false
                        val page = if (response.isSuccessful) response.body()?.data else null
                        if (page != null) {
                            if (chatTimeline.prependHistory(page)) renderHostChat(true)
                        } else {
                            Toast.makeText(this@GoLiveActivity,
                                "Could not load earlier messages", Toast.LENGTH_SHORT).show()
                        }
                        refreshOlderHostButton()
                    }
                    override fun onFailure(
                        call: Call<LmsModels.LiveChatHistoryEnvelope>,
                        t: Throwable,
                    ) {
                        if (streamDisposed || isFinishing || isDestroyed) return
                        olderChatLoading = false
                        refreshOlderHostButton()
                        Toast.makeText(this@GoLiveActivity,
                            "Could not load earlier messages", Toast.LENGTH_SHORT).show()
                    }
                })
        }.addOnFailureListener {
            olderChatLoading = false
            if (!streamDisposed && !isFinishing && !isDestroyed) refreshOlderHostButton()
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
                            if (names.isNotBlank()) System.lineSeparator() + names else ""
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
                            Toast.makeText(this@GoLiveActivity, if (response.code() == 429) "Please wait a moment before sending another message" else "Message not sent (${response.code()})", Toast.LENGTH_SHORT).show()
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
        LiveProjectionService.releaseStream()
        showEndReport()
    }

    private fun showEndReport() {
        val rows = attendanceRows.sortedByDescending { it.watchSeconds }
        val totalSeconds = rows.sumOf { it.watchSeconds.toLong() }
        val details = rows.joinToString("\n") {
            val name = it.displayName?.takeIf { name -> name.isNotBlank() } ?: "Viewer"
            "$name · ${it.watchSeconds / 60}m ${it.watchSeconds % 60}s"
        }
        AlertDialog.Builder(this)
            .setTitle("Live attendance report")
            .setMessage("${rows.size} attendees · ${totalSeconds / 60} total watch minutes\n\n" +
                details.ifBlank { "No attendance recorded" })
            .setPositiveButton("Done") { _, _ -> finish() }
            .setCancelable(false)
            .show()
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
                    ) {
                        if (response.isSuccessful) return
                        android.util.Log.w(
                            "NelsenLive", "Hub live status update failed: HTTP ${response.code()}"
                        )
                        // A provider-side end is terminal for this broadcast.
                        // Do not keep automatically reconnecting an RTMPS
                        // stream whose Hub session can no longer become live.
                        val terminalConflict = response.code() == 409 &&
                            (response.errorBody()?.string()?.contains("ended live session", ignoreCase = true) == true)
                        if (terminalConflict && liveStatus == "live" &&
                            !finishingLive && !streamDisposed && !isFinishing && !isDestroyed
                        ) {
                            finishingLive = true
                            reconnectAttemptsExhausted = true
                            try {
                                if (::stream.isInitialized && stream.isStreaming) stream.stopStream()
                            } catch (error: Exception) {
                                android.util.Log.w("NelsenLive", "Stop after upstream end failed", error)
                            }
                            status.text = "Session ended upstream"
                            AlertDialog.Builder(this@GoLiveActivity)
                                .setTitle("Live session has ended")
                                .setMessage(
                                    "The broadcast has ended on the streaming service. " +
                                    "It cannot reconnect to the same session. " +
                                    "Close this studio and start a new live session."
                                )
                                .setPositiveButton("Close studio") { _, _ -> finish() }
                                .setCancelable(false)
                                .show()
                        }
                    }

                    override fun onFailure(
                        call: Call<LmsModels.HubEventEnvelope>,
                        t: Throwable,
                    ) {
                        android.util.Log.w("NelsenLive", "Hub status sync unavailable", t)
                        // Transient API failures are retried on each confirmed
                        // RTMPS connection success, not treated as an end.
                    }
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
        if (streamDisposed || finishingLive || isFinishing || isDestroyed) return
        wentLive = true
        reconnectAttemptsExhausted = false
        reconnectInProgress = false
        encoderRetryScheduled = false
        // Reconcile the Nelsen event after reconnect too: the first API status
        // update may have failed while the device was offline. The server has
        // an atomic once-only notification claim and monotonic live state.
        markNelsenLiveStatus("live")
        runOnUiThread {
            if (finishingLive || isDestroyed) return@runOnUiThread
            telemetryHandler.removeCallbacks(telemetryPoll)
            telemetryHandler.post(telemetryPoll)
            status.text = if (sharingScreen) "LIVE · Sharing screen" else "LIVE · Streaming"
            endButton.isEnabled = true
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        // Connection status is enough for the host UI; bitrate remains internal.
    }

    override fun onConnectionFailed(reason: String) {
        if (streamDisposed || finishingLive || isFinishing || isDestroyed || reconnectAttemptsExhausted) return
        val retrying = try {
            // RootEncoder handles re-establishing the stream socket and bounds
            // retries through setReTries(6). Do not mark the Hub event ended.
            stream.getStreamClient().reTry(5_000L, reason)
        } catch (e: Exception) {
            android.util.Log.w("NelsenLive", "RTMPS reconnect attempt failed", e)
            false
        }
        reconnectInProgress = retrying
        encoderRetryScheduled = retrying
        runOnUiThread {
            if (streamDisposed || finishingLive || isFinishing || isDestroyed) return@runOnUiThread
            if (retrying) {
                status.text = "Connection interrupted · Reconnecting…"
            } else {
                reconnectAttemptsExhausted = true
                status.text = "Connection lost · Tap reconnect"
                showReconnectPrompt()
            }
        }
    }

    private fun showReconnectPrompt() {
        if (streamDisposed || reconnectPromptVisible || finishingLive || isFinishing || isDestroyed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) return
        reconnectPromptVisible = true
        AlertDialog.Builder(this)
            .setTitle("Connection interrupted")
            .setMessage("Nelsen Live is still scheduled as active. Check your network and reconnect without ending the session.")
            .setPositiveButton("Reconnect") { _, _ ->
                reconnectPromptVisible = false
                retryHostConnection()
            }
            .setNegativeButton("Later") { _, _ -> reconnectPromptVisible = false }
            .setOnCancelListener { reconnectPromptVisible = false }
            .show()
    }

    private fun retryHostConnection() {
        if (streamDisposed || finishingLive || isFinishing || isDestroyed) return
        reconnectAttemptsExhausted = false
        reconnectInProgress = true
        encoderRetryScheduled = false
        try {
            if (stream.isStreaming) stream.stopStream()
            stream.getStreamClient().setReTries(6)
            status.text = "Reconnecting to live…"
            // The encoder was already prepared. Restart only the RTMPS socket.
            if (prepared) stream.startStream(ingestUrl) else maybeStartLive()
        } catch (e: Exception) {
            reconnectInProgress = false
            reconnectAttemptsExhausted = true
            android.util.Log.e("NelsenLive", "Could not restart RTMPS", e)
            status.text = "Could not reconnect"
            showReconnectPrompt()
        }
    }

    override fun onDisconnect() {
        // RootEncoder may report a clean socket disconnect without calling
        // onConnectionFailed (for example when upstream closes RTMPS). Cover
        // that path too, but never restart an explicitly ended host session.
        if (!wentLive || streamDisposed || finishingLive || reconnectInProgress ||
            reconnectAttemptsExhausted || isFinishing || isDestroyed) return
        reconnectInProgress = true
        runOnUiThread {
            if (streamDisposed || finishingLive || isFinishing || isDestroyed) return@runOnUiThread
            status.text = "Connection interrupted · Reconnecting…"
            telemetryHandler.postDelayed({
                if (streamDisposed || finishingLive || isFinishing || isDestroyed) return@postDelayed
                // A connection-success callback may have already recovered
                // the socket; do not interrupt that healthy broadcast.
                if (reconnectInProgress && !encoderRetryScheduled) retryHostConnection()
            }, 3_000L)
        }
    }

    override fun onAuthError() {
        // An invalid stream key is not a transient network error. Stop
        // automatic retries; the host can leave safely without false "ended".
        reconnectAttemptsExhausted = true
        runOnUiThread {
            if (!streamDisposed && !finishingLive && !isDestroyed) {
                status.text = "Live authorization failed · Reconnect channel"
                Toast.makeText(this, "Live connection rejected. Check the school channel connection.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onAuthSuccess() {
        // The live provider uses the stream endpoint/key rather than RTMP user/password auth.
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !sharingScreen &&
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && reconnectAttemptsExhausted && !streamDisposed && !finishingLive &&
            ::status.isInitialized
        ) showReconnectPrompt()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !sharingScreen && wentLive && stream.isStreaming) {
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
        streamDisposed = true
        reconnectAttemptsExhausted = true
        telemetryHandler.removeCallbacks(telemetryPoll)
        try {
            // This Activity can finish before the encoder is created (for
            // example an invalid endpoint or denied permissions).
            if (::stream.isInitialized) {
                if (!stream.isStreaming || finishingLive) LiveProjectionService.releaseStream()
                else LiveProjectionService.detachObserver(this)
            }
            if (finishingLive || !::stream.isInitialized || !stream.isStreaming) {
                mediaProjection?.stop()
                mediaProjection = null
                stopService(Intent(this, LiveProjectionService::class.java))
            }
        } catch (error: Exception) {
            android.util.Log.w("NelsenLive", "Studio cleanup failed", error)
        }
        // An Activity can be destroyed during an Android lifecycle change,
        // memory pressure or PiP transition. Ending the Hub broadcast here
        // used to mark otherwise recoverable streams as ended mid-session.
        // Only the host's confirmed End live action sends an "ended" update;
        // upstream sync handles an actual provider-ended broadcast.
        super.onDestroy()
    }
}
