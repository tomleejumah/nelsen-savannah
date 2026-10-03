package com.app.nisisiafrica.data.local.Entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "lms_tracks",
    primaryKeys = ["uid", "trackId"],
    indices = [Index("uid"), Index("schoolId"), Index("enrolled")]
)
data class LmsTrackCacheEntity(
    val uid: String,
    val trackId: String,
    val schoolId: String = "",
    val payloadJson: String,
    val enrolled: Boolean,
    val cachedAt: Long
)

@Entity(
    tableName = "lms_modules",
    primaryKeys = ["uid", "moduleId"],
    indices = [Index("uid"), Index("trackId")]
)
data class LmsModuleCacheEntity(
    val uid: String,
    val moduleId: String,
    val trackId: String,
    val payloadJson: String,
    val cachedAt: Long
)

@Entity(
    tableName = "lms_lessons",
    primaryKeys = ["uid", "lessonId"],
    indices = [Index("uid"), Index("trackId"), Index("moduleId")]
)
data class LmsLessonCacheEntity(
    val uid: String,
    val lessonId: String,
    val moduleId: String,
    val trackId: String,
    val payloadJson: String,
    val cachedAt: Long
)

@Entity(
    tableName = "lms_progress",
    primaryKeys = ["uid", "lessonId"],
    indices = [Index("uid"), Index("trackId"), Index("pendingSync")]
)
data class LmsProgressCacheEntity(
    val uid: String,
    val lessonId: String,
    val trackId: String,
    val payloadJson: String,
    val pendingBodyJson: String = "",
    val lessonPercent: Float,
    val contentPct: Float,
    val quizPct: Float,
    val assignmentPct: Float,
    val opened: Boolean,
    val pendingSync: Boolean,
    val updatedAt: Long
)
