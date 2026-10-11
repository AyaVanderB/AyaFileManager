package com.example.ayafilemanager

import com.example.ayafilemanager.domain.model.RecentTimeRange
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTimeRangeTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-31T14:30:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test fun todayStartsAtLocalMidnightRatherThan24HoursAgo() {
        assertEquals(Instant.parse("2026-03-30T16:00:00Z").toEpochMilli(), RecentTimeRange.TODAY.startMillis(clock))
    }

    @Test fun weekIncludesTodayAndPreviousSixCalendarDays() {
        assertEquals(Instant.parse("2026-03-24T16:00:00Z").toEpochMilli(), RecentTimeRange.WEEK.startMillis(clock))
    }

    @Test fun monthHandlesFebruaryAndLocalTimezone() {
        assertEquals(Instant.parse("2026-02-27T16:00:00Z").toEpochMilli(), RecentTimeRange.MONTH.startMillis(clock))
    }
}
