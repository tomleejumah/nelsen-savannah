package com.app.nisisiafrica.DataBase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.nisisiafrica.data.Model.ChatMessageEntity
import com.app.nisisiafrica.data.local.Dao.UserDao
import com.app.nisisiafrica.data.Model.UserData
import com.app.nisisiafrica.data.local.Dao.ChatMessageDao
import com.app.nisisiafrica.data.local.Dao.LmsCacheDao
import com.app.nisisiafrica.data.local.Entity.LmsTrackCacheEntity
import com.app.nisisiafrica.data.local.Entity.LmsModuleCacheEntity
import com.app.nisisiafrica.data.local.Entity.LmsLessonCacheEntity
import com.app.nisisiafrica.data.local.Entity.LmsProgressCacheEntity

@Database(entities = [UserData::class, ChatMessageEntity::class, LmsTrackCacheEntity::class, LmsModuleCacheEntity::class, LmsLessonCacheEntity::class, LmsProgressCacheEntity::class],
    version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun lmsCacheDao(): LmsCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 and v2 are structurally identical; v2 was a version bump with no schema change. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        /** Adds quoted-reply metadata and the delete tombstone flag to `messages`. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToSender TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToSnippet TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds additive LMS offline cache/sync tables. Existing user/chat data is untouched. */
        @JvmField
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS lms_tracks (uid TEXT NOT NULL, trackId TEXT NOT NULL, schoolId TEXT NOT NULL, payloadJson TEXT NOT NULL, enrolled INTEGER NOT NULL, cachedAt INTEGER NOT NULL, PRIMARY KEY(uid, trackId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_tracks_uid ON lms_tracks(uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_tracks_schoolId ON lms_tracks(schoolId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_tracks_enrolled ON lms_tracks(enrolled)")
                db.execSQL("CREATE TABLE IF NOT EXISTS lms_modules (uid TEXT NOT NULL, moduleId TEXT NOT NULL, trackId TEXT NOT NULL, payloadJson TEXT NOT NULL, cachedAt INTEGER NOT NULL, PRIMARY KEY(uid, moduleId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_modules_uid ON lms_modules(uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_modules_trackId ON lms_modules(trackId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS lms_lessons (uid TEXT NOT NULL, lessonId TEXT NOT NULL, moduleId TEXT NOT NULL, trackId TEXT NOT NULL, payloadJson TEXT NOT NULL, cachedAt INTEGER NOT NULL, PRIMARY KEY(uid, lessonId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_lessons_uid ON lms_lessons(uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_lessons_trackId ON lms_lessons(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_lessons_moduleId ON lms_lessons(moduleId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS lms_progress (uid TEXT NOT NULL, lessonId TEXT NOT NULL, trackId TEXT NOT NULL, payloadJson TEXT NOT NULL, lessonPercent REAL NOT NULL, contentPct REAL NOT NULL, quizPct REAL NOT NULL, assignmentPct REAL NOT NULL, opened INTEGER NOT NULL, pendingSync INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(uid, lessonId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_progress_uid ON lms_progress(uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_progress_trackId ON lms_progress(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lms_progress_pendingSync ON lms_progress(pendingSync)")
            }
        }

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "main_database"
                )
                    // No destructive fallback: losing the local cache would wipe
                    // chat history that has aged out of the Firestore sync window.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }

    }

}
