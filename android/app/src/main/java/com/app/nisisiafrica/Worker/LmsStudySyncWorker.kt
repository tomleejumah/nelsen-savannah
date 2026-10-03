package com.app.nisisiafrica.Worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.nisisiafrica.data.Repository.LmsOfflineRepository
import com.app.nisisiafrica.data.remote.ApiClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LmsStudySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext Result.success()
        val token = try { user.getIdToken(true).await().token } catch (_: Exception) { return@withContext Result.retry() }
        if (token.isNullOrBlank()) return@withContext Result.retry()
        val repo = LmsOfflineRepository(applicationContext)
        try {
            for (row in repo.pendingProgress(user.uid)) {
                if (row.pendingBodyJson.isBlank()) continue
                val response = ApiClient.getLmsService()
                    .patchProgress("Bearer $token", row.lessonId, repo.progressBody(row.pendingBodyJson))
                    .execute()
                if (!response.isSuccessful) {
                    if (response.code() == 401 || response.code() == 408 || response.code() == 429 || response.code() >= 500) return@withContext Result.retry()
                    continue // retain pending data; do not silently discard a rejected progress update
                }
                response.body()?.data?.progress?.let { repo.mergeServerProgress(user.uid, it) }
                repo.markProgressSynced(user.uid, row.lessonId)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
