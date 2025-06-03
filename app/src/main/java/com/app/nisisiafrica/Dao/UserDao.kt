package com.app.nisisiafrica.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

import com.app.nisisiafrica.Model.UserData
import io.reactivex.rxjava3.core.*

@Dao
interface UserDao {

        // Coroutines (for Kotlin)
        @Insert
        suspend fun insertUser(user: UserData)

        @Query("SELECT * FROM user_data WHERE id = :userId")
        suspend fun getUserById(userId: String): UserData?

        @Query("SELECT * FROM user_data")
        suspend fun getAllUsers(): List<UserData>

        @Query("DELETE FROM user_data")
        suspend fun deleteAllUsers()

        @Query("DELETE FROM user_data WHERE id = :userId")
        suspend fun deleteUserById(userId: String)

        // RxJava (for Java compatibility)
        @Insert
        fun insertUserRx(user: UserData): Completable

        @Query("SELECT * FROM user_data WHERE id = :userId")
        fun getUserByIdRx(userId: String): Maybe<UserData>

        @Query("SELECT * FROM user_data")
        fun getAllUsersRx(): Flowable<List<UserData>>

        @Update(onConflict = OnConflictStrategy.REPLACE)
        fun updateUserRx(user: UserData): Completable
        @Query("DELETE FROM user_data")
        fun deleteAllUsersRx(): Completable

        @Query("DELETE FROM user_data WHERE id = :userId")
        fun deleteUserByIdRx(userId: String): Completable

}