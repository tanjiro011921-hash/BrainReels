package com.example.brainreels.tracking

import com.example.brainreels.data.Confidence
import com.example.brainreels.data.ConfidencePolicy
import com.example.brainreels.data.SourceApp

/**
 * Pure-Kotlin state machine (no Android dependencies, time is injected) that turns noisy
 * [Observation]s into at most one [DetectionResult] per distinct Reel session.
 *
 * It is a HEURISTIC. Instagram and Facebook expose no public Reel counter, so every decision
 * carries a confidence level and a human-readable reason.
 *
 * Event-driven: callers feed observations and ask [nextDeadline] when to call [advance].
 * There is no polling inside the engine.
 *
 * Not thread-safe: call from one thread/dispatcher.
 */
class ReelDetectionEngine(var config: EngineConfig = EngineConfig()) {

    private enum class Evidence(val text: String) {
        ENTER("entered Reel viewer"),
        INDEX("pager position changed"),
        SCROLL_AND_CONTENT("scroll plus changed content"),
        CONTENT_ONLY("content changed without a scroll"),
        SCROLL_ONLY("scroll without distinguishable content change")
    }

    private class Candidate(
        val key: String,
        val source: SourceApp,
        val startMs: Long,
        val confidence: Confidence,
        val evidence: Evidence,
        val signals: String,
        val pagerIndex: Int?
    )

    private var inViewer = false
    private var viewerSource: SourceApp? = null
    private var epochStart = 0L
    private var transitionCount = 0
    private var lastTransitionStart = Long.MIN_VALUE / 2
    private var nonViewerSince: Long? = null
    private var lastFingerprint: String? = null
    private var lastPagerIndex: Int? = null
    private var lastScrollAt: Long? = null
    private var scrollPending = false
    private var lastScore = 0
    private var candidate: Candidate? = null
    private val countedIndices = mutableSetOf<Int>()

    private var lastCountedFingerprint: String? = null
    private var lastCountedAtMs: Long? = null

    val isInViewer: Boolean get() = inViewer

    /** Call after a service restart with the most recent stored counted event (if any). */
    fun recoverFrom(fingerprint: String?, countedAtMs: Long) {
        lastCountedFingerprint = fingerprint
        lastCountedAtMs = countedAtMs
    }

    /** Next time [advance] must be called, or null if nothing is pending. */
    fun nextDeadline(): Long? {
        if (!inViewer) return null
        val nvs = nonViewerSince
        return listOfNotNull(
            nvs?.let { it + config.exitGraceMs },
            if (scrollPending) lastScrollAt?.let { it + config.transitionWindowMs } else null,
            if (nvs == null) candidate?.let { it.startMs + config.minViewMs } else null
        ).minOrNull()
    }

    fun onObservation(o: Observation): List<DetectionResult> {
        val out = mutableListOf<DetectionResult>()
        runDeadlines(o.timeMs, out)
        if (inViewer && viewerSource != o.source) closeViewer(o.timeMs, "switched to another app", out)

        val isViewer = o.negative == null && o.signals.score >= config.minViewerScore
        if (!isViewer) {
            handleNonViewer(o, out)
            return out
        }
        nonViewerSince = null
        lastScore = o.signals.score
        if (!inViewer) enterViewer(o, out) else withinViewer(o, out)
        return out
    }

    /** Process any deadlines that are due at [nowMs] (confirm candidate, scroll window, exit grace). */
    fun advance(nowMs: Long): List<DetectionResult> {
        val out = mutableListOf<DetectionResult>()
        runDeadlines(nowMs, out)
        return out
    }

    /** The user left the supported app (or the screen turned off). */
    fun onLeftApp(nowMs: Long): List<DetectionResult> {
        val out = mutableListOf<DetectionResult>()
        runDeadlines(nowMs, out)
        if (inViewer) closeViewer(nowMs, "left the supported app", out)
        return out
    }

    // --- internals -------------------------------------------------------------------------

    private fun handleNonViewer(o: Observation, out: MutableList<DetectionResult>) {
        if (!inViewer) return
        if (o.negative != null) {
            closeViewer(o.timeMs, "${o.negative.name.lowercase()} screen detected", out)
            return
        }
        val since = nonViewerSince ?: o.timeMs.also { nonViewerSince = it }
        if (o.timeMs - since >= config.exitGraceMs) {
            closeViewer(o.timeMs, "viewer signals lost", out)
        } else {
            out += info(o, "Viewer signals dropped briefly; tolerated as a possible loading state")
        }
    }

    private fun enterViewer(o: Observation, out: MutableList<DetectionResult>) {
        inViewer = true
        viewerSource = o.source
        epochStart = o.timeMs
        transitionCount = 0
        lastFingerprint = o.fingerprint
        lastPagerIndex = o.pagerIndex
        lastScrollAt = null
        scrollPending = false
        countedIndices.clear()
        lastTransitionStart = o.timeMs

        val fp = o.fingerprint
        val recent = lastCountedAtMs?.let { o.timeMs - it < config.restartGraceMs } ?: false
        if (fp != null && fp == lastCountedFingerprint && recent) {
            out += DetectionResult(
                Outcome.REJECTED,
                "Same content as the Reel counted moments ago; treated as the same session",
                o.timeMs, o.source, fingerprint = fp
            )
            return
        }
        startCandidate(o.timeMs, o.source, Evidence.ENTER, o.signals.score, o.pagerIndex, o.signals.describe(), out)
    }

