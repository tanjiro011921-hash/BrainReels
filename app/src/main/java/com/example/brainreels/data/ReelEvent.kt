package com.example.brainreels.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per automatic observation or manual correction.
 * [sessionKey] is unique, so inserting the same session twice is a no-op (see ReelDao.insert).
 * Totals are always SUM(delta); manual corrections are never disguised as detections.
 */
@Entity(
    tableName = "reel_events",
    indices = [Index(value = ["sessionKey"], unique = true), Index(value = ["timestamp"])]
)
data class ReelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceApp: String,
    val timestamp: Long,
    val detectionMethod: String,
    val confidence: String,
    val sessionKey: String,
    val eventStatus: String,
    val manuallyAdjusted: Boolean = false,
    val delta: Int = 1,
    /** Hash of limited on-screen identity text (never raw text). Used for duplicate protection. */
    val fingerprint: String? = null,
    /** Short human-readable detection metadata / audit note. */
    val note: String? = null
)
