package com.example.picgram

import com.example.picgram.data.pushNotificationContent
import org.junit.Assert.assertEquals
import org.junit.Test

class PushNotificationContentTest {
    @Test fun messageIncludesSenderActionAndPreview() {
        val content = pushNotificationContent(mapOf("kind" to "message", "actor_name" to " Ana ", "message_preview" to "Hola 👋"))
        assertEquals("Ana", content.title)
        assertEquals("Te envió un mensaje: Hola 👋", content.body)
    }

    @Test fun olderPayloadStillHasUsefulContent() {
        val content = pushNotificationContent(mapOf("kind" to "message"))
        assertEquals("PicGram", content.title)
        assertEquals("Te envió un mensaje", content.body)
    }

    @Test fun socialEventsDescribeTheirAction() {
        mapOf("like" to "Le gusta tu publicación", "story_like" to "Le gusta tu historia",
            "comment" to "Comentó tu publicación", "request" to "Solicitó seguirte",
            "follow" to "Comenzó a seguirte").forEach { (kind, action) ->
            assertEquals(action, pushNotificationContent(mapOf("kind" to kind)).body)
        }
    }
}
