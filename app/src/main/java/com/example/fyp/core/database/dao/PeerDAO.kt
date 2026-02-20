package com.example.fyp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp.core.database.entities.PeerEntity

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers WHERE peerId = :id")
    suspend fun getPeerById(id: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: PeerEntity)
}