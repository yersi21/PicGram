package com.example.picgram.ui

import com.example.picgram.data.model.AppNotification
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

internal enum class NotificationPeriod(val label: String) {
    TODAY("Hoy"), YESTERDAY("Ayer"), WEEK("Últimos 7 días"), MONTH("Últimos 30 días"), OLDER("Anteriores"), UNKNOWN("Actividad")
}

internal data class NotificationSection(val period: NotificationPeriod, val notifications: List<AppNotification>)

/** Sections only organize dates: each event keeps its own actor and row. */
internal fun notificationSections(notifications: List<AppNotification>, nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(), maxItems: Int = Int.MAX_VALUE): List<NotificationSection> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val dated = notifications.map { notification ->
        notification to try { Instant.parse(notification.createdAt) } catch (_: DateTimeParseException) { null }
    }.sortedWith(compareByDescending<Pair<AppNotification, Instant?>> { it.second ?: Instant.MIN }.thenByDescending { it.first.id })
        .take(maxItems.coerceAtLeast(0))
    val groups = dated.groupBy { (_, instant) ->
        if (instant == null) NotificationPeriod.UNKNOWN else {
            val days = ChronoUnit.DAYS.between(instant.atZone(zone).toLocalDate(), today)
            when {
                days <= 0 -> NotificationPeriod.TODAY
                days == 1L -> NotificationPeriod.YESTERDAY
                days <= 7 -> NotificationPeriod.WEEK
                days <= 30 -> NotificationPeriod.MONTH
                else -> NotificationPeriod.OLDER
            }
        }
    }
    return NotificationPeriod.entries.mapNotNull { period ->
        groups[period]?.let { NotificationSection(period, it.map { entry -> entry.first }) }
    }
}
