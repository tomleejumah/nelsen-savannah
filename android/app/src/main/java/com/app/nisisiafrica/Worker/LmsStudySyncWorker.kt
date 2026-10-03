package com.app.nisisiafrica.Worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.nisisiafrica.data.Repository.LmsOfflineRepository
import com.app.nisisiafrica.data.remote.ApiClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext

class LmsStudySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext Result.success()
        val token = suspendCancellableCoroutine<String?> { cont ->
            user.getIdToken(true)
                .addOnSuccessListener { cont.resume(it.token) }
                .addOnFailureListener { cont.resume(null) }
        }
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
            // Pull authoritative progress for every locally cached track touched by
            // pending work. Merge is monotonic, so server refresh cannot move progress backwards.
            val trackIds = repo.cachedTrackIds(user.uid)
            for (trackId in trackIds) {
                val pull = ApiClient.getLmsService().myProgress("Bearer $token", trackId).execute()
                if (pull.isSuccessful) {
                    repo.mergeServerProgressMap(user.uid, trackId, pull.body()?.data?.byLessonId)
                }
            }
            repo.pruneStaleCache(user.uid)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
