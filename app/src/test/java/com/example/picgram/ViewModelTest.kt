@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.picgram

import com.example.picgram.data.*
import com.example.picgram.presentation.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ViewModelTest {
    private val account = UserSession("jwt", "me", "me@example.test", "Yo")
    private val post = Post(id = "post", userId = "author", mediaUrl = "https://example.test/photo.jpg", likesCount = 4)

    @Before fun setMainDispatcher() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    private class MemorySession(var account: UserSession?) : SessionRepository {
        override fun currentSession() = account
        override fun saveSession(session: UserSession) { account = session }
        override fun clearSession() { account = null }
        override suspend fun validAccessToken(token: String) = token
    }
    private class MemoryDrafts : DraftRepository {
        val stored = mutableMapOf<String, Post>()
        override suspend fun load(userId: String) = stored[userId]
        override suspend fun save(post: Post) { stored[post.userId] = post }
        override suspend fun remove(userId: String) { stored.remove(userId) }
    }
    private fun container(social: SocialRepository = StubSocialRepository(), auth: AuthRepository = StubAuthRepository(),
        session: SessionRepository = MemorySession(account), drafts: DraftRepository = MemoryDrafts(),
        theme: ThemeRepository = ThemeRepository()) = AppContainer(
        auth, social, session, object : StubMediaRepository() {
            override suspend fun flushCleanup(session: UserSession) {}
        }, drafts, EmailValidator { it.contains('@') }, theme)

    @Test fun feedChangesAppearanceWithoutCallingNetworkOrReloadingPosts() = runTest {
        val theme = ThemeRepository()
        val model = FeedViewModel(container(theme = theme))
        val originalFeed = model.uiState.value
        model.setDarkMode(true)
        assertEquals(true, theme.darkMode.value)
        model.setDarkMode(false)
        assertEquals(false, theme.darkMode.value)
        assertEquals(originalFeed, model.uiState.value)
        assertTrue(model.notices.value.isEmpty())
    }

    @Test fun loginUsesTheProfileFallbackAndPersistsTheCompleteSession() = runTest {
        val local = MemorySession(null)
        var createdUsername = ""
        val model = LoginViewModel(container(session = local, auth = object : StubAuthRepository() {
            override suspend fun signIn(email: String, password: String): AuthResult {
                assertEquals("ana@example.test", email)
                return AuthResult("new-jwt", "ana", email, "refresh", 12345L, "Ana", "Ana López")
            }
        }, social = object : StubSocialRepository() {
            override suspend fun getUsername(token: String, userId: String) = ""
            override suspend fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long, displayName: String) {
                createdUsername = username
                assertEquals("Ana López", displayName)
            }
        }))
        model.handleLogin(" ana@example.test ", "password"); advanceUntilIdle()
        assertEquals("Ana", createdUsername)
        assertEquals("refresh", local.account?.refreshToken)
        assertEquals(12345L, local.account?.expiresAt)
        assertEquals(Destination.FEED, model.notices.value.last().destination)
    }

    @Test fun registerFailureAfterCreatingAccountDoesNotPersistAnIncompleteSession() = runTest {
        val local = MemorySession(null)
        val model = RegisterViewModel(container(session = local, auth = object : StubAuthRepository() {
            override suspend fun signUp(email: String, password: String, username: String, displayName: String) = AuthResult("jwt", "ana", email)
        }, social = object : StubSocialRepository() {
            override suspend fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long, displayName: String) {
                throw IOException("offline")
            }
        }))
        model.handleRegister("Ana López", "Ana", "ana@example.test", "password"); advanceUntilIdle()
        assertNull(local.account)
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.notices.value.single().message!!.contains("Tu cuenta se creo"))
    }

    @Test fun recoveryRequestDisablesRepeatSendingAndRetainsSentState() = runTest {
        var mails = 0
        val model = ForgotPasswordViewModel(container(auth = object : StubAuthRepository() {
            override suspend fun requestPasswordRecovery(email: String) { mails++ }
        }))
        model.sendRecovery("ana@example.test"); model.sendRecovery("ana@example.test"); advanceUntilIdle()
        assertTrue(model.uiState.value.sent)
        assertFalse(model.uiState.value.sending)
        model.sendRecovery("ana@example.test"); advanceUntilIdle()
        assertEquals(1, mails)
    }

    @Test fun commentRetryReusesTheIdAndAddsOnlyOneComment() = runTest {
        val pending = Comment(id = "comment-id", postId = post.id, userId = account.userId, content = "Hola", username = "Yo")
        val ids = mutableListOf<String>()
        val model = PostDetailViewModel(container(social = object : StubSocialRepository() {
            override suspend fun getLikesForPost(token: String, postId: String, currentUserId: String) = 4 to false
            override suspend fun getComments(token: String, postId: String) = emptyList<Comment>()
            override suspend fun getAvatarUrls(token: String, userIds: List<String>) = emptyMap<String, String>()
            override suspend fun addComment(token: String, postId: String, userId: String, username: String,
                content: String, timestamp: Long, id: String): Comment {
                ids += id
                if (ids.size == 1) throw IOException("ambiguous response")
                return pending
            }
        }))
        model.initialize(post, "Hola", pending); advanceUntilIdle()
        model.handleAddComment(account, post.id); advanceUntilIdle()
        assertEquals("comment-id", model.uiState.value.pendingComment?.id)
        model.handleAddComment(account, post.id); advanceUntilIdle()
        assertEquals(listOf("comment-id", "comment-id"), ids)
        assertEquals(1, model.uiState.value.comments.size)
        assertNull(model.uiState.value.pendingComment)
        assertEquals("", model.uiState.value.commentText)
    }

    @Test fun failedFollowRollsBackTheFollowingFlagAndFollowerCount() = runTest {
        val model = ProfileViewModel(container(social = object : StubSocialRepository() {
            override suspend fun getDisplayName(token: String, userId: String) = "Ana López"
            override suspend fun getUserPosts(token: String, userId: String) = emptyList<Post>()
            override suspend fun getAvatarUrl(token: String, userId: String) = ""
            override suspend fun isFollowing(token: String, followerId: String, followingId: String) = false
            override suspend fun getFollowersCount(token: String, userId: String) = 8
            override suspend fun getFollowingCount(token: String, userId: String) = 2
            override suspend fun followUser(token: String, followerId: String, followingId: String) { throw IOException("offline") }
        }))
        model.initialize("other-user"); advanceUntilIdle()
        model.handleFollowToggle(account.accessToken, account.userId, "other-user")
        assertTrue(model.uiState.value.isFollowing)
        assertEquals(9, model.uiState.value.followersCount)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isFollowing)
        assertEquals(8, model.uiState.value.followersCount)
        assertFalse(model.uiState.value.isTogglingFollow)
    }

    @Test fun doubleTapAddsOnlyOnceEvenWhileRequestIsPendingOrAlreadyLiked() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val model = FeedViewModel(container(social = object : StubSocialRepository() {
            override suspend fun getPosts(token: String, currentUserId: String) = listOf(post)
            override suspend fun toggleLike(token: String, postId: String, userId: String, currentlyLiked: Boolean) {
                calls++; gate.await()
            }
        }))
        model.loadPosts(); advanceUntilIdle()
        model.handleLike(post, addOnly = true)
        assertTrue(model.uiState.value.posts.single().likedByMe)
        assertEquals(5, model.uiState.value.posts.single().likesCount)
        model.handleLike(post, addOnly = true); runCurrent()
        assertEquals(1, calls)
        gate.complete(Unit); advanceUntilIdle()
        model.handleLike(post, addOnly = true); advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun failedLikeRestoresOriginalCountAndExposesAnAcknowledgedNotice() = runTest {
        val model = FeedViewModel(container(social = object : StubSocialRepository() {
            override suspend fun getPosts(token: String, currentUserId: String) = listOf(post)
            override suspend fun toggleLike(token: String, postId: String, userId: String, currentlyLiked: Boolean) {
                throw IOException("offline")
            }
        }))
        model.loadPosts(); advanceUntilIdle()
        model.handleLike(post, true); advanceUntilIdle()
        assertFalse(model.uiState.value.posts.single().likedByMe)
        assertEquals(4, model.uiState.value.posts.single().likesCount)
        val notice = model.notices.value.single()
        assertEquals("Error al actualizar like", notice.message)
        model.acknowledge(notice.id)
        assertTrue(model.notices.value.isEmpty())
    }

    @Test fun expiredSessionClearsLocalAccountAndRequestsLogin() = runTest {
        val session = MemorySession(account)
        val model = FeedViewModel(container(session = session, social = object : StubSocialRepository() {
            override suspend fun getPosts(token: String, currentUserId: String): List<Post> = throw SessionExpiredException()
        }))
        model.loadPosts(); advanceUntilIdle()
        assertNull(session.account)
        assertEquals(Destination.LOGIN, model.notices.value.last().destination)
        assertFalse(model.uiState.value.isLoadingFeed)
    }

    @Test fun initializationDoesNotRepeatWhenTheActivityIsRecreated() = runTest {
        var validations = 0
        var feeds = 0
        val model = FeedViewModel(container(auth = object : StubAuthRepository() {
            override suspend fun validateToken(token: String): Boolean { validations++; return true }
        }, social = object : StubSocialRepository() {
            override suspend fun getPosts(token: String, currentUserId: String): List<Post> { feeds++; return listOf(post) }
        }))
        model.initialize(); model.initialize(); advanceUntilIdle()
        assertEquals(1, validations)
        assertEquals(1, feeds)
    }

    @Test fun searchDebouncesCancelsEarlierQueriesAndClearsResults() = runTest {
        val queries = mutableListOf<String>()
        val model = SearchViewModel(container(social = object : StubSocialRepository() {
            override suspend fun searchUsers(token: String, query: String): List<UserProfile> {
                queries += query
                return listOf(UserProfile(id = "user", username = query))
            }
        }))
        model.search("a"); runCurrent(); advanceTimeBy(200)
        model.search("ana"); runCurrent(); advanceTimeBy(399); runCurrent()
        assertTrue(queries.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("ana"), queries)
        assertEquals("ana", model.uiState.value.results.single().username)
        model.search("")
        assertTrue(model.uiState.value.results.isEmpty())
        assertFalse(model.uiState.value.hasSearched)
    }

    @Test fun searchNormalizesHandlesAndPreservesLiteralPunctuation() = runTest {
        val queries = mutableListOf<String>()
        val expected = UserProfile(id = "selected-id", username = "ana_test", displayName = "Ana López")
        val model = SearchViewModel(container(social = object : StubSocialRepository() {
            override suspend fun searchUsers(token: String, query: String): List<UserProfile> {
                queries += query
                return listOf(expected)
            }
        }))
        model.search("  @ana_test  "); advanceUntilIdle()
        assertEquals(listOf("ana_test"), queries)
        assertEquals(expected, model.uiState.value.results.single())
        model.search("O'Brien%,(x)")
        assertTrue(model.uiState.value.results.isEmpty())
        advanceUntilIdle()
        assertEquals("O'Brien%,(x)", queries.last())
        model.search(" @ "); advanceUntilIdle()
        assertFalse(model.uiState.value.isSearching)
        assertTrue(model.uiState.value.results.isEmpty())
        assertEquals(2, queries.size)
    }

    @Test fun searchRejectsExcessiveQueryWithoutCallingRepository() = runTest {
        val model = SearchViewModel(container())
        model.search("a".repeat(81)); advanceUntilIdle()
        assertNotNull(model.uiState.value.searchError)
        assertFalse(model.uiState.value.isSearching)
    }

    @Test fun searchNeverPublishesAnOldNonCancellableResponse() = runTest {
        val gate = CompletableDeferred<Unit>()
        val model = SearchViewModel(container(social = object : StubSocialRepository() {
            override suspend fun searchUsers(token: String, query: String): List<UserProfile> {
                if (query == "old") withContext(NonCancellable) { gate.await() }
                return listOf(UserProfile(id = query, username = query))
            }
        }))
        model.search("old"); advanceTimeBy(400); runCurrent()
        model.search("new"); advanceTimeBy(400); runCurrent()
        assertEquals("new", model.uiState.value.results.single().id)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals("new", model.uiState.value.results.single().id)
    }

    @Test fun searchFailureIsVisibleAndCanBeRetried() = runTest {
        var attempts = 0
        val model = SearchViewModel(container(social = object : StubSocialRepository() {
            override suspend fun searchUsers(token: String, query: String): List<UserProfile> {
                if (++attempts == 1) throw IOException("offline")
                return listOf(UserProfile(id = "ana", username = "ana"))
            }
        }))
        model.search("Ana"); advanceUntilIdle()
        assertNotNull(model.uiState.value.searchError)
        assertFalse(model.uiState.value.isSearching)
        model.search(); advanceUntilIdle()
        assertNull(model.uiState.value.searchError)
        assertEquals("ana", model.uiState.value.results.single().id)
    }

    private fun profileRepository() = object : StubSocialRepository() {
        override suspend fun getDisplayName(token: String, userId: String) = "Ana López"
        override suspend fun getAvatarUrl(token: String, userId: String) = ""
        override suspend fun getUserPosts(token: String, userId: String) = listOf(post.copy(userId = userId))
        override suspend fun isFollowing(token: String, followerId: String, followingId: String) = false
        override suspend fun getFollowersCount(token: String, userId: String) = 0
        override suspend fun getFollowingCount(token: String, userId: String) = 0
    }

    @Test fun selectedProfileLoadsNameAndPostsByAccountId() = runTest {
        val model = ProfileViewModel(container(social = profileRepository()))
        model.initialize("selected-id"); advanceUntilIdle()
        assertEquals("Ana López", model.uiState.value.displayName)
        assertEquals("selected-id", model.uiState.value.posts.single().userId)
        assertFalse(model.uiState.value.isOwnProfile)
    }

    @Test fun registerRequiresNameBeforeCreatingAnAccount() = runTest {
        val model = RegisterViewModel(container())
        for (name in listOf("", " ", "A", "a".repeat(81), "Ana\nMaría")) {
            model.handleRegister(name, "ana_test", "ana@example.test", "password")
            advanceUntilIdle()
            assertFalse(model.uiState.value.isLoading)
        }
        assertEquals(5, model.notices.value.size)
    }

    @Test fun registerSendsSeparateTrimmedNamesToAuthAndProfileAndPreventsRepeatSending() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var profileName = ""
        val local = MemorySession(null)
        val model = RegisterViewModel(container(session = local, auth = object : StubAuthRepository() {
            override suspend fun signUp(email: String, password: String, username: String, displayName: String): AuthResult {
                calls++
                assertEquals("Ana María", displayName)
                assertEquals("ana_test", username)
                assertEquals("ana@example.test", email)
                gate.await()
                return AuthResult("jwt", "ana", email)
            }
        }, social = object : StubSocialRepository() {
            override suspend fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long, displayName: String) {
                assertEquals("ana_test", username)
                profileName = displayName
            }
        }))
        model.handleRegister("  Ana María  ", " ana_test ", " ana@example.test ", "password")
        model.handleRegister("Otro nombre", "otro", "otro@example.test", "password"); runCurrent()
        assertEquals(1, calls)
        assertTrue(model.uiState.value.isLoading)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals("Ana María", profileName)
        assertEquals("ana_test", local.account?.username)
        assertEquals(Destination.FEED, model.notices.value.last().destination)
    }

    @Test fun connectionsPreventOverlappingLoadsAndAllowRetryAfterFailure() = runTest {
        var attempts = 0
        val gate = CompletableDeferred<Unit>()
        val model = ConnectionsViewModel(container(social = object : StubSocialRepository() {
            override suspend fun getConnections(token: String, userId: String, type: ConnectionType): List<UserProfile> {
                assertEquals("author", userId)
                assertEquals(ConnectionType.FOLLOWERS, type)
                attempts++; gate.await()
                if (attempts == 1) throw IOException("offline")
                return listOf(UserProfile(id = "follower", username = "Ana"))
            }
        }))
        model.initialize("author", ConnectionType.FOLLOWERS)
        model.reload(); model.reload(); runCurrent()
        assertEquals(1, attempts)
        gate.complete(Unit); advanceUntilIdle()
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.loading)
        model.reload(); advanceUntilIdle()
        assertEquals(2, attempts)
        assertNull(model.uiState.value.error)
        assertEquals("follower", model.uiState.value.users.single().id)
    }

    @Test fun postRetryReusesItsOriginalDraftIdAndClearsOnlyThatOwnersDraft() = runTest {
        val drafts = MemoryDrafts()
        val pending = post.copy(id = "draft-id", userId = account.userId, caption = "Original")
        drafts.stored["other"] = post.copy(userId = "other")
        val ids = mutableListOf<String>()
        val model = CreatePostViewModel(container(drafts = drafts, social = object : StubSocialRepository() {
            override suspend fun insertPost(token: String, userId: String, userEmail: String, username: String,
                caption: String, mediaUrl: String, mediaType: String, timestamp: Long, id: String) {
                ids += id
                assertEquals("Original", caption)
                if (ids.size == 1) throw IOException("ambiguous response")
            }
        }))
        model.initialize(null, "image", pending); advanceUntilIdle()
        model.startUpload("Changed"); advanceUntilIdle()
        assertEquals("draft-id", model.uiState.value.pendingPost?.id)
        assertEquals("draft-id", drafts.stored[account.userId]?.id)
        model.startUpload("Changed again"); advanceUntilIdle()
        assertEquals(listOf("draft-id", "draft-id"), ids)
        assertNull(model.uiState.value.pendingPost)
        assertFalse(drafts.stored.containsKey(account.userId))
        assertTrue(drafts.stored.containsKey("other"))
        assertEquals(Destination.CLOSE, model.notices.value.last().destination)
    }

    @Test fun textOnlySubmissionIsRejectedBeforeAnyRepositoryWrite() = runTest {
        val model = CreatePostViewModel(container())
        model.initialize(null, "image", null); advanceUntilIdle()
        model.startUpload("Solo texto"); advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.notices.value.single().message!!.contains("imagen o un video"))
    }

    @Test fun recoveryDoesNotClearADifferentUsersLocalSession() = runTest {
        val local = MemorySession(account)
        val recovery = AuthResult("recovery-jwt", "different-user", "different@example.test", "refresh")
        var closed = false
        val model = ResetPasswordViewModel(container(session = local, auth = object : StubAuthRepository() {
            override suspend fun verifyRecoveryLink(link: RecoveryLink) = recovery
            override suspend fun updateRecoveredPassword(session: AuthResult, password: String) = "updated-token"
            override suspend fun closeRecoverySession(token: String) { closed = true }
        }))
        model.acceptLink("${RecoveryLinks.REDIRECT_URL}?token_hash=hash&type=recovery"); advanceUntilIdle()
        assertTrue(model.ready)
        model.savePassword("new-password-123", "new-password-123"); advanceUntilIdle()
        assertTrue(model.success)
        assertTrue(closed)
        assertEquals(account, local.account)
    }

    @Test fun invalidRecoveryLinkNeverCallsAuthenticationRepository() = runTest {
        val model = ResetPasswordViewModel(container())
        model.acceptLink("https://invalid.example/reset"); advanceUntilIdle()
        assertFalse(model.ready)
        assertFalse(model.verifying)
        assertNotNull(model.error)
    }
}