    private fun withinViewer(o: Observation, out: MutableList<DetectionResult>) {
        if (o.scrolled) {
            lastScrollAt = o.timeMs
            scrollPending = true
        }
        val indexChanged = o.pagerIndex != null && lastPagerIndex != null && o.pagerIndex != lastPagerIndex
        val fpChanged = o.fingerprint != null && lastFingerprint != null && o.fingerprint != lastFingerprint
        val scrollRecent = lastScrollAt?.let { o.timeMs - it <= config.transitionWindowMs } ?: false
        val settling = !indexChanged && o.timeMs - lastTransitionStart <= config.transitionWindowMs

        o.pagerIndex?.let { lastPagerIndex = it }
        o.fingerprint?.let { lastFingerprint = it }

        val evidence = when {
            indexChanged -> Evidence.INDEX
            fpChanged && settling -> {
                out += info(o, "Content settled after a transition; not a new Reel")
                return
            }
            fpChanged && scrollRecent -> Evidence.SCROLL_AND_CONTENT
            fpChanged -> Evidence.CONTENT_ONLY
            else -> return // plain redraw / repeated event: nothing to do
        }
        scrollPending = false
        startCandidate(o.timeMs, o.source, evidence, o.signals.score, o.pagerIndex, o.signals.describe(), out)
    }

    private fun startCandidate(
        now: Long, source: SourceApp, evidence: Evidence, score: Int,
        pagerIndex: Int?, signals: String, out: MutableList<DetectionResult>
    ) {
        candidate?.let {
            out += DetectionResult(
                Outcome.REJECTED, "Reel left view before ${config.minViewMs} ms; not counted",
                now, it.source, it.confidence, it.key, metadata = it.signals
            )
        }
        candidate = null
        lastTransitionStart = now

        if (pagerIndex != null && evidence != Evidence.ENTER && pagerIndex in countedIndices) {
            out += DetectionResult(
                Outcome.REJECTED, "Returned to an already counted Reel position; not a new session",
                now, source, metadata = signals
            )
            return
        }
        val high = score >= config.highViewerScore
        val confidence = when (evidence) {
            Evidence.ENTER, Evidence.INDEX, Evidence.SCROLL_AND_CONTENT ->
                if (high) Confidence.HIGH else Confidence.MEDIUM
            Evidence.CONTENT_ONLY, Evidence.SCROLL_ONLY ->
                if (high) Confidence.MEDIUM else Confidence.LOW
        }
        if (confidence == Confidence.LOW) {
            out += DetectionResult(Outcome.REJECTED, "Insufficient evidence (${evidence.text}, signals $signals)", now, source, Confidence.LOW, metadata = signals)
            return
        }
        transitionCount++
        candidate = Candidate(
            key = "${source.name.lowercase()}-$epochStart-$transitionCount",
            source = source, startMs = now, confidence = confidence,
            evidence = evidence, signals = signals, pagerIndex = pagerIndex
        )
    }

    private fun runDeadlines(now: Long, out: MutableList<DetectionResult>) {
        if (!inViewer) return
        val nvs = nonViewerSince
        if (nvs != null && now >= nvs + config.exitGraceMs) {
            closeViewer(now, "viewer signals lost", out)
            return
        }
        val sc = lastScrollAt
        if (scrollPending && sc != null && now >= sc + config.transitionWindowMs) {
            scrollPending = false
            val src = viewerSource
            if (src != null) startCandidate(now, src, Evidence.SCROLL_ONLY, lastScore, lastPagerIndex, "scroll", out)
        }
        val c = candidate
        if (c != null && nonViewerSince == null && now >= c.startMs + config.minViewMs) confirm(c, now, out)
    }

    private fun confirm(c: Candidate, now: Long, out: MutableList<DetectionResult>) {
        candidate = null
        val fp = lastFingerprint
        val counts = when (config.policy) {
            ConfidencePolicy.HIGH_ONLY -> c.confidence == Confidence.HIGH
            ConfidencePolicy.MEDIUM_AND_ABOVE -> c.confidence >= Confidence.MEDIUM
        }
        val meta = "evidence=${c.evidence.name}; signals=${c.signals}; viewedMs>=${config.minViewMs}"
        if (counts) {
            lastCountedFingerprint = fp
            lastCountedAtMs = now
            c.pagerIndex?.let { countedIndices += it }
            out += DetectionResult(
                Outcome.COUNTED, "Counted: ${c.evidence.text}", now, c.source, c.confidence, c.key, fp, meta
            )
        } else {
            out += DetectionResult(
                Outcome.UNCERTAIN,
                "Possible Reel (${c.evidence.text}) but ${c.confidence.name.lowercase()} confidence does not meet the selected policy",
                now, c.source, c.confidence, c.key, fp, meta
            )
        }
    }

    private fun closeViewer(now: Long, reason: String, out: MutableList<DetectionResult>) {
        candidate?.let {
            out += DetectionResult(
                Outcome.REJECTED, "Left viewer before ${config.minViewMs} ms ($reason); not counted",
                now, it.source, it.confidence, it.key, metadata = it.signals
            )
        }
        candidate = null
        if (inViewer) out += DetectionResult(Outcome.INFO, "Reel viewer closed: $reason", now, viewerSource)
        inViewer = false
        viewerSource = null
        nonViewerSince = null
        scrollPending = false
        lastScrollAt = null
        lastPagerIndex = null
        lastFingerprint = null
        countedIndices.clear()
    }

    private fun info(o: Observation, msg: String) = DetectionResult(Outcome.INFO, msg, o.timeMs, o.source)
}
