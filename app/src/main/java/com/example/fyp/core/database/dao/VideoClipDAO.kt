package com.example.fyp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp.core.database.entities.VideoClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoClipDao {
    @Query("SELECT * FROM video_clips WHERE finalisedVideoId = :videoId ORDER BY sequence ASC")
    fun getClipsForVideo(videoId: String): Flow<List<VideoClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: VideoClipEntity)

    @Delete
    suspend fun deleteClip(clip: VideoClipEntity)
}