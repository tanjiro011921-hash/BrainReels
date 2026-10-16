package com.example.brainreels.data

import com.example.brainreels.tracking.DetectionResult
import com.example.brainreels.tracking.Outcome
import com.example.brainreels.utils.CsvExporter
import com.example.brainreels.utils.DateUtils
import com.example.brainreels.utils.DayTotals
import com.example.brainreels.utils.Stats
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ReelRepository(
    private val dao: ReelDao,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    fun observeAll(): Flow<List<ReelEvent>> = dao.observeAll()

    /**
     * Persists a COUNTED or UNCERTAIN detection. Returns true if a new row was written,
     * false if the session key already existed (duplicate) or the result is not storable.
     */
    suspend fun recordDetection(r: DetectionResult): Boolean {
        if (r.outcome != Outcome.COUNTED && r.outcome != Outcome.UNCERTAIN) return false
        val key = r.sessionKey ?: return false
        val source = r.source ?: return false
        val counted = r.outcome == Outcome.COUNTED
        val row = ReelEvent(
            sourceApp = source.name,
            timestamp = r.timeMs,
            detectionMethod = DetectionMethod.ACCESSIBILITY_HEURISTIC,
            confidence = (r.confidence ?: Confidence.LOW).name,
            sessionKey = key,
            eventStatus = (if (counted) EventStatus.COUNTED else EventStatus.UNCERTAIN).name,
            manuallyAdjusted = false,
            delta = if (counted) 1 else 0,
            fingerprint = r.fingerprint,
            note = r.metadata
        )
        return dao.insert(row) != -1L
    }

    /** Manual corrections are separate, clearly marked rows; they never masquerade as detections. */
    suspend fun addManualAdjustment(
        source: SourceApp, delta: Int, note: String, timestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (delta == 0) return false
        return dao.insert(
            ReelEvent(
                sourceApp = source.name,
                timestamp = timestampMs,
                detectionMethod = DetectionMethod.MANUAL,
                confidence = Confidence.HIGH.name,
                sessionKey = "manual-${UUID.randomUUID()}",
                eventStatus = EventStatus.MANUAL_ADJUSTMENT.name,
                manuallyAdjusted = true,
                delta = delta,
                note = note.ifBlank { "Manual correction" }
            )
        ) != -1L
    }

    /** "Reset" never deletes history: it appends negative manual adjustments that bring each source to zero. */
    suspend fun resetDay(date: LocalDate, nowMs: Long = System.currentTimeMillis()) {
        val zone = zoneProvider()
        val start = DateUtils.dayStartMillis(date, zone)
        val end = DateUtils.dayEndMillis(date, zone)
        val totals = Stats.forDay(dao.getBetween(start, end), date, zone)
        val ts = nowMs.coerceIn(start, end - 1)
        if (totals.instagram > 0) addManualAdjustment(SourceApp.INSTAGRAM, -totals.instagram, "Reset day $date", ts)
        if (totals.facebook > 0) addManualAdjustment(SourceApp.FACEBOOK, -totals.facebook, "Reset day $date", ts)
    }

    suspend fun totalsForDay(date: LocalDate): DayTotals {
        val zone = zoneProvider()
        return Stats.forDay(
            dao.getBetween(DateUtils.dayStartMillis(date, zone), DateUtils.dayEndMillis(date, zone)), date, zone
        )
    }

    suspend fun latestCounted(): ReelEvent? = dao.latestCounted()

    suspend fun exportCsv(): String = CsvExporter.toCsv(dao.getAll(), zoneProvider())

    suspend fun deleteAll() = dao.deleteAll()
}
