package com.app.nisisiafrica.DataBase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.app.nisisiafrica.data.Model.ChatMessageEntity
import com.app.nisisiafrica.data.local.Dao.UserDao
import com.app.nisisiafrica.data.Model.UserData
import com.app.nisisiafrica.data.local.Dao.ChatMessageDao

@Database(entities = [UserData::class, ChatMessageEntity::class],
    version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "main_database"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }

    }

}
