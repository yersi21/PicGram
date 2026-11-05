package com.example.picgram

import com.example.picgram.data.model.Conversation
import com.example.picgram.ui.filterConversations
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesInboxTest {
    private val conversations = listOf(
        Conversation("ana", "Hola", "", 2, "ana_fotos", displayName = "Ana María"),
        Conversation("luis", "Hola", "", 0, "luis", displayName = "Luis Fernando"),
        Conversation("sol", "Hola", "", 1, "sol"))

    @Test fun searchMatchesNamesAndUsernamesIgnoringCaseAndOptionalAtSign() {
        assertEquals(listOf(conversations[0]), filterConversations(conversations, "  @ANA_FOTOS ", false))
        assertEquals(listOf(conversations[1]), filterConversations(conversations, "fernando", false))
    }

    @Test fun unreadFilterPreservesConversationOrderAndWorksWithSearch() {
        assertEquals(listOf(conversations[0], conversations[2]), filterConversations(conversations, "", true))
        assertEquals(emptyList<Conversation>(), filterConversations(conversations, "luis", true))
        assertEquals(listOf(conversations[2]), filterConversations(conversations, "sol", true))
    }

    @Test fun emptySearchShowsAllConversationsAndUnknownSearchShowsNone() {
        assertEquals(conversations, filterConversations(conversations, "  ", false))
        assertEquals(emptyList<Conversation>(), filterConversations(conversations, "unknown", false))
    }
}
