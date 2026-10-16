package com.example.brainreels

import com.example.brainreels.data.Confidence
import com.example.brainreels.data.ConfidencePolicy
import com.example.brainreels.data.SourceApp
import com.example.brainreels.tracking.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelDetectionEngineTest {

    private fun obs(
        t: Long, score: Int = 3, scrolled: Boolean = false, idx: Int? = null, fp: String? = null,
        neg: NegativeContext? = null, src: SourceApp = SourceApp.INSTAGRAM
    ) = Observation(
        source = src, timeMs = t,
        signals = ViewerSignals(score >= 1, score >= 2, score >= 3, score >= 4),
        negative = neg, scrolled = scrolled, pagerIndex = idx, fingerprint = fp
    )

    private fun List<DetectionResult>.of(o: Outcome) = filter { it.outcome == o }

    /** Feeds an observation and any deadline-driven results into [sink]. */
    private fun ReelDetectionEngine.feed(sink: MutableList<DetectionResult>, o: Observation) {
        sink += onObservation(o)
    }

    private fun ReelDetectionEngine.tick(sink: MutableList<DetectionResult>, t: Long) {
        sink += advance(t)
    }

    @Test fun doesNotCountBeforeMinimumViewingDuration() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0)); e.tick(r, 1999)
        assertTrue(r.of(Outcome.COUNTED).isEmpty())
        e.tick(r, 2000)
        assertEquals(1, r.of(Outcome.COUNTED).size)
        assertEquals(Confidence.HIGH, r.of(Outcome.COUNTED).single().confidence)
    }

    @Test fun minimumDurationIsConfigurable() {
        val e = ReelDetectionEngine(EngineConfig(minViewMs = 500)); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0)); e.tick(r, 500)
        assertEquals(1, r.of(Outcome.COUNTED).size)
    }

    @Test fun repeatedEventsAndRedrawsCountOnce() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, fp = "a", idx = 0))
        for (t in listOf(100L, 200L, 300L, 900L)) e.feed(r, obs(t, fp = "a", idx = 0))
        e.tick(r, 2500)
        for (t in listOf(3000L, 3100L)) e.feed(r, obs(t, fp = "a", idx = 0))
        e.tick(r, 9000)
        assertEquals(1, r.of(Outcome.COUNTED).size)
    }

    @Test fun eachDistinctTransitionCountsOnceWithUniqueKeys() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, idx = 0, fp = "a")); e.tick(r, 2000)
        e.feed(r, obs(2500, scrolled = true, idx = 1, fp = "a")); e.tick(r, 4500)
        val counted = r.of(Outcome.COUNTED)
        assertEquals(2, counted.size)
        assertEquals(2, counted.map { it.sessionKey }.toSet().size) // identical labels, distinct sessions
    }

    @Test fun quickSwipingCountsOnlyTheReelThatStayed() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, idx = 0)); e.feed(r, obs(500, scrolled = true, idx = 1))
        e.feed(r, obs(1000, scrolled = true, idx = 2)); e.tick(r, 5000)
        assertEquals(1, r.of(Outcome.COUNTED).size)
        assertEquals(2, r.of(Outcome.REJECTED).size)
    }

    @Test fun settlingContentAfterSwipeIsNotANewReel() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, idx = 0, fp = "a")); e.tick(r, 2000)
        e.feed(r, obs(2500, scrolled = true, idx = 1, fp = "a"))
        e.feed(r, obs(2700, fp = "b")) // late text update for the same swipe
        e.tick(r, 4700)
        assertEquals(2, r.of(Outcome.COUNTED).size)
    }

    @Test fun leavingToStoryBeforeThresholdIsRejected() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0)); e.feed(r, obs(1000, neg = NegativeContext.STORY)); e.tick(r, 9000)
        assertTrue(r.of(Outcome.COUNTED).isEmpty())
        assertEquals(1, r.of(Outcome.REJECTED).size)
        assertTrue(!e.isInViewer)
    }

    @Test fun leavingTheAppRejectsPendingCandidate() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0)); r += e.onLeftApp(1000); e.tick(r, 9000)
        assertTrue(r.of(Outcome.COUNTED).isEmpty())
        assertNull(e.nextDeadline())
    }

    @Test fun weakSignalsAreNeverCounted() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, score = 1)); e.tick(r, 9000)
        assertTrue(r.filter { it.outcome != Outcome.INFO }.isEmpty())
    }

    @Test fun mediumConfidenceIsUncertainUnderHighOnlyPolicy() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, score = 2)); e.tick(r, 2000)
        assertTrue(r.of(Outcome.COUNTED).isEmpty())
        val u = r.of(Outcome.UNCERTAIN).single()
        assertEquals(Confidence.MEDIUM, u.confidence)
    }

    @Test fun mediumConfidenceCountsWhenPolicyAllows() {
        val e = ReelDetectionEngine(EngineConfig(policy = ConfidencePolicy.MEDIUM_AND_ABOVE))
        val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, score = 2)); e.tick(r, 2000)
        assertEquals(Confidence.MEDIUM, r.of(Outcome.COUNTED).single().confidence)
    }

    @Test fun contentChangeWithoutScrollIsOnlyMediumEvidence() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, fp = "a")); e.tick(r, 2000)
        e.feed(r, obs(3000, fp = "b")); e.tick(r, 5000)
        assertEquals(1, r.of(Outcome.COUNTED).size)
        assertEquals(1, r.of(Outcome.UNCERTAIN).size)
    }

    @Test fun scrollPlusContentChangeIsHighConfidence() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, fp = "a")); e.tick(r, 2000)
        e.feed(r, obs(3000, scrolled = true, fp = "a")); e.feed(r, obs(3200, fp = "b")); e.tick(r, 5200)
        assertEquals(2, r.of(Outcome.COUNTED).size)
    }

    @Test fun scrollWithIdenticalContentIsRecordedAsUncertainNotInvented() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, fp = "a")); e.tick(r, 2000)
        e.feed(r, obs(3000, scrolled = true, fp = "a"))
        assertEquals(4500L, e.nextDeadline())
        e.tick(r, 4500); e.tick(r, 6500)
        assertEquals(1, r.of(Outcome.COUNTED).size)
        assertEquals(1, r.of(Outcome.UNCERTAIN).size)
    }

    @Test fun briefLoadingStateDoesNotEndSessionOrDoubleCount() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0)); e.feed(r, obs(500, score = 0)); e.feed(r, obs(800)); e.tick(r, 2000)
        assertEquals(1, r.of(Outcome.COUNTED).size)
    }

    @Test fun prolongedSignalLossClosesViewer() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0)); e.feed(r, obs(500, score = 0)); e.tick(r, 1200); e.tick(r, 9000)
        assertTrue(r.of(Outcome.COUNTED).isEmpty())
        assertTrue(!e.isInViewer)
    }

    @Test fun returningToAlreadyCountedPositionIsNotCounted() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, idx = 0)); e.tick(r, 2000)
        e.feed(r, obs(2500, scrolled = true, idx = 1)); e.tick(r, 4500)
        e.feed(r, obs(5000, scrolled = true, idx = 0)); e.tick(r, 9000)
        assertEquals(2, r.of(Outcome.COUNTED).size)
    }

    @Test fun restartRecoveryDoesNotRecountSameReel() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.recoverFrom("fp1", 1000)
        e.feed(r, obs(5000, fp = "fp1")); e.tick(r, 9000)
        assertTrue(r.of(Outcome.COUNTED).isEmpty())
        val e2 = ReelDetectionEngine(); val r2 = mutableListOf<DetectionResult>()
        e2.recoverFrom("fp1", 1000)
        e2.feed(r2, obs(5000, fp = "other")); e2.tick(r2, 9000)
        assertEquals(1, r2.of(Outcome.COUNTED).size)
    }

    @Test fun restartRecoveryExpiresAfterGraceWindow() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.recoverFrom("fp1", 0)
        e.feed(r, obs(60_000, fp = "fp1")); e.tick(r, 62_000)
        assertEquals(1, r.of(Outcome.COUNTED).size)
    }

    @Test fun switchingAppsClosesTheOtherViewer() {
        val e = ReelDetectionEngine(); val r = mutableListOf<DetectionResult>()
        e.feed(r, obs(0, src = SourceApp.INSTAGRAM)); e.feed(r, obs(1000, src = SourceApp.FACEBOOK)); e.tick(r, 3000)
        val c = r.of(Outcome.COUNTED).single()
        assertEquals(SourceApp.FACEBOOK, c.source)
        assertTrue(c.sessionKey!!.startsWith("facebook-"))
    }
}
