package com.app.nisisiafrica.Utils

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.app.nisisiafrica.BuildConfig
import com.app.nisisiafrica.Worker.AppUpdateCheckWorker
import com.app.nisisiafrica.Worker.AppUpdateDownloadWorker
import com.app.nisisiafrica.data.Model.LmsModels
import com.app.nisisiafrica.data.remote.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS = "app_updates"
    private const val PREF_DOWNLOADED_VERSION = "downloaded_version"

    @JvmStatic @JvmOverloads
    fun checkForUpdates(activity: FragmentActivity, forceShow: Boolean = false, onUpdateRequired: (() -> Unit)? = null, onReady: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        ApiClient.getLmsService().appRelease.enqueue(object : Callback<LmsModels.AppReleaseEnvelope> {
            override fun onResponse(call: Call<LmsModels.AppReleaseEnvelope>, response: Response<LmsModels.AppReleaseEnvelope>) {
                if (activity.isFinishing || activity.isDestroyed) return
                val release = response.body()?.data
                val serverCode = release?.versionCode ?: 0
                if (release?.available != true || serverCode <= BuildConfig.VERSION_CODE) {
                    cleanupUpdateFiles(activity)
                    onReady?.invoke()
                    return
                }
                cleanupUpdateFiles(activity, serverCode)
                onUpdateRequired?.invoke()
                AppUpdateBottomSheet.show(activity, release, !forceShow, onReady)
            }
            override fun onFailure(call: Call<LmsModels.AppReleaseEnvelope>, t: Throwable) {
                Log.e(TAG, "Failed to check for app update", t)
                onReady?.invoke()
            }
        })
    }

    @JvmStatic
    fun scheduleBackgroundChecks(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("app-update-prefetch", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    @JvmStatic fun prefetch(context: Context, release: LmsModels.AppReleaseDto) {
        if ((release.versionCode ?: 0) > BuildConfig.VERSION_CODE) enqueueDownload(context, release)
    }

    fun enqueueDownload(context: Context, release: LmsModels.AppReleaseDto, restart: Boolean = false) {
        val code = release.versionCode ?: return
        if (code <= BuildConfig.VERSION_CODE) return
        val file = downloadedApk(context, code)
        if (file.exists() && verifySha256(file, release.sha256.orEmpty())) {
            markDownloaded(context, code); return
        }
        val input = Data.Builder().putInt(AppUpdateDownloadWorker.KEY_VERSION_CODE, code)
            .putString(AppUpdateDownloadWorker.KEY_SHA256, release.sha256.orEmpty()).build()
        val request = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "app-update-$code", if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request
        )
    }

    fun downloadedApk(context: Context, versionCode: Int): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        return File(dir, "nelsen-update-$versionCode.apk")
    }
    fun markDownloaded(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(PREF_DOWNLOADED_VERSION, versionCode).apply()
    }
    fun cleanupUpdateFiles(context: Context, keepVersionCode: Int? = null) {
        val keepApk = keepVersionCode?.let { "nelsen-update-$it.apk" }
        val keepPart = keepVersionCode?.let { "nelsen-update-$it.apk.part" }
        listOfNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), context.cacheDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                val updateFile = file.name.startsWith("nelsen-update-") || file.name == "nelsen-savannah-update.apk"
                if (updateFile && file.name != keepApk && file.name != keepPart) file.delete()
            }
        }
        if (keepVersionCode == null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun verifySha256(file: File, expected: String): Boolean {
        if (!file.exists()) return false
        if (expected.isBlank()) return file.length() > 0
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count <= 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected.trim(), ignoreCase = true)
    }
}
