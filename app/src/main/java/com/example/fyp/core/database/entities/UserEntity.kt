package com.example.fyp.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,
    val username: String,
    val topicId: Int,
    val publisherId: String,
    val subscriberId: String,
    val currentlySharing: Boolean
)