package com.example.fyp.core.database.dao

import androidx.room.*
import com.example.fyp.core.database.entities.TopicEntity
import com.example.fyp.core.database.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDAO {
    @Query("SELECT * FROM topics")
    fun getTopics(): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(topic: TopicEntity)
    


}