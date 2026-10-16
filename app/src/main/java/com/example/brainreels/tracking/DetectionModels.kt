package com.example.brainreels.tracking

import com.example.brainreels.data.Confidence
import com.example.brainreels.data.ConfidencePolicy
import com.example.brainreels.data.SourceApp

/** Independent yes/no signals that a screen is a Reel viewer. See SignalExtractor for how each is derived. */
data class ViewerSignals(
    val idHint: Boolean = false,
    val labelHint: Boolean = false,
    val fullScreenPager: Boolean = false,
    val actionRail: Boolean = false
) {
    val score: Int get() = listOf(idHint, labelHint, fullScreenPager, actionRail).count { it }

    fun describe(): String = buildList {
        if (idHint) add("id")
        if (labelHint) add("label")
        if (fullScreenPager) add("pager")
        if (actionRail) add("rail")
    }.joinToString("+").ifEmpty { "none" }
}

/** Screens that look similar to Reels but must not be counted. */
enum class NegativeContext { STORY, AD, COMMENTS }

/** A privacy-minimal snapshot of what the extractor saw. No raw text is included. */
data class Observation(
    val source: SourceApp,
    val timeMs: Long,
    val signals: ViewerSignals,
    val negative: NegativeContext? = null,
    /** A scroll event occurred since the previous observation. */
    val scrolled: Boolean = false,
    /** Index of the first visible item in the scrolling pager, if the app reports one. */
    val pagerIndex: Int? = null,
    /** Hash of limited identity text (author/caption nodes), or null if none was exposed. */
    val fingerprint: String? = null
)

enum class Outcome { COUNTED, UNCERTAIN, REJECTED, INFO }

data class DetectionResult(
    val outcome: Outcome,
    val reason: String,
    val timeMs: Long,
    val source: SourceApp? = null,
    val confidence: Confidence? = null,
    val sessionKey: String? = null,
    val fingerprint: String? = null,
    val metadata: String? = null
)

data class EngineConfig(
    /** Minimum time a candidate Reel must stay in view before it can be counted. */
    val minViewMs: Long = 2000,
    val policy: ConfidencePolicy = ConfidencePolicy.HIGH_ONLY,
    /** How long viewer signals may disappear (loading states) before the viewer is considered closed. */
    val exitGraceMs: Long = 700,
    /** Window in which a scroll and a content change are treated as one swipe. */
    val transitionWindowMs: Long = 1500,
    /** After a count, an identical fingerprint within this window is treated as the same session. */
    val restartGraceMs: Long = 30_000,
    val minViewerScore: Int = 2,
    val highViewerScore: Int = 3
)
