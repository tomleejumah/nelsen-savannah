package com.app.nisisiafrica.Worker

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.nisisiafrica.Utils.AppUpdateManager
import com.app.nisisiafrica.data.remote.ApiClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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
            return Result.success()
        }

        return try {
            val user = FirebaseAuth.getInstance().currentUser
            val bearer = user?.getIdToken(false)?.await()?.token?.let { "Bearer $it" }
            val envelope = if (bearer != null) {
                ApiClient.getLmsService().getAppDownloadUrl(bearer).execute()
            } else null
            val signedUrl = envelope?.body()?.data?.downloadUrl
            val url = signedUrl?.takeIf { it.isNotBlank() }
                ?: "https://api.nelsen-savannah.co.ke/lms/app/android/download"

            val client = OkHttpClient()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return Result.retry()
                val body = response.body ?: return Result.retry()
                val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: applicationContext.cacheDir
                dir.mkdirs()
                val temp = File(dir, "nelsen-update-$versionCode.apk.part")
                val target = AppUpdateManager.downloadedApk(applicationContext, versionCode)
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
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
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_SHA256 = "sha256"
    }
}
