package com.example.picgram.presentation

import android.net.Uri
import com.example.picgram.Post
import androidx.lifecycle.viewModelScope
import com.example.picgram.data.AppContainer
import com.example.picgram.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class StoryReplyUiState(val draft: String = "", val pending: DirectMessage? = null,
    val sending: Boolean = false, val feedback: String? = null)

data class StoryViewersUiState(val viewers: List<StoryViewer> = emptyList(),
    val loading: Boolean = false, val error: String? = null)

data class CommunityUiState(
    val profile: ProfileDetails? = null, val stories: List<Story> = emptyList(),
    val conversations: List<Conversation> = emptyList(), val messages: List<DirectMessage> = emptyList(),
    val notifications: List<AppNotification> = emptyList(), val requests: List<FollowRequest> = emptyList(),
    val busy: Boolean = false, val loading: Boolean = false, val error: String? = null,
    val connected: Boolean = false, val pendingMessage: DirectMessage? = null,
    val profileSaved: Int = 0, val storyPublished: Int = 0, val messageSent: Int = 0,
    val hasOlderMessages: Boolean = false,
    val hasPendingStory: Boolean = false,
    val postToOpen: Post? = null,
    val chatProfile: ProfileDetails? = null,
    val sendingMessage: Boolean = false,
    val pendingStoryLikes: Set<String> = emptySet(),
    val storyReplies: Map<String, StoryReplyUiState> = emptyMap(),
    val storyViewers: Map<String, StoryViewersUiState> = emptyMap(),
    val storyToOpen: String? = null
) {
    val unreadMessageCount: Long get() = conversations.sumOf { it.unread.coerceAtLeast(0).toLong() }
    val unreadNotificationCount: Long get() = notifications.count { !it.read }.toLong()
    val hasUnreadMessages: Boolean get() = unreadMessageCount > 0
    val hasUnreadNotifications: Boolean get() = unreadNotificationCount > 0
}
enum class CommunityPage { HOME, EDIT_PROFILE, CONVERSATIONS, CHAT, NOTIFICATIONS, CREATE_STORY }
class CommunityViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val state = MutableStateFlow(CommunityUiState())
    val uiState = state.asStateFlow()
    private var active: Job? = null
    private var reload: Job? = null
    private var reloadPending = false
    private val refreshLock = Mutex()
    private var page = CommunityPage.HOME
    private var peer = ""
    private var pendingStory: Triple<String, String, String>? = null
    private var storyLikeRevision = 0
    private val recordedStoryViews = mutableSetOf<String>()
    private val pendingStoryViews = mutableSetOf<String>()

    fun start(page: CommunityPage, peer: String = "") {
        if (active?.isActive == true) return
        this.page = page; this.peer = peer
        refresh()
        active = viewModelScope.launch {
            if (page in listOf(CommunityPage.EDIT_PROFILE, CommunityPage.CREATE_STORY)) return@launch
            launch { while (isActive) { delay(30_000); refresh() } }
            while (isActive) {
                val session = repositories.session.currentSession() ?: break
                try {
                    val token = repositories.session.validAccessToken(session.accessToken)
                    withTimeout(50_000) {
                        repositories.realtime.changes(token, session.userId).conflate().collect {
                            state.update { s -> s.copy(connected = true) }; refresh()
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    // Refresh the JWT regularly; never leave an expired WebSocket alive.
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    state.update { it.copy(connected = false) }
                    if (handleSessionError(error)) break
                    delay(2_000)
                }
                delay(1_000)
            }
        }
    }
    fun stop() { active?.cancel(); active = null; reload?.cancel(); state.update { it.copy(connected = false) } }

    fun refresh() {
        if (reload?.isActive == true) { reloadPending = true; return }
        reload = viewModelScope.launch {
            do { reloadPending = false; refreshData() } while (reloadPending)
        }
    }
    private suspend fun refreshData() = refreshLock.withLock {
        val session = repositories.session.currentSession() ?: run { navigate(Destination.LOGIN); return@withLock }
        state.update { it.copy(loading = true, error = null) }
        try {
            val token = session.accessToken
            when (page) {
                CommunityPage.EDIT_PROFILE -> {
                    val profile = repositories.community.profile(token, session.userId, true)
                    state.update { it.copy(profile = profile) }
                }
                CommunityPage.HOME, CommunityPage.CONVERSATIONS, CommunityPage.NOTIFICATIONS -> {
                    if (page == CommunityPage.HOME) {
                        val revision = storyLikeRevision
                        val stories = repositories.community.stories(token)
                        state.update { current -> current.copy(stories = stories.map { story ->
                            if (revision != storyLikeRevision || story.id in current.pendingStoryLikes)
                                story.copy(likedByMe = current.stories.firstOrNull { it.id == story.id }?.likedByMe ?: story.likedByMe)
                            else story
                        }) }
                    }
                    val conversations = repositories.community.conversations(token)
                    val notifications = repositories.community.notifications(token, session.userId)
                    val requests = repositories.community.requests(token, session.userId)
                    state.update { it.copy(conversations = conversations, notifications = notifications, requests = requests) }
                    if (page == CommunityPage.CONVERSATIONS) {
                        val profile = repositories.community.profile(token, session.userId, true)
                        state.update { it.copy(profile = profile) }
                    }
                }
                CommunityPage.CHAT -> {
                    val messages = repositories.community.messages(token, peer)
                    // Preserve previously loaded history on live updates.
                    state.update { it.copy(messages = (it.messages + messages).associateBy { m -> m.id }.values
                        .sortedWith(compareBy<DirectMessage> { m -> m.createdAt }.thenBy { m -> m.id }),
                        hasOlderMessages = if (it.messages.isEmpty()) messages.size == 50 else it.hasOlderMessages) }
                    messages.filter { it.recipientId == session.userId }.maxByOrNull { it.createdAt }?.let {
                        repositories.community.markMessages(token, peer, it.createdAt)
                    }
                    val chatProfile = repositories.community.profile(token, peer, false)
                    state.update { it.copy(chatProfile = chatProfile) }
                }
                CommunityPage.CREATE_STORY -> Unit
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!handleSessionError(error)) state.update { it.copy(error = error.message ?: "No se pudo cargar") }
        } finally { state.update { it.copy(loading = false) } }
    }
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!handleSessionError(error)) state.update { it.copy(error = error.message ?: "No se pudo completar") }
            } finally { state.update { it.copy(busy = false) } }
        }
    }
    fun saveProfile(profile: ProfileDetails) = action {
        val session = repositories.session.currentSession() ?: return@action
        require(profile.id == session.userId) { "Solo puedes editar tu perfil" }
        val valid = profile.validated()
        repositories.community.saveProfile(session.accessToken, valid)
        state.update { it.copy(profile = valid, profileSaved = it.profileSaved + 1) }
    }
    fun uploadAvatar(uri: Uri) = action {
        val session = repositories.session.currentSession() ?: return@action
        val previous = repositories.social.getAvatarUrl(session.accessToken, session.userId)
        val url = repositories.media.uploadProfilePhoto(uri, session)
        repositories.social.updateAvatarUrl(session.accessToken, session.userId, url)
        state.update { it.copy(profile = it.profile?.copy(avatarUrl = url)) }
        repositories.media.extractPath(previous)?.let { path ->
            if (com.example.picgram.isOwnedAvatarPath(session.userId, path)) {
                repositories.media.scheduleCleanup(session.userId, path)
                repositories.media.flushCleanup(session)
            }
        }
    }
    fun publishStory(uri: Uri?) = action {
        val session = repositories.session.currentSession() ?: return@action
        if (pendingStory == null) {
            requireNotNull(uri) { "Selecciona una imagen o un video" }
            val uploaded = repositories.media.uploadStoryMedia(uri, session)
            pendingStory = Triple(UUID.randomUUID().toString(), uploaded.first, uploaded.second)
            state.update { it.copy(hasPendingStory = true) }
        }
        val pending = requireNotNull(pendingStory)
        repositories.community.createStory(session.accessToken, pending.first, session.userId, pending.second, pending.third)
        pendingStory = null
        state.update { it.copy(storyPublished = it.storyPublished + 1, hasPendingStory = false) }
    }
    fun sendMessage(content: String) = action {
        val session = repositories.session.currentSession() ?: return@action
        require(peer.isNotBlank() && peer != session.userId) { "Selecciona otro usuario" }
        val pending = state.value.pendingMessage ?: DirectMessage(UUID.randomUUID().toString(), session.userId, peer, validatedMessage(content), "")
        state.update { it.copy(pendingMessage = pending) }
        state.update { it.copy(sendingMessage = true) }
        try {
            repositories.community.sendMessage(session.accessToken, pending)
            state.update { it.copy(pendingMessage = null, messageSent = it.messageSent + 1) }
            refreshData()
        } finally { state.update { it.copy(sendingMessage = false) } }
    }
    fun loadOlder() = action {
        val session = repositories.session.currentSession() ?: return@action
        val before = state.value.messages.firstOrNull() ?: return@action
        val older = repositories.community.messages(session.accessToken, peer, before)
        state.update { it.copy(messages = (older + it.messages).distinctBy { m -> m.id }, hasOlderMessages = older.size == 50) }
    }
    fun resolveRequest(request: FollowRequest, accept: Boolean) = action {
        val session = repositories.session.currentSession() ?: return@action
        repositories.community.resolveRequest(session.accessToken, request.userId, accept)
        refreshData()
    }
    fun markNotification(id: String) = action {
        val session = repositories.session.currentSession() ?: return@action
        repositories.community.markNotification(session.accessToken, id)
        state.update { it.copy(notifications = it.notifications.map { n -> if (n.id == id) n.copy(read = true) else n }) }
    }
    fun openNotificationPost(notification: AppNotification) = action {
        val session = repositories.session.currentSession() ?: return@action
        require(notification.kind in listOf("like", "comment")) { "Esta notificación no corresponde a una publicación" }
        require(notification.postId.isNotBlank()) { "Esta publicación ya no está disponible" }
        val post = repositories.social.getPost(session.accessToken, notification.postId)
            ?: error("Esta publicación fue eliminada o ya no tienes acceso a ella")
        repositories.community.markNotification(session.accessToken, notification.id)
        state.update { it.copy(postToOpen = post,
            notifications = it.notifications.map { n -> if (n.id == notification.id) n.copy(read = true) else n }) }
    }
    fun consumePostNavigation() { state.update { it.copy(postToOpen = null) } }

    fun openNotificationStory(notification: AppNotification) = action {
        val session = repositories.session.currentSession() ?: return@action
        require(notification.kind == "story_like" && notification.storyId.isNotBlank()) { "Esta historia ya no está disponible" }
        val story = repositories.community.stories(session.accessToken).firstOrNull { it.id == notification.storyId }
        require(story != null && story.isActive(monotonicMillis())) { "Esta historia venció o ya no está disponible" }
        repositories.community.markNotification(session.accessToken, notification.id)
        state.update { it.copy(storyToOpen = story.id,
            notifications = it.notifications.map { n -> if (n.id == notification.id) n.copy(read = true) else n }) }
    }
    fun consumeStoryNavigation() { state.update { it.copy(storyToOpen = null) } }

    fun storyOpened(storyId: String) {
        val session = repositories.session.currentSession() ?: return
        val story = state.value.stories.firstOrNull { it.id == storyId } ?: return
        if (!story.isActive(monotonicMillis())) return
        if (story.userId == session.userId) { loadStoryViewers(storyId); return }
        if (storyId in recordedStoryViews || !pendingStoryViews.add(storyId)) return
        viewModelScope.launch {
            try {
                repositories.community.recordStoryView(session.accessToken, storyId)
                recordedStoryViews.add(storyId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!handleSessionError(error)) notify("No se pudo registrar la vista de la historia. Se reintentará al volver a abrirla.")
            } finally { pendingStoryViews.remove(storyId) }
        }
    }

    fun loadStoryViewers(storyId: String) {
        val session = repositories.session.currentSession() ?: return
        val story = state.value.stories.firstOrNull { it.id == storyId } ?: return
        if (story.userId != session.userId || !story.isActive(monotonicMillis())) return
        val previous = state.value.storyViewers[storyId] ?: StoryViewersUiState()
        if (previous.loading) return
        state.update { it.copy(storyViewers = it.storyViewers + (storyId to previous.copy(loading = true, error = null))) }
        viewModelScope.launch {
            try {
                val viewers = repositories.community.storyViewers(session.accessToken, storyId)
                state.update { it.copy(storyViewers = it.storyViewers + (storyId to StoryViewersUiState(viewers))) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state.update { it.copy(storyViewers = it.storyViewers + (storyId to previous.copy(error = "No se pudieron cargar las vistas"))) }
                handleSessionError(error)
            }
        }
    }

    fun toggleStoryLike(storyId: String) {
        val session = repositories.session.currentSession() ?: return
        val story = state.value.stories.firstOrNull { it.id == storyId } ?: return
        if (story.userId == session.userId || !story.isActive(monotonicMillis()) || storyId in state.value.pendingStoryLikes) return
        storyLikeRevision++
        state.update { it.copy(pendingStoryLikes = it.pendingStoryLikes + storyId,
            stories = it.stories.map { s -> if (s.id == storyId) s.copy(likedByMe = !story.likedByMe) else s }) }
        viewModelScope.launch {
            try {
                repositories.community.setStoryLike(session.accessToken, storyId, session.userId, !story.likedByMe)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state.update { it.copy(stories = it.stories.map { s -> if (s.id == storyId) s.copy(likedByMe = story.likedByMe) else s }) }
                if (!handleSessionError(error)) notify("No se pudo actualizar el like de la historia. Inténtalo de nuevo.")
            } finally {
                storyLikeRevision++
                state.update { it.copy(pendingStoryLikes = it.pendingStoryLikes - storyId) }
            }
        }
    }

    fun editStoryReply(storyId: String, text: String) {
        val reply = state.value.storyReplies[storyId] ?: StoryReplyUiState()
        if (reply.sending || reply.pending != null) return
        state.update { it.copy(storyReplies = it.storyReplies + (storyId to reply.copy(draft = text, feedback = null))) }
    }

    fun sendStoryReply(storyId: String) {
        val session = repositories.session.currentSession() ?: return
        val story = state.value.stories.firstOrNull { it.id == storyId } ?: return
        val reply = state.value.storyReplies[storyId] ?: return
        if (reply.sending || story.userId == session.userId || !story.isActive(monotonicMillis())) return
        val content = try { validatedMessage(reply.draft) } catch (error: IllegalArgumentException) {
            state.update { it.copy(storyReplies = it.storyReplies + (storyId to reply.copy(feedback = error.message))) }
            return
        }
        val pending = reply.pending ?: DirectMessage(UUID.randomUUID().toString(), session.userId, story.userId, content, "")
        state.update { it.copy(storyReplies = it.storyReplies + (storyId to reply.copy(pending = pending, sending = true, feedback = null))) }
        viewModelScope.launch {
            try {
                repositories.community.sendMessage(session.accessToken, pending)
                state.update { it.copy(storyReplies = it.storyReplies + (storyId to StoryReplyUiState(feedback = "Mensaje enviado"))) }
                notify("Mensaje enviado a ${story.username}")
                refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state.update { current ->
                    val latest = current.storyReplies[storyId] ?: reply
                    current.copy(storyReplies = current.storyReplies + (storyId to latest.copy(sending = false,
                        feedback = "No se pudo enviar. Reintenta para enviar el mismo mensaje.")))
                }
                handleSessionError(error)
            }
        }
    }
}
