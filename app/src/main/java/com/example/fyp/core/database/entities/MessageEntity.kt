package com.example.fyp.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val receiverId: String,
    val messageType: String,
    val payload: String,
    val relatedVideoId: String,
    val relatedTopicId: Int,
    val sentAt: Long,
    val deliveryState: String
)