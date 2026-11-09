package com.example.picgram.ui

import com.example.picgram.data.model.UserPresence
import com.example.picgram.data.model.monotonicMillis

internal fun presenceLabel(presence: UserPresence?, now: Long = monotonicMillis()): String {
    if (presence == null) return "Estado no disponible"
    if (presence.online) return "En línea"
    val age = presence.lastSeenAgeMillis?.plus((now - presence.fetchedAt).coerceAtLeast(0))
        ?: return "Sin conexión"
    val minutes = age / 60_000
    return when {
        minutes < 1 -> "En línea hace un momento"
        minutes < 60 -> "En línea hace $minutes min"
        minutes < 1440 -> "En línea hace ${minutes / 60} h"
        else -> "En línea hace ${minutes / 1440} días"
    }
}
