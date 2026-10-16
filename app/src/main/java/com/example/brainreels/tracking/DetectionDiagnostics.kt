package com.example.brainreels.tracking

import com.example.brainreels.data.Confidence
import com.example.brainreels.data.SourceApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticEntry(
    val timeMs: Long,
    val outcome: Outcome,
    val message: String,
    val confidence: Confidence?,
    val app: SourceApp?
)

/** In-memory explanation of why events were counted, rejected, or uncertain. Never persisted, never sent anywhere. */
object DetectionDiagnostics {
    private const val MAX = 60
    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    private val _connected = MutableStateFlow(false)

    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()
    val serviceConnected: StateFlow<Boolean> = _connected.asStateFlow()

    fun record(r: DetectionResult) {
        val e = DiagnosticEntry(r.timeMs, r.outcome, r.reason, r.confidence, r.source)
        _entries.value = (listOf(e) + _entries.value).take(MAX)
    }

    fun setConnected(connected: Boolean) { _connected.value = connected }
    fun clear() { _entries.value = emptyList() }
}
