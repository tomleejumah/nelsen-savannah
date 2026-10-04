package com.app.nisisiafrica.Utils

import android.content.Context
import androidx.work.*
import com.app.nisisiafrica.Worker.LmsStudySyncWorker
import java.util.concurrent.TimeUnit

object LmsStudySync {
    private const val PERIODIC = "lms-study-sync"
    const val IMMEDIATE = "lms-study-sync-now"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<LmsStudySyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun request(context: Context) {
        val request = OneTimeWorkRequestBuilder<LmsStudySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP, request)
    }
}
