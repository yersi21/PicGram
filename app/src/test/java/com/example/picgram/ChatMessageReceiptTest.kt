package com.example.picgram

import com.example.picgram.data.model.DirectMessage
import com.example.picgram.ui.messageReceipt
import org.junit.Assert.*
import org.junit.Test

class ChatMessageReceiptTest {
    private val sent = DirectMessage("id", "me", "peer", "Hola", "2026-10-29T10:00:00Z")

    @Test fun confirmedOutgoingMessageIsSentUntilTheRecipientReadsIt() {
        assertEquals("Enviado", messageReceipt(sent, "me"))
        assertEquals("Visto", messageReceipt(sent.copy(readAt = "2026-10-29T10:01:00Z"), "me"))
    }

    @Test fun emptyReadReceiptDoesNotMeanSeen() {
        assertEquals("Enviado", messageReceipt(sent.copy(readAt = ""), "me"))
        assertEquals("Enviado", messageReceipt(sent.copy(readAt = "  "), "me"))
    }

    @Test fun incomingMessagesDoNotDisplayAnOutgoingDeliveryStatus() {
        assertNull(messageReceipt(sent.copy(senderId = "peer", recipientId = "me"), "me"))
        assertNull(messageReceipt(sent.copy(senderId = "peer", recipientId = "me", readAt = "2026-10-29T10:01:00Z"), "me"))
    }

    @Test fun unconfirmedDraftNeverAppearsAsSentOrSeen() {
        assertNull(messageReceipt(sent.copy(createdAt = ""), "me"))
        assertNull(messageReceipt(sent.copy(createdAt = "", readAt = "2026-10-29T10:01:00Z"), "me"))
    }
}
