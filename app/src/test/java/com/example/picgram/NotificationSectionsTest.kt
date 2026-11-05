package com.example.picgram

import com.example.picgram.data.model.AppNotification
import com.example.picgram.ui.NotificationPeriod
import com.example.picgram.ui.notificationSections
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class NotificationSectionsTest {
    private val zone = ZoneId.of("America/Lima")
    private val now = Instant.parse("2026-10-29T17:00:00Z").toEpochMilli()
    private fun notice(id: String, date: String, actor: String = "ana") =
        AppNotification(id, actor, "like", date, false, "post", actor)

    @Test fun sectionsUseCalendarDaysAndStayInChronologicalOrder() {
        val notices = listOf(
            notice("old", "2026-09-01T17:00:00Z"), notice("week", "2026-10-22T17:00:00Z"),
            notice("today", "2026-10-29T16:00:00Z"), notice("month", "2026-09-29T17:00:00Z"),
            notice("yesterday", "2026-10-28T17:00:00Z"))
        val sections = notificationSections(notices, now, zone)
        assertEquals(listOf(NotificationPeriod.TODAY, NotificationPeriod.YESTERDAY, NotificationPeriod.WEEK,
            NotificationPeriod.MONTH, NotificationPeriod.OLDER), sections.map { it.period })
        assertEquals(listOf("today", "yesterday", "week", "month", "old"), sections.flatMap { it.notifications }.map { it.id })
    }

    @Test fun accountsAndRepeatedEventsAreNeverMergedIntoOneNotification() {
        val notices = listOf(notice("1", "2026-10-29T16:00:00Z"), notice("2", "2026-10-29T16:01:00Z"),
            notice("3", "2026-10-29T16:02:00Z", "luis"))
        val displayed = notificationSections(notices, now, zone).flatMap { it.notifications }
        assertEquals(3, displayed.size)
        assertEquals(notices.toSet(), displayed.toSet())
        assertEquals(listOf("luis", "ana", "ana"), displayed.map { it.actorId })
    }

    @Test fun localMidnightDeterminesYesterdayEvenWhenTheUtcDateIsTheSame() {
        val nearMidnight = Instant.parse("2026-10-29T05:05:00Z").toEpochMilli()
        val sections = notificationSections(listOf(notice("1", "2026-10-29T04:55:00Z")), nearMidnight, zone)
        assertEquals(NotificationPeriod.YESTERDAY, sections.single().period)
    }

    @Test fun showMoreExpandsTheListWithoutDroppingOrCombiningEvents() {
        val notices = (1..35).map { notice(it.toString(), Instant.ofEpochMilli(now - it * 60_000).toString()) }
        val first = notificationSections(notices, now, zone, maxItems = 30).flatMap { it.notifications }
        val all = notificationSections(notices, now, zone, maxItems = 60).flatMap { it.notifications }
        assertEquals(30, first.size)
        assertEquals(35, all.size)
        assertEquals(first, all.take(30))
        assertEquals(35, all.map { it.id }.distinct().size)
    }

    @Test fun malformedDatesDoNotCrashOrHideEventsAndFutureClockSkewStaysInToday() {
        val sections = notificationSections(listOf(notice("broken", "invalid"), notice("future", "2026-10-30T17:00:00Z")), now, zone)
        assertEquals(listOf(NotificationPeriod.TODAY, NotificationPeriod.UNKNOWN), sections.map { it.period })
        assertEquals(listOf("future", "broken"), sections.flatMap { it.notifications }.map { it.id })
        assertTrue(notificationSections(emptyList(), now, zone).isEmpty())
    }
}
