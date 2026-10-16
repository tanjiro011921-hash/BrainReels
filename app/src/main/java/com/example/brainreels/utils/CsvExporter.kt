package com.example.brainreels.utils

import com.example.brainreels.data.ReelEvent
import java.time.Instant
import java.time.ZoneId

object CsvExporter {
    const val HEADER =
        "id,timestamp_iso,local_date,source_app,detection_method,confidence,event_status,delta,manually_adjusted,session_key,note"

    fun toCsv(events: List<ReelEvent>, zone: ZoneId): String {
        val sb = StringBuilder(HEADER).append('\n')
        for (e in events.sortedBy { it.timestamp }) {
            sb.append(
                listOf(
                    e.id.toString(),
                    Instant.ofEpochMilli(e.timestamp).toString(),
                    DateUtils.localDate(e.timestamp, zone).toString(),
                    e.sourceApp, e.detectionMethod, e.confidence, e.eventStatus,
                    e.delta.toString(), e.manuallyAdjusted.toString(), e.sessionKey, e.note ?: ""
                ).joinToString(",") { escape(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    internal fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
