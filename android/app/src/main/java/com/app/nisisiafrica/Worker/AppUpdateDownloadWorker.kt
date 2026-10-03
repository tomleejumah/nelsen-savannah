package com.app.nisisiafrica.Worker

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.app.nisisiafrica.Utils.AppUpdateManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class AppUpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val versionCode = inputData.getInt(KEY_VERSION_CODE, 0)
        val expectedSha = inputData.getString(KEY_SHA256).orEmpty()
        if (versionCode <= 0) return Result.failure()

        AppUpdateManager.cleanupUpdateFiles(applicationContext, keepVersionCode = versionCode)
        val existing = AppUpdateManager.downloadedApk(applicationContext, versionCode)
        if (existing.exists() && AppUpdateManager.verifySha256(existing, expectedSha)) {
            setProgress(progressData(100))
            return Result.success()
        }

        return try {
            setProgress(progressData(0))
            val url = "https://api.nelsen-savannah.co.ke/lms/app/android/download"
            val client = OkHttpClient()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return Result.retry()
                val body = response.body ?: return Result.retry()
                val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: applicationContext.cacheDir
                dir.mkdirs()
                val temp = File(dir, "nelsen-update-$versionCode.apk.part")
                val target = AppUpdateManager.downloadedApk(applicationContext, versionCode)
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastProgress = -1

                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100L) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 99)
                                if (progress != lastProgress) {
                                    setProgress(progressData(progress))
                                    lastProgress = progress
                                }
                            }
                        }
                    }
                }
                if (!AppUpdateManager.verifySha256(temp, expectedSha)) {
                    temp.delete()
                    return Result.retry()
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
            AppUpdateManager.markDownloaded(applicationContext, versionCode)
            setProgress(progressData(100))
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun progressData(progress: Int): Data =
        Data.Builder().putInt(KEY_PROGRESS, progress.coerceIn(0, 100)).build()

    companion object {
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_SHA256 = "sha256"
        const val KEY_PROGRESS = "progress"
    }
}
