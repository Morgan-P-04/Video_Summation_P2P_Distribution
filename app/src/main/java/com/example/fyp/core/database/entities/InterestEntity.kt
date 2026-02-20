package com.example.fyp.core.database.entities

import androidx.room.Entity

@Entity(tableName = "my_interests", primaryKeys = ["topicId", "userId"])
data class InterestEntity(
    val topicId: Int,
    val userId: String,
    val isSubscribed: Boolean,
    val priority: Int
)