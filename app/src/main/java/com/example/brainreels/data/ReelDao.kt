package com.example.brainreels.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    /** Returns the new row id, or -1 if the session key already exists (duplicate is ignored atomically). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ReelEvent): Long

    @Query("SELECT * FROM reel_events ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<ReelEvent>>

    @Query("SELECT * FROM reel_events WHERE timestamp >= :startMs AND timestamp < :endMs ORDER BY timestamp DESC, id DESC")
    fun observeBetween(startMs: Long, endMs: Long): Flow<List<ReelEvent>>

    @Query("SELECT * FROM reel_events WHERE timestamp >= :startMs AND timestamp < :endMs")
    suspend fun getBetween(startMs: Long, endMs: Long): List<ReelEvent>

    @Query("SELECT * FROM reel_events ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<ReelEvent>

    @Query("SELECT * FROM reel_events WHERE eventStatus = 'COUNTED' ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestCounted(): ReelEvent?

    @Query("SELECT COUNT(*) FROM reel_events WHERE sessionKey = :key")
    suspend fun countSessionKey(key: String): Int

    @Query("DELETE FROM reel_events")
    suspend fun deleteAll()
}
