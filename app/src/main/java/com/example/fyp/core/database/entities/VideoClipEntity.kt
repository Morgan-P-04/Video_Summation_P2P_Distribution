package com.example.fyp.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_clips")
data class VideoClipEntity(
    @PrimaryKey val clipId: String,
    val localURI: String,
    val finalisedVideoId: String,
    val sequence: Int,
    val startTime: Int,
    val endTime: Int
)