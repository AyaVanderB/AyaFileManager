package com.example.ayafilemanager.domain.model

import java.time.Clock
import java.time.LocalDate

enum class RecentTimeRange {
    TODAY, WEEK, MONTH;

    /** Calendar ranges in the device zone, including today. */
    fun startMillis(clock: Clock = Clock.systemDefaultZone()): Long {
        val today = LocalDate.now(clock)
        val start = when (this) {
            TODAY -> today
            WEEK -> today.minusDays(6)
            MONTH -> today.minusMonths(1)
        }
        return start.atStartOfDay(clock.zone).toInstant().toEpochMilli()
    }
}
