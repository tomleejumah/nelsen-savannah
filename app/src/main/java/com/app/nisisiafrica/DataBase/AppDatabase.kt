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

@Database(entities = [UserData::class, ChatMessageEntity::class],
    version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun chatMessageDao(): ChatMessageDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }

    }

}
