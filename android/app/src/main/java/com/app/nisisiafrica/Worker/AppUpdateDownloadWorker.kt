package com.app.nisisiafrica.Worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.app.nisisiafrica.MainActivity
import com.app.nisisiafrica.R
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.app.nisisiafrica.Utils.AppUpdateManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AppUpdateDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val versionCode = inputData.getInt(KEY_VERSION_CODE, 0)
        val expectedSha = inputData.getString(KEY_SHA256).orEmpty()
        if (versionCode <= 0) return Result.failure()
        val target = AppUpdateManager.downloadedApk(applicationContext, versionCode)
        if (target.exists() && AppUpdateManager.verifySha256(target, expectedSha)) {
            setProgress(progressData(100))
            return Result.success()
        }
        val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: applicationContext.cacheDir
        dir.mkdirs()
        val temp = File(dir, "nelsen-update-$versionCode.apk.part")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()

        return try {
            setForeground(downloadForegroundInfo(0))
            var offset = temp.takeIf { it.exists() }?.length() ?: 0L
            val request = Request.Builder()
                .url(DOWNLOAD_URL)
                .apply { if (offset > 0L) header("Range", "bytes=$offset-") }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 416) return retryOrFail()
                if (response.code == 416) {
                    // A stale partial may be longer than the current release. Reset safely.
                    temp.delete()
                    return retryOrFail()
                }
                val body = response.body ?: return retryOrFail()
                val rangeStart = response.header("Content-Range")
                    ?.let { Regex("^bytes (\\d+)-\\d+/\\d+$").find(it)?.groupValues?.get(1)?.toLongOrNull() }
                val append = offset > 0L && response.code == 206 && rangeStart == offset
                if (response.code == 206 && !append) {
                    // Never append a mismatched or unverified byte range.
                    temp.delete()
                    return retryOrFail()
                }
                if (!append) offset = 0L // Server ignored Range and sent the full APK.
                val contentBytes = body.contentLength()
                val totalBytes = if (contentBytes > 0) offset + contentBytes else -1L
                var downloaded = offset
                var lastProgress = -1
                body.byteStream().use { input ->
                    FileOutputStream(temp, append).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            if (isStopped) return retryOrFail()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (totalBytes > 0L) {
                                val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                if (pct != lastProgress) {
                                    setProgress(progressData(pct))
                                    setForeground(downloadForegroundInfo(pct))
                                    lastProgress = pct
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (totalBytes > 0 && downloaded != totalBytes) return retryOrFail()
            }
            if (!AppUpdateManager.verifySha256(temp, expectedSha)) {
                // A checksum mismatch means the partial is unusable.
                temp.delete()
                return Result.failure(Data.Builder().putString(KEY_ERROR, "Downloaded APK verification failed").build())
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            AppUpdateManager.markDownloaded(applicationContext, versionCode)
            setProgress(progressData(100))
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Update interrupted; keeping partial APK for resume", e)
            retryOrFail()
        }
    }

    private fun downloadForegroundInfo(percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App update downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val launch = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            applicationContext, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.nelsen_icon)
            .setContentTitle("Downloading Nelsen Savannah update")
            .setContentText("$percent% downloaded")
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
            Result.failure(Data.Builder().putString(KEY_ERROR, "Download interrupted; partial file saved").build())
        } else Result.retry()

    private fun progressData(progress: Int): Data =
        Data.Builder().putInt(KEY_PROGRESS, progress.coerceIn(0, 100)).build()

    companion object {
        private const val TAG = "AppUpdateDownload"
        private const val CHANNEL_ID = "app_update_downloads"
        private const val NOTIFICATION_ID = 3071
        private const val DOWNLOAD_URL = "https://api.nelsen-savannah.co.ke/lms/app/android/download"
        private const val MAX_ATTEMPTS = 4
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_SHA256 = "sha256"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }
}
