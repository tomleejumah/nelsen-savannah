package com.app.nisisiafrica.data.local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.nisisiafrica.data.local.Entity.LmsLessonCacheEntity
import com.app.nisisiafrica.data.local.Entity.LmsModuleCacheEntity
import com.app.nisisiafrica.data.local.Entity.LmsProgressCacheEntity
import com.app.nisisiafrica.data.local.Entity.LmsTrackCacheEntity

@Dao
interface LmsCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTracks(rows: List<LmsTrackCacheEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putModules(rows: List<LmsModuleCacheEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLessons(rows: List<LmsLessonCacheEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProgress(row: LmsProgressCacheEntity)

    @Query("SELECT * FROM lms_tracks WHERE uid=:uid AND enrolled=1 ORDER BY cachedAt DESC")
    suspend fun enrolledTracks(uid: String): List<LmsTrackCacheEntity>
    @Query("SELECT * FROM lms_tracks WHERE uid=:uid AND trackId=:trackId LIMIT 1")
    suspend fun track(uid: String, trackId: String): LmsTrackCacheEntity?
    @Query("SELECT * FROM lms_modules WHERE uid=:uid AND trackId=:trackId ORDER BY cachedAt DESC")
    suspend fun modules(uid: String, trackId: String): List<LmsModuleCacheEntity>
    @Query("SELECT * FROM lms_lessons WHERE uid=:uid AND trackId=:trackId")
    suspend fun lessons(uid: String, trackId: String): List<LmsLessonCacheEntity>
    @Query("SELECT * FROM lms_lessons WHERE uid=:uid AND lessonId=:lessonId LIMIT 1")
    suspend fun lesson(uid: String, lessonId: String): LmsLessonCacheEntity?
    @Query("SELECT * FROM lms_progress WHERE uid=:uid AND trackId=:trackId")
    suspend fun progress(uid: String, trackId: String): List<LmsProgressCacheEntity>
    @Query("SELECT * FROM lms_progress WHERE uid=:uid AND pendingSync=1 ORDER BY updatedAt ASC")
    suspend fun pendingProgress(uid: String): List<LmsProgressCacheEntity>

    @Query("""UPDATE lms_progress SET
        lessonPercent = MAX(lessonPercent, :lessonPercent),
        contentPct = MAX(contentPct, :contentPct),
        quizPct = MAX(quizPct, :quizPct),
        assignmentPct = MAX(assignmentPct, :assignmentPct),
        opened = CASE WHEN opened = 1 OR :opened = 1 THEN 1 ELSE 0 END,
        payloadJson = :payloadJson,
        pendingSync = CASE WHEN pendingSync = 1 OR :pendingSync = 1 THEN 1 ELSE 0 END,
        updatedAt = MAX(updatedAt, :updatedAt)
        WHERE uid=:uid AND lessonId=:lessonId""")
    suspend fun advanceProgress(uid:String, lessonId:String, lessonPercent:Float, contentPct:Float, quizPct:Float, assignmentPct:Float, opened:Boolean, payloadJson:String, pendingSync:Boolean, updatedAt:Long): Int

    @Query("UPDATE lms_progress SET pendingSync=0 WHERE uid=:uid AND lessonId=:lessonId")
    suspend fun markProgressSynced(uid:String, lessonId:String)

    @Query("DELETE FROM lms_tracks WHERE uid=:uid AND cachedAt < :before AND enrolled=0")
    suspend fun pruneCatalog(uid:String, before:Long)
}
