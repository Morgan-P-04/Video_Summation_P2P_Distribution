package com.example.fyp.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscribed_videos")
data class SubscribedVideoEntity(
    @PrimaryKey val deliveryId: String,
    val videoId: String,
    val title: String,
    val subscriberId: String,
    val topicId: Int,
    val sourcePeerId: String,
    val receivedAt: Long,
    val TTL: Int,
    val deliveryState: String
)