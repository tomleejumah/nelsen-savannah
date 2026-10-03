package com.app.nisisiafrica.data.Repository

import android.content.Context
import com.app.nisisiafrica.data.Model.LmsModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LmsCacheBridge(context: Context) {
    private val repo = LmsOfflineRepository(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun moduleLessons(uid:String,moduleId:String,callback:(List<LmsModels.LessonDto>)->Unit) {
        scope.launch { val value=repo.cachedModuleLessons(uid,moduleId); withContext(Dispatchers.Main){callback(value)} }
    }
    fun lesson(uid:String,lessonId:String,callback:(LmsModels.LessonDto?)->Unit) {
        scope.launch { val value=repo.cachedLesson(uid,lessonId); withContext(Dispatchers.Main){callback(value)} }
    }
    fun saveLessons(uid:String,lessons:List<LmsModels.LessonDto>) { scope.launch { repo.cacheLessons(uid,lessons) } }
    fun queueProgress(uid:String,trackId:String,lessonId:String,body:LmsModels.ProgressBody) {
        scope.launch { repo.queueLocalProgress(uid,trackId,lessonId,body) }
    }
    fun mergeProgress(uid:String,progress:LmsModels.Progress) { scope.launch { repo.mergeServerProgress(uid,progress) } }
}
