@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example.picgram

import com.example.picgram.data.*
import com.example.picgram.data.model.*
import com.example.picgram.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

class CommunityViewModelTest {
    private val session = UserSession("jwt", "me", "me@test.local", "me")
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private fun container(repository: CommunityRepository, social: SocialRepository = StubSocialRepository()): AppContainer = AppContainer(StubAuthRepository(), social,
        object : SessionRepository {
            override fun currentSession() = session
            override fun saveSession(session: UserSession) {}
            override fun clearSession() {}
            override suspend fun validAccessToken(token: String) = token
        }, StubMediaRepository(), object : DraftRepository {
            override suspend fun load(userId: String): Post? = null
            override suspend fun save(post: Post) {}
            override suspend fun remove(userId: String) {}
        }, community = repository, realtime = object : RealtimeSource {
            override fun changes(token: String, userId: String): Flow<Boolean> = emptyFlow()
        })

    @Test fun profileValidationTrimsNamesAndRejectsInvalidData() {
        assertEquals("Ana", ProfileDetails(name = " Ana ").validated().name)
        listOf(ProfileDetails(name = ""), ProfileDetails(name = "Ana", bio = "x".repeat(501)),
            ProfileDetails(name = "Ana", phone = "x".repeat(31)), ProfileDetails(name = "Ana", birthday = "2999-01-01")).forEach {
            assertThrows(Exception::class.java) { it.validated() }
        }
    }
    @Test fun storyExpiresAtBoundaryAndMessagesCannotBeBlankOrOversized() {
        val story = Story("id", "me", "url", "image", 100)
        assertTrue(story.isActive(99)); assertFalse(story.isActive(100)); assertFalse(story.isActive(101))
        assertEquals("Hola", validatedMessage(" Hola "))
        assertThrows(IllegalArgumentException::class.java) { validatedMessage("  ") }
        assertThrows(IllegalArgumentException::class.java) { validatedMessage("a".repeat(4001)) }
    }
    @Test fun saveProfileRejectsAnotherAccountAndDoesNotReportSuccessOnFailure() = runTest {
        val model = CommunityViewModel(container(object : StubCommunityRepository() {
            override suspend fun saveProfile(token: String, profile: ProfileDetails) { throw IOException("offline") }
        }))
        model.saveProfile(ProfileDetails(id = "other", name = "Ana")); advanceUntilIdle()
        assertNotNull(model.uiState.value.error); assertEquals(0, model.uiState.value.profileSaved)
        model.saveProfile(ProfileDetails(id = "me", name = "Ana")); advanceUntilIdle()
        assertEquals("offline", model.uiState.value.error)
        assertFalse(model.uiState.value.busy); assertEquals(0, model.uiState.value.profileSaved)
    }
    @Test fun messageRetryReusesIdAndContentAndAcknowledgesOnlyLoadedMessages() = runTest {
        val submitted = mutableListOf<DirectMessage>()
        val marked = mutableListOf<String>()
        val loaded = DirectMessage("old", "peer", "me", "Hola", "2026-10-26T10:00:00Z")
        val model = CommunityViewModel(container(object : StubCommunityRepository() {
            override suspend fun profile(token: String, userId: String, own: Boolean) = ProfileDetails(id = userId, username = "peer")
            override suspend fun messages(token: String, peer: String, before: DirectMessage?) = listOf(loaded)
            override suspend fun markMessages(token: String, peer: String, through: String) { marked += through }
            override suspend fun sendMessage(token: String, message: DirectMessage) {
                submitted += message
                if (submitted.size == 1) throw IOException("ambiguous response")
            }
        }))
        model.start(CommunityPage.CHAT, "peer"); runCurrent()
        model.sendMessage("Original"); runCurrent()
        assertNotNull(model.uiState.value.pendingMessage)
        assertFalse(model.uiState.value.sendingMessage)
        model.sendMessage("Changed draft"); runCurrent()
        assertEquals(submitted[0].id, submitted[1].id)
        assertEquals("Original", submitted[1].content)
        assertNull(model.uiState.value.pendingMessage)
        assertFalse(model.uiState.value.sendingMessage)
        assertTrue(marked.all { it == loaded.createdAt })
        model.stop(); runCurrent()
    }
    @Test fun chatLoadsPeerPhotoAndUpdatesOutgoingReadReceiptsWithoutAcknowledgingOwnMessages() = runTest {
        val peer = ProfileDetails(id = "peer", username = "ana", name = "Ana", avatarUrl = "avatar.jpg")
        val outgoing = DirectMessage("out", "me", "peer", "Hola", "2026-10-29T10:00:00Z")
        val incoming = DirectMessage("in", "peer", "me", "Hola también", "2026-10-29T10:01:00Z")
        var seen = false
        val acknowledgements = mutableListOf<String>()
        val model = CommunityViewModel(container(object : StubCommunityRepository() {
            override suspend fun profile(token: String, userId: String, own: Boolean): ProfileDetails {
                assertEquals("peer", userId)
                assertFalse(own)
                return peer
            }
            override suspend fun messages(token: String, peer: String, before: DirectMessage?) =
                listOf(outgoing.copy(readAt = if (seen) "2026-10-29T10:02:00Z" else null), incoming)
            override suspend fun markMessages(token: String, peer: String, through: String) { acknowledgements += through }
        }))
        try {
            model.start(CommunityPage.CHAT, "peer"); runCurrent()
            assertEquals(peer, model.uiState.value.chatProfile)
            assertNull(model.uiState.value.messages.first().readAt)
            seen = true
            model.refresh(); runCurrent()
            assertEquals("2026-10-29T10:02:00Z", model.uiState.value.messages.first().readAt)
            assertEquals(2, model.uiState.value.messages.size)
            assertTrue(acknowledgements.isNotEmpty())
            assertTrue(acknowledgements.all { it == incoming.createdAt })
        } finally { model.stop(); runCurrent() }
    }
    @Test fun busySavePreventsDuplicateRequests() = runTest {
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val model = CommunityViewModel(container(object : StubCommunityRepository() {
            override suspend fun saveProfile(token: String, profile: ProfileDetails) { calls++; gate.await() }
        }))
        val profile = ProfileDetails(id = "me", name = "Ana")
        model.saveProfile(profile); model.saveProfile(profile); runCurrent()
        assertEquals(1, calls); assertTrue(model.uiState.value.busy)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, model.uiState.value.profileSaved); assertFalse(model.uiState.value.busy)
    }

    @Test fun likesAndCommentsOpenTheReferencedPostAndConsumeNavigation() = runTest {
        for (kind in listOf("like", "comment")) {
            val notification = AppNotification("notice", "actor", kind, "", false, postId = "target")
            val target = Post(id = "target", userId = "me", mediaUrl = "photo", caption = "Mi publicación")
            val marked = mutableListOf<String>()
            val model = CommunityViewModel(container(object : StubCommunityRepository() {
                override suspend fun conversations(token: String) = emptyList<Conversation>()
                override suspend fun requests(token: String, me: String) = emptyList<FollowRequest>()
                override suspend fun notifications(token: String, me: String) = listOf(notification)
                override suspend fun markNotification(token: String, id: String) { marked += id }
            }, object : StubSocialRepository() {
                override suspend fun getPost(token: String, postId: String): Post {
                    assertEquals("target", postId)
                    return target
                }
            }))
            model.start(CommunityPage.NOTIFICATIONS); runCurrent()
            model.stop(); runCurrent()
            model.openNotificationPost(notification); runCurrent()
            assertEquals(target, model.uiState.value.postToOpen)
            assertEquals(listOf("notice"), marked)
            assertTrue(model.uiState.value.notifications.single().read)
            model.consumePostNavigation()
            assertNull(model.uiState.value.postToOpen)
            model.stop(); runCurrent()
        }
    }

    @Test fun deletedOrInaccessiblePostShowsErrorWithoutOpeningAnotherDestination() = runTest {
        val model = CommunityViewModel(container(StubCommunityRepository(), object : StubSocialRepository() {
            override suspend fun getPost(token: String, postId: String): Post? = null
        }))
        model.openNotificationPost(AppNotification("n", "actor", "comment", "", false, postId = "gone"))
        advanceUntilIdle()
        assertNull(model.uiState.value.postToOpen)
        assertTrue(model.uiState.value.error.orEmpty().contains("eliminada"))
        assertFalse(model.uiState.value.busy)
    }

    @Test fun missingPostIdDoesNotRequestAPost() = runTest {
        val model = CommunityViewModel(container(StubCommunityRepository()))
        model.openNotificationPost(AppNotification("n", "actor", "like", "", false))
        advanceUntilIdle()
        assertNull(model.uiState.value.postToOpen)
        assertEquals("Esta publicación ya no está disponible", model.uiState.value.error)
    }

    @Test fun failedPostLoadCanBeRetriedWithoutDuplicatingNavigation() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val target = Post(id = "post", userId = "me")
        val model = CommunityViewModel(container(object : StubCommunityRepository() {
            override suspend fun markNotification(token: String, id: String) {}
        }, object : StubSocialRepository() {
            override suspend fun getPost(token: String, postId: String): Post {
                calls++
                if (calls == 1) throw IOException("Sin conexión")
                gate.await()
                return target
            }
        }))
        val notification = AppNotification("n", "actor", "like", "", false, postId = "post")
        model.openNotificationPost(notification); runCurrent()
        assertEquals("Sin conexión", model.uiState.value.error)
        assertNull(model.uiState.value.postToOpen)
        model.openNotificationPost(notification); model.openNotificationPost(notification); runCurrent()
        assertEquals(2, calls)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(target, model.uiState.value.postToOpen)
        assertNull(model.uiState.value.error)
    }
}
