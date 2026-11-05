package com.example.picgram

import com.example.picgram.ui.notificationRelativeTime
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRelativeTimeTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z").toEpochMilli()

    private fun age(seconds: Long): String = notificationRelativeTime(
        Instant.ofEpochMilli(now - seconds * 1_000).toString(), now
    )

    @Test fun recentNotificationsUseSeconds() {
        assertEquals("Hace unos segundos", age(0))
        assertEquals("Hace unos segundos", age(59))
    }

    @Test fun minutesHaveSingularAndPlural() {
        assertEquals("Hace 1 minuto", age(60))
        assertEquals("Hace 5 minutos", age(300))
        assertEquals("Hace 59 minutos", age(3_599))
    }

    @Test fun hoursHaveSingularAndPlural() {
        assertEquals("Hace 1 hora", age(3_600))
        assertEquals("Hace 23 horas", age(86_399))
    }

    @Test fun daysHaveSingularAndPlural() {
        assertEquals("Hace 1 día", age(86_400))
        assertEquals("Hace 30 días", age(30 * 86_400L))
    }

    @Test fun futureTimestampHandlesClockSkew() {
        assertEquals("Ahora", age(-60))
    }

    @Test fun serverOffsetsAndFractionsAreSupported() {
        assertEquals("Hace 5 minutos", notificationRelativeTime("2026-10-27T06:55:00.000000-05:00", now))
        assertEquals("Hace 5 minutos", notificationRelativeTime("2026-10-27T11:55:00+00:00", now))
    }

    @Test fun invalidTimestampDoesNotExposeRawDate() {
        assertEquals("Fecha no disponible", notificationRelativeTime("invalid", now))
        assertEquals("Fecha no disponible", notificationRelativeTime("", now))
    }

    @Test fun timeAdvancesWithoutChangingTheNotification() {
        val created = "2026-10-27T11:59:30Z"
        assertEquals("Hace unos segundos", notificationRelativeTime(created, now))
        assertEquals("Hace 1 minuto", notificationRelativeTime(created, now + 30_000))
    }
}
