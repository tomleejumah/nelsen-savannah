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
            val pending = repo.pendingProgress(user.uid).filter { row -> row.pendingBodyJson.isNotBlank() }
            if (pending.isNotEmpty()) {
                val items = pending.map { row ->
                    com.app.nisisiafrica.data.Model.LmsModels.ProgressSyncItem(
                        row.lessonId, repo.progressBody(row.pendingBodyJson)
                    )
                }
                // Deterministic for this queued snapshot: retries send the same key/body.
                val snapshot = pending.joinToString("-") { row -> "${row.lessonId}:${row.updatedAt}" }
                val idempotencyKey = "android-" + user.uid.take(16) + "-" + snapshot.hashCode().toUInt().toString(16)
                val response = ApiClient.getLmsService().syncProgress(
                    "Bearer $token", idempotencyKey,
                    com.app.nisisiafrica.data.Model.LmsModels.ProgressSyncBody(items)
                ).execute()
                if (!response.isSuccessful) {
                    if (response.code() == 401 || response.code() == 408 || response.code() == 429 || response.code() >= 500) return@withContext Result.retry()
                    return@withContext Result.failure()
                }
                response.body()?.data?.results?.forEach { result ->
                    result.progress?.let { progress -> repo.mergeServerProgress(user.uid, progress) }
                }
                pending.forEach { row -> repo.markProgressSynced(user.uid, row.lessonId) }
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
