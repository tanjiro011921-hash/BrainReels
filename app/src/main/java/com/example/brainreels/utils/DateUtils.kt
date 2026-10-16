package com.example.brainreels.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** All day boundaries use the supplied (device-local) zone, so DST days are 23h/25h long. */
object DateUtils {
    fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun dayStartMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive end of the day = start of the next local day. */
    fun dayEndMillis(date: LocalDate, zone: ZoneId): Long =
        dayStartMillis(date.plusDays(1), zone)

    /** Milliseconds until the next local midnight (always >= 1). */
    fun millisUntilNextDay(nowMs: Long, zone: ZoneId): Long =
        (dayEndMillis(localDate(nowMs, zone), zone) - nowMs).coerceAtLeast(1)
}
