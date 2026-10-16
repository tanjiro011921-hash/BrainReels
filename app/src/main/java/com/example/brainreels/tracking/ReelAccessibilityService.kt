package com.example.brainreels.tracking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.brainreels.data.*
import com.example.brainreels.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feeds privacy-minimal observations from Instagram/Facebook into [ReelDetectionEngine].
 * Everything runs on the main thread, so the (non-thread-safe) engine needs no locking.
 * EXPERIMENTAL: heuristic detection, see docs/LIMITATIONS.md.
 */
class ReelAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val engine = ReelDetectionEngine()
    private lateinit var scope: CoroutineScope
    private lateinit var repo: ReelRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var extractor: SignalExtractor

    @Volatile private var settings = AppSettings()
    private var pendingScroll = false
    private var pagerIndex: Int? = null
    private var lastSource: SourceApp? = null
    private var extractScheduled = false
    private var timerJob: Job? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleResults(engine.onLeftApp(System.currentTimeMillis()))
            timerJob?.cancel()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        repo = ReelRepository(AppDatabase.get(this).reelDao())
        settingsRepo = SettingsRepository(this)
        val dm = resources.displayMetrics
        extractor = SignalExtractor(dm.widthPixels, dm.heightPixels)
        DetectionDiagnostics.setConnected(true)

        scope.launch {
            settingsRepo.flow.collect { s ->
                settings = s
                engine.config = EngineConfig(minViewMs = s.minViewMs.toLong(), policy = s.policy)
                if (s.widgetEnabled) OverlayController.start(this@ReelAccessibilityService)
            }
        }
        // Service-restart recovery: remember the last counted Reel so a restart doesn't re-count it.
        scope.launch { repo.latestCounted()?.let { engine.recoverFrom(it.fingerprint, it.timestamp) } }

        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!settings.detectionEnabled) return
        val source = SourceApp.fromPackage(event.packageName?.toString()) ?: return
        if (!settings.isEnabled(source)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                pendingScroll = true
                if (event.itemCount > 0 && event.fromIndex >= 0) pagerIndex = event.fromIndex
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> Unit
            else -> return
        }
        lastSource = source
        // Throttle noisy event bursts: at most one extraction per 200 ms, using the freshest tree.
        if (extractScheduled) return
        extractScheduled = true
        handler.postDelayed({ extractScheduled = false; sampleAndProcess(source) }, 200)
    }

    private fun sampleAndProcess(source: SourceApp) {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != source.packageName) return
            val obs = extractor.extract(root, source, System.currentTimeMillis(), pendingScroll, pagerIndex)
            pendingScroll = false
            handleResults(engine.onObservation(obs))
        } finally {
            @Suppress("DEPRECATION") root.recycle()
        }
        scheduleDeadline()
    }

    /** One timer for the engine's next deadline. No polling: it is re-armed only after state changes. */
    private fun scheduleDeadline() {
        timerJob?.cancel()
        val deadline = engine.nextDeadline() ?: return
        timerJob = scope.launch {
            delay((deadline - System.currentTimeMillis()).coerceAtLeast(50))
            verifyStillInApp()
            handleResults(engine.advance(System.currentTimeMillis()))
            scheduleDeadline()
        }
    }

    /** Before confirming, re-check that a supported app is still the active window. */
    private fun verifyStillInApp() {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        @Suppress("DEPRECATION") root?.recycle()
        if (pkg != null && SourceApp.fromPackage(pkg) == null) {
            handleResults(engine.onLeftApp(System.currentTimeMillis()))
        }
        // If pkg == null the platform gave us no information; we do not treat that as leaving.
    }

    private fun handleResults(results: List<DetectionResult>) {
        for (r in results) {
            DetectionDiagnostics.record(r)
            if (r.outcome == Outcome.COUNTED || r.outcome == Outcome.UNCERTAIN) {
                scope.launch { repo.recordDetection(r) } // duplicate session keys are ignored by the DB
            }
        }
    }

    override fun onInterrupt() {
        handleResults(engine.onLeftApp(System.currentTimeMillis()))
        timerJob?.cancel()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        handler.removeCallbacksAndMessages(null)
        DetectionDiagnostics.setConnected(false)
        if (::scope.isInitialized) scope.cancel()
        super.onDestroy()
    }
}
