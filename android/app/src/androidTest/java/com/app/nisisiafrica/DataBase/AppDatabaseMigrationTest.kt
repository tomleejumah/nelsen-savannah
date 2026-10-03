package com.app.nisisiafrica.DataBase

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val dbName = "lms-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4PreservesExistingDataAndCreatesLmsCache() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO user_data (id,email,userRole,displayName,firstName,lastName,photoUrl,bio,lastLogin) VALUES ('migration-user','test@example.com',NULL,NULL,'Test','User',NULL,NULL,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4).apply {
            query("SELECT id FROM user_data WHERE id='migration-user'").use { check(it.moveToFirst()) }
            for (table in listOf("lms_tracks","lms_modules","lms_lessons","lms_progress")) {
                query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { check(it.moveToFirst()) }
            }
            close()
        }
    }
}
