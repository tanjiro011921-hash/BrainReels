package com.example.brainreels

import com.example.brainreels.data.*
import com.example.brainreels.utils.CsvExporter
import com.example.brainreels.utils.DateUtils
import com.example.brainreels.utils.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsAndCsvTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2025, 6, 15)
    private fun at(d: LocalDate, hour: Int = 12) = DateUtils.dayStartMillis(d, zone) + hour * 3_600_000L
    private var n = 0
    private fun ev(
        app: SourceApp, d: LocalDate, delta: Int = 1, manual: Boolean = false,
        status: EventStatus = if (manual) EventStatus.MANUAL_ADJUSTMENT else EventStatus.COUNTED
    ) = ReelEvent(
        id = (++n).toLong(), sourceApp = app.name, timestamp = at(d),
        detectionMethod = if (manual) DetectionMethod.MANUAL else DetectionMethod.ACCESSIBILITY_HEURISTIC,
        confidence = Confidence.HIGH.name, sessionKey = "k$n", eventStatus = status.name,
        manuallyAdjusted = manual, delta = delta
    )

    @Test fun sourceAppClassification() {
        assertEquals(SourceApp.INSTAGRAM, SourceApp.fromPackage("com.instagram.android"))
        assertEquals(SourceApp.FACEBOOK, SourceApp.fromPackage("com.facebook.katana"))
        assertEquals(SourceApp.FACEBOOK_LITE, SourceApp.fromPackage("com.facebook.lite"))
        assertEquals(null, SourceApp.fromPackage("com.whatsapp"))
        assertEquals(AppGroup.FACEBOOK, SourceApp.FACEBOOK_LITE.group)
    }

    @Test fun dailyTotalsSplitBySourceAndKind() {
        val events = listOf(
            ev(SourceApp.INSTAGRAM, today), ev(SourceApp.INSTAGRAM, today), ev(SourceApp.FACEBOOK, today),
            ev(SourceApp.FACEBOOK_LITE, today), ev(SourceApp.INSTAGRAM, today, delta = 2, manual = true),
            ev(SourceApp.INSTAGRAM, today, delta = 0, status = EventStatus.UNCERTAIN)
        )
        val t = Stats.forDay(events, today, zone)
        assertEquals(6, t.total); assertEquals(4, t.instagram); assertEquals(2, t.facebook)
        assertEquals(4, t.automatic); assertEquals(2, t.manual); assertEquals(1, t.uncertain)
    }

    @Test fun manualCorrectionIsAuditableAndCannotGoNegative() {
        val events = listOf(ev(SourceApp.INSTAGRAM, today), ev(SourceApp.INSTAGRAM, today, delta = -5, manual = true))
        val t = Stats.forDay(events, today, zone)
        assertEquals(0, t.total); assertEquals(1, t.automatic); assertEquals(-5, t.manual)
    }

    @Test fun oldDaysArePreservedAndMidnightStartsAtZero() {
        val yesterday = today.minusDays(1)
        val events = listOf(ev(SourceApp.INSTAGRAM, yesterday), ev(SourceApp.INSTAGRAM, yesterday))
        assertEquals(0, Stats.forDay(events, today, zone).total)
        assertEquals(2, Stats.forDay(events, yesterday, zone).total)
        assertEquals(listOf(yesterday), Stats.daily(events, zone).map { it.date })
    }

    @Test fun lastDaysIsZeroFilledAndMonthToDateIgnoresPreviousMonth() {
        val events = listOf(
            ev(SourceApp.FACEBOOK, today), ev(SourceApp.FACEBOOK, today.minusDays(2)),
            ev(SourceApp.FACEBOOK, today.minusMonths(1))
        )
        val week = Stats.lastDays(events, today, 7, zone)
        assertEquals(7, week.size); assertEquals(today, week.last().date)
        assertEquals(2, week.sumOf { it.total })
        assertEquals(2, Stats.monthToDate(events, today, zone))
    }

    @Test fun csvEscapesAndOrders() {
        val e = ev(SourceApp.INSTAGRAM, today).copy(note = "a,\"b\"\nc")
        val csv = CsvExporter.toCsv(listOf(e), zone)
        val lines = csv.split("\n")
        assertEquals(CsvExporter.HEADER, lines[0])
        assertTrue(csv.contains("\"a,\"\"b\"\"\nc\""))
        assertTrue(csv.contains("INSTAGRAM"))
    }
}
