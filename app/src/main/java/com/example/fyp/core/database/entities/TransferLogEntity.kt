package com.example.fyp.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_logs")
data class TransferLogEntity(
    @PrimaryKey val logId: String,
    val senderId: String,
    val receiverId: String,
    val videoId: String,
    val status: String,
    val timestamp: Long
)