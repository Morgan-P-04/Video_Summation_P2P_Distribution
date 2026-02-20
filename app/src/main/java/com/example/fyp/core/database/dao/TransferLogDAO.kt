package com.example.fyp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.fyp.core.database.entities.TransferLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferLogDao {
    @Query("SELECT * FROM transfer_logs ORDER BY timestamp DESC")
    fun getLogs(): Flow<List<TransferLogEntity>>

    @Insert
    suspend fun insertLog(log: TransferLogEntity)
}