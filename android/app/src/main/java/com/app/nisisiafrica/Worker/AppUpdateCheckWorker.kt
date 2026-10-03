package com.app.nisisiafrica.Worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.nisisiafrica.BuildConfig
import com.app.nisisiafrica.Utils.AppUpdateManager
import com.app.nisisiafrica.data.remote.ApiClient

class AppUpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val response = ApiClient.getLmsService().appRelease.execute()
            val release = response.body()?.data
            if (response.isSuccessful && release?.available == true) {
                val code = release.versionCode ?: 0
                if (code > BuildConfig.VERSION_CODE) {
                    AppUpdateManager.prefetch(applicationContext, release)
                } else {
                    AppUpdateManager.cleanupUpdateFiles(applicationContext)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
