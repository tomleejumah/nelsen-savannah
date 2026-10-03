package com.app.nisisiafrica.Utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.app.nisisiafrica.BuildConfig
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.LmsModels
import com.app.nisisiafrica.data.remote.ApiClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object AppUpdateBottomSheet {
    private const val TAG = "AppUpdateBottomSheet"

    fun show(activity: Activity, release: LmsModels.AppReleaseDto) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_app_update, null)

        val txtVersion = view.findViewById<TextView>(R.id.txtUpdateVersion)
        val txtSize = view.findViewById<TextView>(R.id.txtUpdateSize)
        val btnUpdate = view.findViewById<Button>(R.id.btnUpdateNow)
        val btnLater = view.findViewById<Button>(R.id.btnUpdateLater)
        val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
        val txtProgress = view.findViewById<TextView>(R.id.txtUpdateProgress)

        val versionName = release.versionName ?: "Latest"
        val versionCode = release.versionCode ?: 0
        txtVersion.text = "Version $versionName (Build $versionCode)"

        val sizeMb = release.sizeBytes?.let { bytes ->
            val mb = bytes.toDouble() / (1024 * 1024)
            String.format("%.1f MB", mb)
        } ?: "New build"
        txtSize.text = sizeMb

        var downloadedFile: File? = null
        var isDownloading = false

        btnLater.setOnClickListener {
            if (!isDownloading) {
                dialog.dismiss()
            }
        }

        btnUpdate.setOnClickListener {
            if (downloadedFile != null && downloadedFile!!.exists()) {
                promptInstall(activity, downloadedFile!!)
                return@setOnClickListener
            }

            if (isDownloading) return@setOnClickListener

            isDownloading = true
            btnLater.visibility = View.GONE
            btnUpdate.isEnabled = false
            btnUpdate.text = "Starting download..."
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true
            txtProgress.visibility = View.VISIBLE
            txtProgress.text = "Preparing download..."

            fetchDownloadUrlAndStart(activity, release, object : DownloadCallback {
                override fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long) {
                    activity.runOnUiThread {
                        progressBar.isIndeterminate = false
                        progressBar.progress = percent
                        txtProgress.text = "Downloading... $percent%"
                        btnUpdate.text = "Downloading ($percent%)"
                    }
                }

                override fun onSuccess(file: File) {
                    activity.runOnUiThread {
                        isDownloading = false
                        downloadedFile = file
                        progressBar.progress = 100
                        txtProgress.text = "Download complete!"
                        btnUpdate.isEnabled = true
                        btnUpdate.text = "Install Now"
                        btnLater.visibility = View.VISIBLE
                        btnLater.text = "Dismiss"

                        promptInstall(activity, file)
                    }
                }

                override fun onError(errorMsg: String) {
                    activity.runOnUiThread {
                        isDownloading = false
                        btnLater.visibility = View.VISIBLE
                        btnUpdate.isEnabled = true
                        btnUpdate.text = "Retry Update"
                        progressBar.visibility = View.GONE
                        txtProgress.text = "Download failed: $errorMsg"
                        Toast.makeText(activity, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }

        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.show()
    }

    private interface DownloadCallback {
        fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long)
        fun onSuccess(file: File)
        fun onError(errorMsg: String)
    }

    private fun fetchDownloadUrlAndStart(
        activity: Activity,
        release: LmsModels.AppReleaseDto,
        callback: DownloadCallback
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            user.getIdToken(false).addOnSuccessListener { tokenResult ->
                val bearer = "Bearer ${tokenResult.token}"
                ApiClient.getLmsService().getAppDownloadUrl(bearer)
                    .enqueue(object : Callback<LmsModels.AppReleaseEnvelope> {
                        override fun onResponse(
                            call: Call<LmsModels.AppReleaseEnvelope>,
                            response: Response<LmsModels.AppReleaseEnvelope>
                        ) {
                            val url = response.body()?.data?.downloadUrl
                            if (response.isSuccessful && !url.isNullOrBlank()) {
                                startFileDownload(activity, url, callback)
                            } else {
                                val fallbackUrl = "https://api.nelsen-savannah.co.ke/lms/app/android/download"
                                startFileDownload(activity, fallbackUrl, callback)
                            }
                        }

                        override fun onFailure(call: Call<LmsModels.AppReleaseEnvelope>, t: Throwable) {
                            val fallbackUrl = "https://api.nelsen-savannah.co.ke/lms/app/android/download"
                            startFileDownload(activity, fallbackUrl, callback)
                        }
                    })
            }.addOnFailureListener {
                val fallbackUrl = "https://api.nelsen-savannah.co.ke/lms/app/android/download"
                startFileDownload(activity, fallbackUrl, callback)
            }
        } else {
            val fallbackUrl = "https://api.nelsen-savannah.co.ke/lms/app/android/download"
            startFileDownload(activity, fallbackUrl, callback)
        }
    }

    private fun startFileDownload(
        context: Context,
        downloadUrl: String,
        callback: DownloadCallback
    ) {
        Thread {
            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    callback.onError("Server returned code ${response.code}")
                    return@Thread
                }

                val body = response.body
                if (body == null) {
                    callback.onError("Empty response body")
                    return@Thread
                }

                val totalLength = body.contentLength()
                val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                if (!destDir.exists()) destDir.mkdirs()

                val apkFile = File(destDir, "nelsen-savannah-update.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (totalLength > 0) {
                        val progress = ((totalBytesRead * 100) / totalLength).toInt()
                        if (progress > lastProgress) {
                            lastProgress = progress
                            callback.onProgress(progress, totalBytesRead, totalLength)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                callback.onSuccess(apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                callback.onError(e.message ?: "Unknown error")
            }
        }.start()
    }

    fun promptInstall(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "Please allow installation from unknown sources for Nelsen Savannah",
                        Toast.LENGTH_LONG
                    ).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(context, "Could not launch package installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
