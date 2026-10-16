package com.example.brainreels

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.brainreels.data.*
import com.example.brainreels.tracking.DetectionResult
import com.example.brainreels.tracking.Outcome
import com.example.brainreels.utils.DateUtils
import com.example.brainreels.utils.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReelRepositoryIntegrationTest {
    private val zone = ZoneId.of("UTC")
    private lateinit var db: AppDatabase
    private lateinit var repo: ReelRepository
    private val day = LocalDate.of(2025, 6, 15)
    private fun at(d: LocalDate, hour: Int) = DateUtils.dayStartMillis(d, zone) + hour * 3_600_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ReelRepository(db.reelDao()) { zone }
    }

    @After fun tearDown() { db.close() }

    private fun counted(key: String, t: Long, app: SourceApp = SourceApp.INSTAGRAM, fp: String? = "fp") = DetectionResult(
        Outcome.COUNTED, "Counted", t, app, Confidence.HIGH, key, fp, "signals=id+label+pager"
    )

    @Test fun insertsDetectionWithMetadata() = runBlocking {
        assertTrue(repo.recordDetection(counted("k1", at(day, 10))))
        val row = db.reelDao().getAll().single()
        assertEquals("INSTAGRAM", row.sourceApp); assertEquals("COUNTED", row.eventStatus)
        assertEquals(DetectionMethod.ACCESSIBILITY_HEURISTIC, row.detectionMethod)
        assertEquals("k1", row.sessionKey); assertFalse(row.manuallyAdjusted); assertEquals(1, row.delta)
        assertNotNull(row.note)
    }

    @Test fun sessionKeyIsUnique_duplicateInsertIsIgnored() = runBlocking {
        assertTrue(repo.recordDetection(counted("same", at(day, 10))))
        assertFalse(repo.recordDetection(counted("same", at(day, 11))))
        assertEquals(1, db.reelDao().countSessionKey("same"))
        assertEquals(1, db.reelDao().getAll().size)
    }

    @Test fun uncertainObservationsAreStoredButNotCounted() = runBlocking {
        repo.recordDetection(counted("k1", at(day, 9)))
        repo.recordDetection(DetectionResult(Outcome.UNCERTAIN, "weak", at(day, 10), SourceApp.INSTAGRAM, Confidence.MEDIUM, "k2"))
        val t = repo.totalsForDay(day)
        assertEquals(1, t.total); assertEquals(1, t.uncertain)
    }

    @Test fun rejectedAndInfoResultsAreNeverStored() = runBlocking {
        assertFalse(repo.recordDetection(DetectionResult(Outcome.REJECTED, "x", 1, SourceApp.INSTAGRAM, sessionKey = "k")))
        assertFalse(repo.recordDetection(DetectionResult(Outcome.INFO, "x", 1)))
        assertTrue(db.reelDao().getAll().isEmpty())
    }

    @Test fun historicalStatisticsAcrossDays() = runBlocking {
        repo.recordDetection(counted("a", at(day.minusDays(1), 10)))
        repo.recordDetection(counted("b", at(day.minusDays(1), 11), SourceApp.FACEBOOK))
        repo.recordDetection(counted("c", at(day, 10)))
        val daily = Stats.daily(repo.observeAll().first(), zone)
        assertEquals(listOf(day, day.minusDays(1)), daily.map { it.date })
        assertEquals(1, daily[0].total); assertEquals(2, daily[1].total)
    }

    @Test fun dailyResetPreservesHistoryAndAuditTrail() = runBlocking {
        repo.recordDetection(counted("y1", at(day.minusDays(1), 10)))
        repo.recordDetection(counted("t1", at(day, 8)))
        repo.recordDetection(counted("t2", at(day, 9), SourceApp.FACEBOOK))
        repo.resetDay(day, nowMs = at(day, 12))
        assertEquals(0, repo.totalsForDay(day).total)
        assertEquals(1, repo.totalsForDay(day.minusDays(1)).total)
        val all = db.reelDao().getAll()
        assertEquals(5, all.size) // 3 detections + 2 manual reset rows, nothing deleted
        val manual = all.filter { it.manuallyAdjusted }
        assertEquals(2, manual.size)
        assertTrue(manual.all { it.detectionMethod == DetectionMethod.MANUAL && it.eventStatus == EventStatus.MANUAL_ADJUSTMENT.name })
        assertEquals(2, repo.totalsForDay(day).let { it.automatic })
    }

    @Test fun manualCorrectionIsSeparateFromAutomatic() = runBlocking {
        repo.recordDetection(counted("a", at(day, 10)))
        assertTrue(repo.addManualAdjustment(SourceApp.INSTAGRAM, 3, "missed some", at(day, 11)))
        assertFalse(repo.addManualAdjustment(SourceApp.INSTAGRAM, 0, "no-op", at(day, 11)))
        val t = repo.totalsForDay(day)
        assertEquals(4, t.total); assertEquals(1, t.automatic); assertEquals(3, t.manual)
    }

    @Test fun serviceRestartRecoveryReadsLastCountedEvent() = runBlocking {
        assertNull(repo.latestCounted())
        repo.recordDetection(counted("a", at(day, 10), fp = "old"))
        repo.recordDetection(counted("b", at(day, 11), fp = "newest"))
        repo.recordDetection(DetectionResult(Outcome.UNCERTAIN, "weak", at(day, 12), SourceApp.INSTAGRAM, Confidence.MEDIUM, "c", "ignored"))
        assertEquals("newest", repo.latestCounted()?.fingerprint)
    }

    @Test fun csvExportContainsAllRows() = runBlocking {
        repo.recordDetection(counted("a", at(day, 10)))
        repo.addManualAdjustment(SourceApp.FACEBOOK, -1, "oops, wrong", at(day, 11))
        val lines = repo.exportCsv().trim().split("\n")
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("id,timestamp_iso"))
        assertTrue(lines.any { it.contains("MANUAL_ADJUSTMENT") && it.contains("\"oops, wrong\"") })
    }

    @Test fun deleteAllRemovesEverything() = runBlocking {
        repo.recordDetection(counted("a", at(day, 10)))
        repo.deleteAll()
        assertTrue(db.reelDao().getAll().isEmpty())
    }
}
