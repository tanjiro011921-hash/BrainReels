package com.example.brainreels

import com.example.brainreels.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DateUtilsTest {
    private val ny = ZoneId.of("America/New_York")
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val utc = ZoneId.of("UTC")
    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli()
    private val HOUR = 3_600_000L

    @Test fun springForwardDayIs23Hours() {
        val d = LocalDate.of(2024, 3, 10)
        assertEquals(23 * HOUR, DateUtils.dayEndMillis(d, ny) - DateUtils.dayStartMillis(d, ny))
    }

    @Test fun fallBackDayIs25Hours() {
        val d = LocalDate.of(2024, 11, 3)
        assertEquals(25 * HOUR, DateUtils.dayEndMillis(d, ny) - DateUtils.dayStartMillis(d, ny))
    }

    @Test fun midnightRolloverTiming() {
        val almost = ms(ZonedDateTime.of(2025, 5, 1, 23, 59, 59, 0, dhaka))
        assertEquals(1000L, DateUtils.millisUntilNextDay(almost, dhaka))
        val midnight = ms(ZonedDateTime.of(2025, 5, 2, 0, 0, 0, 0, dhaka))
        assertEquals(24 * HOUR, DateUtils.millisUntilNextDay(midnight, dhaka))
        assertEquals(LocalDate.of(2025, 5, 2), DateUtils.localDate(midnight, dhaka))
        assertEquals(LocalDate.of(2025, 5, 1), DateUtils.localDate(midnight - 1, dhaka))
    }

    @Test fun sameInstantFallsOnDifferentLocalDaysAcrossTimezones() {
        val t = ms(ZonedDateTime.of(2025, 5, 1, 20, 0, 0, 0, utc)) // 02:00 next day in Dhaka (UTC+6)
        assertEquals(LocalDate.of(2025, 5, 1), DateUtils.localDate(t, utc))
        assertEquals(LocalDate.of(2025, 5, 2), DateUtils.localDate(t, dhaka))
    }
}
