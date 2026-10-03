package com.app.nisisiafrica.data.Repository

import android.content.Context
import com.app.nisisiafrica.DataBase.AppDatabase
import com.app.nisisiafrica.data.Model.LmsModels
import com.app.nisisiafrica.data.local.Entity.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LmsOfflineRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).lmsCacheDao()
    private val gson = Gson()

    suspend fun cacheTrack(uid: String, track: LmsModels.TrackCard, schoolId: String = "") = withContext(Dispatchers.IO) {
        val id = track.trackId ?: track.courseId ?: return@withContext
        dao.putTracks(listOf(LmsTrackCacheEntity(uid,id,schoolId,gson.toJson(track),track.enrolled,System.currentTimeMillis())))
    }

    suspend fun cacheTracks(uid:String,tracks:List<LmsModels.TrackCard>,schoolId:String="") = withContext(Dispatchers.IO) {
        val now=System.currentTimeMillis()
        dao.putTracks(tracks.mapNotNull { track ->
            val id=track.trackId ?: track.courseId ?: return@mapNotNull null
            LmsTrackCacheEntity(uid,id,schoolId,gson.toJson(track),track.enrolled,now)
        })
    }

    suspend fun cachedTracks(uid:String,schoolId:String=""):List<LmsModels.TrackCard> = withContext(Dispatchers.IO) {
        val rows=if(schoolId.isBlank()) dao.tracks(uid) else dao.schoolTracks(uid,schoolId)
        rows.map { gson.fromJson(it.payloadJson,LmsModels.TrackCard::class.java) }
    }

    suspend fun cachedTrack(uid:String, trackId:String): LmsModels.TrackCard? = withContext(Dispatchers.IO) {
        dao.track(uid,trackId)?.let { gson.fromJson(it.payloadJson,LmsModels.TrackCard::class.java) }
    }

    suspend fun cacheModules(uid:String, modules:List<LmsModels.ModuleDto>) = withContext(Dispatchers.IO) {
        val now=System.currentTimeMillis()
        dao.putModules(modules.mapNotNull { m -> if(m.moduleId.isNullOrBlank()||m.trackId.isNullOrBlank()) null else LmsModuleCacheEntity(uid,m.moduleId,m.trackId,gson.toJson(m),now) })
    }

    suspend fun cachedModules(uid:String,trackId:String):List<LmsModels.ModuleDto> = withContext(Dispatchers.IO) {
        dao.modules(uid,trackId).map { gson.fromJson(it.payloadJson,LmsModels.ModuleDto::class.java) }
    }

    suspend fun cacheLessons(uid:String, lessons:List<LmsModels.LessonDto>) = withContext(Dispatchers.IO) {
        val now=System.currentTimeMillis()
        dao.putLessons(lessons.mapNotNull { l -> if(l.lessonId.isNullOrBlank()||l.trackId.isNullOrBlank()) null else LmsLessonCacheEntity(uid,l.lessonId,l.moduleId?:"",l.trackId,gson.toJson(l),now) })
    }

    suspend fun cachedModuleLessons(uid:String,moduleId:String):List<LmsModels.LessonDto> = withContext(Dispatchers.IO) {
        dao.moduleLessons(uid,moduleId).map { gson.fromJson(it.payloadJson,LmsModels.LessonDto::class.java) }
    }

    suspend fun cachedLesson(uid:String,lessonId:String):LmsModels.LessonDto? = withContext(Dispatchers.IO) {
        dao.lesson(uid,lessonId)?.let { gson.fromJson(it.payloadJson,LmsModels.LessonDto::class.java) }
    }

    suspend fun mergeServerProgress(uid:String,p:LmsModels.Progress) = mergeProgress(uid,p,false,null)

    suspend fun mergeServerProgressMap(uid:String,trackId:String,map:Map<String,Any>?) {
        if(map==null) return
        for((lessonId,value) in map) {
            val row=value as? Map<*,*> ?: continue
            fun number(name:String)= (row[name] as? Number)?.toFloat() ?: 0f
            val p=LmsModels.Progress().apply {
                this.lessonId=lessonId
                this.trackId=(row["trackId"] as? String) ?: trackId
                this.moduleId=row["moduleId"] as? String
                opened=(row["opened"] as? Boolean) ?: false
                contentPct=number("contentPct"); quizPct=number("quizPct")
                assignmentPct=number("assignmentPct"); lessonPercent=number("lessonPercent")
                trackPercent=number("trackPercent"); modulePercent=number("modulePercent")
                status=row["status"] as? String; lastPlatform=row["lastPlatform"] as? String
                updatedAt=(row["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            }
            mergeServerProgress(uid,p,false,null)
        }
    }

    suspend fun queueLocalProgress(uid:String,trackId:String,lessonId:String,body:LmsModels.ProgressBody) {
        val p=LmsModels.Progress().apply {
            this.trackId=trackId; this.lessonId=lessonId; opened=body.opened
            contentPct=body.contentPct?:0f; quizPct=body.quizPct?:0f
            lessonPercent=maxOf(contentPct,quizPct); updatedAt=System.currentTimeMillis()
        }
        mergeProgress(uid,p,true,body)
    }

    private suspend fun mergeProgress(uid:String,p:LmsModels.Progress,pending:Boolean,body:LmsModels.ProgressBody?) = withContext(Dispatchers.IO) {
        if(p.lessonId.isNullOrBlank()||p.trackId.isNullOrBlank()) return@withContext
        val json=gson.toJson(p); val pendingJson=body?.let(gson::toJson)?:""
        val changed=dao.advanceProgress(uid,p.lessonId,p.lessonPercent,p.contentPct,p.quizPct,p.assignmentPct,p.opened,json,pendingJson,pending,p.updatedAt)
        if(changed==0) dao.putProgress(LmsProgressCacheEntity(uid,p.lessonId,p.trackId,json,pendingJson,p.lessonPercent,p.contentPct,p.quizPct,p.assignmentPct,p.opened,pending,p.updatedAt))
    }

    suspend fun cachedProgress(uid:String,trackId:String):Map<String,Float> = withContext(Dispatchers.IO) {
        dao.progress(uid,trackId).associate { it.lessonId to it.lessonPercent }
    }
    suspend fun pruneStaleCache(uid:String) = withContext(Dispatchers.IO) {
        // Keep enrolled study content indefinitely for offline access. Only non-enrolled
        // catalog/content older than 30 days is removed.
        val before=System.currentTimeMillis() - 30L*24*60*60*1000
        dao.pruneLessons(uid,before)
        dao.pruneModules(uid,before)
        dao.pruneCatalog(uid,before)
    }
    suspend fun pendingProgress(uid:String)=withContext(Dispatchers.IO){dao.pendingProgress(uid)}
    suspend fun markProgressSynced(uid:String,lessonId:String)=withContext(Dispatchers.IO){dao.markProgressSynced(uid,lessonId)}
    fun progressBody(json:String):LmsModels.ProgressBody = gson.fromJson(json,LmsModels.ProgressBody::class.java)
}
