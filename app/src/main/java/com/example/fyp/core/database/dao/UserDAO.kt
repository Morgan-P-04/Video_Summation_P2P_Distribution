package com.example.fyp.core.database.dao

import androidx.room.*
import com.example.fyp.core.database.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // Fetches the user data and observes it for changes in the UI
    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    // Inserts or updates the user profile
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: UserEntity)

    // Currently Sharing logic updates
    @Query("UPDATE users SET currentlySharing = :isSharing WHERE userId = :id")
    suspend fun updateSharingStatus(id: String, isSharing: Boolean)
}