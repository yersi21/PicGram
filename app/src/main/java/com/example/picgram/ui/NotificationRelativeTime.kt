package com.example.picgram.ui

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** Formats server timestamps independently of the device's time zone. */
fun notificationRelativeTime(createdAt: String, nowMillis: Long): String {
    val created = try {
        Instant.parse(createdAt)
    } catch (_: DateTimeParseException) {
        return "Fecha no disponible"
    }
    val seconds = Duration.between(created, Instant.ofEpochMilli(nowMillis)).seconds
    return when {
        seconds < 0 -> "Ahora"
        seconds < 60 -> "Hace unos segundos"
        seconds < 3_600 -> relativeUnits(seconds / 60, "minuto", "minutos")
        seconds < 86_400 -> relativeUnits(seconds / 3_600, "hora", "horas")
        else -> relativeUnits(seconds / 86_400, "día", "días")
    }
}

private fun relativeUnits(count: Long, singular: String, plural: String): String =
    "Hace $count ${if (count == 1L) singular else plural}"
