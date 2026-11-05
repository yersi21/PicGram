package com.example.picgram

import com.example.picgram.data.model.AppNotification
import com.example.picgram.data.model.Conversation
import com.example.picgram.presentation.CommunityUiState
import org.junit.Assert.*
import org.junit.Test

class UnreadIndicatorsTest {
    @Test fun messageCountAddsUnreadMessagesAcrossConversations() {
        val state = CommunityUiState(conversations = listOf(
            Conversation("first", "", "", 3), Conversation("second", "", "", 2),
            Conversation("read", "", "", 0), Conversation("invalid", "", "", -1)))
        assertEquals(5L, state.unreadMessageCount)
        assertEquals(0L, state.unreadNotificationCount)
    }

    @Test fun notificationCountExcludesAlreadyReadItems() {
        val state = CommunityUiState(notifications = listOf(
            AppNotification("first", "peer", "like", "", false),
            AppNotification("read", "peer", "like", "", true),
            AppNotification("second", "peer", "comment", "", false)))
        assertEquals(2L, state.unreadNotificationCount)
        assertEquals(0L, state.unreadMessageCount)
    }

    @Test fun countersReachZeroAfterReadingTheirItems() {
        val state = CommunityUiState(
            conversations = listOf(Conversation("peer", "", "", 4)),
            notifications = listOf(AppNotification("n", "peer", "message", "", false)))
        assertEquals(4L, state.unreadMessageCount)
        assertEquals(1L, state.unreadNotificationCount)
        val read = state.copy(conversations = state.conversations.map { it.copy(unread = 0) },
            notifications = state.notifications.map { it.copy(read = true) })
        assertEquals(0L, read.unreadMessageCount)
        assertEquals(0L, read.unreadNotificationCount)
    }

    @Test fun messageTotalsDoNotOverflowAnInteger() {
        val state = CommunityUiState(conversations = listOf(
            Conversation("first", "", "", Int.MAX_VALUE), Conversation("second", "", "", Int.MAX_VALUE)))
        assertEquals(4_294_967_294L, state.unreadMessageCount)
    }

    @Test fun emptyOrReadListsShowNoDots() {
        assertFalse(CommunityUiState().hasUnreadMessages)
        assertFalse(CommunityUiState().hasUnreadNotifications)
        val read = CommunityUiState(
            conversations = listOf(Conversation("peer", "Hola", "", 0)),
            notifications = listOf(AppNotification("n", "peer", "like", "", true)))
        assertFalse(read.hasUnreadMessages)
        assertFalse(read.hasUnreadNotifications)
    }

    @Test fun anyUnreadConversationShowsMessageDotWithoutNotificationDot() {
        val state = CommunityUiState(conversations = listOf(
            Conversation("read", "Hola", "", 0), Conversation("unread", "Nuevo", "", 3)))
        assertTrue(state.hasUnreadMessages)
        assertFalse(state.hasUnreadNotifications)
    }

    @Test fun anyUnreadNotificationShowsBellDotWithoutMessageDot() {
        val state = CommunityUiState(notifications = listOf(
            AppNotification("read", "peer", "like", "", true),
            AppNotification("new", "peer", "comment", "", false)))
        assertTrue(state.hasUnreadNotifications)
        assertFalse(state.hasUnreadMessages)
    }

    @Test fun indicatorsDisappearOnlyWhenTheirItemsAreRead() {
        val unread = CommunityUiState(
            conversations = listOf(Conversation("peer", "Hola", "", 1)),
            notifications = listOf(AppNotification("n", "peer", "message", "", false)))
        val messagesRead = unread.copy(conversations = unread.conversations.map { it.copy(unread = 0) })
        assertFalse(messagesRead.hasUnreadMessages)
        assertTrue(messagesRead.hasUnreadNotifications)
        val allRead = messagesRead.copy(notifications = messagesRead.notifications.map { it.copy(read = true) })
        assertFalse(allRead.hasUnreadMessages)
        assertFalse(allRead.hasUnreadNotifications)
    }
}
