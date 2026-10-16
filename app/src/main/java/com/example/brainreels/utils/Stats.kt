package com.example.brainreels.utils

import com.example.brainreels.data.AppGroup
import com.example.brainreels.data.EventStatus
import com.example.brainreels.data.ReelEvent
import com.example.brainreels.data.SourceApp
import java.time.LocalDate
import java.time.ZoneId

data class DayTotals(
    val date: LocalDate,
    val total: Int,
    val instagram: Int,
    val facebook: Int,
    /** Net automatically detected count. */
    val automatic: Int,
    /** Net manual adjustment (may be negative). */
    val manual: Int,
    /** Observations that were not counted because of weak evidence. */
    val uncertain: Int
) {
    companion object {
        fun empty(date: LocalDate) = DayTotals(date, 0, 0, 0, 0, 0, 0)
    }
}

object Stats {
    fun forDay(events: List<ReelEvent>, date: LocalDate, zone: ZoneId): DayTotals {
        val sameDay = events.filter { DateUtils.localDate(it.timestamp, zone) == date }
        return summarize(date, sameDay)
    }

    /** Totals for every local day that has at least one event, newest first. */
    fun daily(events: List<ReelEvent>, zone: ZoneId): List<DayTotals> =
        events.groupBy { DateUtils.localDate(it.timestamp, zone) }
            .map { (d, list) -> summarize(d, list) }
            .sortedByDescending { it.date }

    /** [n] consecutive days ending at [today], oldest first, zero-filled. */
    fun lastDays(events: List<ReelEvent>, today: LocalDate, n: Int, zone: ZoneId): List<DayTotals> {
        val byDay = daily(events, zone).associateBy { it.date }
        return (n - 1 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            byDay[d] ?: DayTotals.empty(d)
        }
    }

    fun monthToDate(events: List<ReelEvent>, today: LocalDate, zone: ZoneId): Int =
        daily(events, zone).filter { it.date.year == today.year && it.date.month == today.month && !it.date.isAfter(today) }
            .sumOf { it.total }

    private fun summarize(date: LocalDate, list: List<ReelEvent>): DayTotals {
        fun net(group: AppGroup) = list.filter { SourceApp.fromName(it.sourceApp)?.group == group }
            .sumOf { it.delta }.coerceAtLeast(0)
        val ig = net(AppGroup.INSTAGRAM)
        val fb = net(AppGroup.FACEBOOK)
        return DayTotals(
            date = date,
            total = ig + fb,
            instagram = ig,
            facebook = fb,
            automatic = list.filter { !it.manuallyAdjusted }.sumOf { it.delta },
            manual = list.filter { it.manuallyAdjusted }.sumOf { it.delta },
            uncertain = list.count { it.eventStatus == EventStatus.UNCERTAIN.name }
        )
    }
}
