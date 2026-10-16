package com.example.brainreels.ui

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainreels.data.*
import com.example.brainreels.overlay.OverlayController
import com.example.brainreels.permissions.PermissionHelper
import com.example.brainreels.permissions.PermissionState
import com.example.brainreels.permissions.UsageSummary
import com.example.brainreels.tracking.DetectionDiagnostics
import com.example.brainreels.tracking.DiagnosticEntry
import com.example.brainreels.utils.DayClock
import com.example.brainreels.utils.DayTotals
import com.example.brainreels.utils.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class DashboardState(
    val today: DayTotals,
    val week: List<DayTotals>,
    val monthTotal: Int,
    val recent: List<ReelEvent>,
    val history: List<DayTotals>,
    val settings: AppSettings,
    val permissions: PermissionState,
    val diagnostics: List<DiagnosticEntry>,
    val serviceConnected: Boolean,
    val zone: ZoneId
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = ReelRepository(AppDatabase.get(app).reelDao())
    private val settingsRepo = SettingsRepository(app)
    private val permissions = MutableStateFlow(PermissionHelper.state(app))

    val usage = MutableStateFlow<UsageSummary?>(null)
    val message = MutableStateFlow<String?>(null)

    private val diag = combine(DetectionDiagnostics.entries, DetectionDiagnostics.serviceConnected) { e, c -> e to c }

    val state: StateFlow<DashboardState?> = combine(
        repo.observeAll(), DayClock.currentDay(app), settingsRepo.flow, permissions, diag
    ) { events, key, settings, perms, d ->
        DashboardState(
            today = Stats.forDay(events, key.date, key.zone),
            week = Stats.lastDays(events, key.date, 7, key.zone),
            monthTotal = Stats.monthToDate(events, key.date, key.zone),
            recent = events.take(12),
            history = Stats.daily(events, key.zone),
            settings = settings,
            permissions = perms,
            diagnostics = d.first,
            serviceConnected = d.second,
            zone = key.zone
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            combine(settingsRepo.flow, permissions) { s, p -> s.widgetEnabled && p.overlay }
                .distinctUntilChanged()
                .collect { show -> if (show) OverlayController.start(app) else OverlayController.stop(app) }
        }
    }

    /** Call from onResume: re-reads permission state after the user returns from Android Settings. */
    fun refresh() {
        permissions.value = PermissionHelper.state(app)
        viewModelScope.launch(Dispatchers.IO) { usage.value = PermissionHelper.usageToday(app) }
    }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { settingsRepo.set(key, value) }
    }

    fun manualAdjust(source: SourceApp, delta: Int, note: String) {
        viewModelScope.launch {
            val ok = repo.addManualAdjustment(source, delta, note)
            message.value = if (ok) "Correction saved (kept as a separate manual entry)" else "Nothing to change"
        }
    }

    fun resetToday() {
        viewModelScope.launch {
            val s = state.value
            val zone = s?.zone ?: ZoneId.systemDefault()
            repo.resetDay(s?.today?.date ?: java.time.LocalDate.now(zone))
            message.value = "Today was reset. History is preserved as manual adjustments."
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repo.deleteAll()
            DetectionDiagnostics.clear()
            message.value = "All local tracking data deleted"
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = repo.exportCsv()
                    app.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } != null
                }.getOrDefault(false)
            }
            message.value = if (ok) "History exported" else "Export failed"
        }
    }
}
