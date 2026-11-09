package com.example.picgram.data

internal data class PushNotificationContent(val title: String, val body: String)

internal fun pushNotificationContent(data: Map<String, String>): PushNotificationContent {
    val actor = data["actor_name"]?.trim().orEmpty().ifBlank { "PicGram" }
    val preview = data["message_preview"]?.trim().orEmpty()
    val action = when (data["kind"]) {
        "message" -> if (preview.isBlank()) "Te envió un mensaje" else "Te envió un mensaje: $preview"
        "like" -> "Le gusta tu publicación"
        "story_like" -> "Le gusta tu historia"
        "comment" -> "Comentó tu publicación"
        "request" -> "Solicitó seguirte"
        "follow" -> "Comenzó a seguirte"
        else -> "Tienes una nueva notificación"
    }
    return PushNotificationContent(actor, action)
}
