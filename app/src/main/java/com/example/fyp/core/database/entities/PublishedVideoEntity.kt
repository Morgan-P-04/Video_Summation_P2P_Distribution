package com.example.fyp.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "published_videos")
data class PublishedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val topicId: Int,
    val userId: String,
    val duration: Int,
    val createdAt: Long,
    val shareCount: Int,
    val localPath: String
)