package com.app.nisisiafrica

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.app.nisisiafrica.data.Model.LmsModels
import com.app.nisisiafrica.data.remote.ApiClient
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.pedro.common.ConnectChecker
import com.pedro.common.socket.base.SocketType
import com.pedro.encoder.input.sources.video.Camera2Source
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
    private lateinit var endButton: MaterialButton
    private lateinit var switchButton: MaterialButton
    private lateinit var stream: GenericStream

    private var ingestUrl = ""
    private var eventId = ""
    private var youtubeUrl = ""
    private var surfaceReady = false
    private var prepared = false
    private var finishingLive = false
    private var wentLive = false
    private var endedSent = false

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
        endButton = findViewById(R.id.btnEndLive)
        switchButton = findViewById(R.id.btnSwitchCamera)
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
        try {
            if (stream.isStreaming) stream.stopStream()
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
