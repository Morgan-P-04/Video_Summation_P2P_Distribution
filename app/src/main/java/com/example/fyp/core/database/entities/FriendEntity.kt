package com.example.fyp.core.database.entities

import androidx.room.Entity

@Entity(tableName = "friends", primaryKeys = ["userId", "friendUserId"])
data class FriendEntity(
    val userId: String,
    val friendUserId: String,
    val friendName: String
)