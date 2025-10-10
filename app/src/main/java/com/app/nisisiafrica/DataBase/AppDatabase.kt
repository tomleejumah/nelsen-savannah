package com.app.nisisiafrica.DataBase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.app.nisisiafrica.data.local.Dao.UserDao
import com.app.nisisiafrica.data.Model.UserData

@Database(entities = [UserData::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

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
                ).build().also { INSTANCE = it }
            }
        }
    }
}
