package com.example.picgram.presentation

import com.example.picgram.*
import com.example.picgram.data.AppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class PostDetailUiState(
    val likesCount: Int = 0,
    val likedByMe: Boolean = false,
    val comments: List<Comment> = emptyList(),
    val isLoadingDetail: Boolean = true,
    val commentText: String = "",
    val isSubmittingComment: Boolean = false,
    val isDeleting: Boolean = false,
    val isLiking: Boolean = false,
    val pendingComment: Comment? = null,
    val authorAvatarUrl: String = "",
    val currentAvatarUrl: String = "",
    val scrollToBottom: Int = 0
)

class PostDetailViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val mutableUiState = MutableStateFlow(PostDetailUiState())
    val uiState = mutableUiState.asStateFlow()
    private var likesCount: Int
        get() = uiState.value.likesCount
        set(value) { mutableUiState.update { it.copy(likesCount = value) } }
    private var likedByMe: Boolean
        get() = uiState.value.likedByMe
        set(value) { mutableUiState.update { it.copy(likedByMe = value) } }
    private var comments: List<Comment>
        get() = uiState.value.comments
        set(value) { mutableUiState.update { it.copy(comments = value) } }
    private var isLoadingDetail: Boolean
        get() = uiState.value.isLoadingDetail
        set(value) { mutableUiState.update { it.copy(isLoadingDetail = value) } }
    private var commentText: String
        get() = uiState.value.commentText
        set(value) { mutableUiState.update { it.copy(commentText = value) } }
    private var isSubmittingComment: Boolean
        get() = uiState.value.isSubmittingComment
        set(value) { mutableUiState.update { it.copy(isSubmittingComment = value) } }
    private var isDeleting: Boolean
        get() = uiState.value.isDeleting
        set(value) { mutableUiState.update { it.copy(isDeleting = value) } }
    private var isLiking: Boolean
        get() = uiState.value.isLiking
        set(value) { mutableUiState.update { it.copy(isLiking = value) } }
    private var pendingComment: Comment?
        get() = uiState.value.pendingComment
        set(value) { mutableUiState.update { it.copy(pendingComment = value) } }
    private var authorAvatarUrl: String
        get() = uiState.value.authorAvatarUrl
        set(value) { mutableUiState.update { it.copy(authorAvatarUrl = value) } }
    private var currentAvatarUrl: String
        get() = uiState.value.currentAvatarUrl
        set(value) { mutableUiState.update { it.copy(currentAvatarUrl = value) } }
    private var scrollToBottom: Int
        get() = uiState.value.scrollToBottom
        set(value) { mutableUiState.update { it.copy(scrollToBottom = value) } }
    private var avatarRefreshJob: kotlinx.coroutines.Job? = null
    /** Dispara scroll al final cuando se añade un comentario nuevo */
    private var initialized = false
    private var authorId = ""
    fun initialize(post: Post, restoredText: String, restoredComment: Comment?) {
        if (initialized) return
        initialized = true
        authorId = post.userId
        authorAvatarUrl = post.avatarUrl
        commentText = restoredText
        val session = repositories.session.currentSession() ?: run { navigate(Destination.CLOSE); return }
        pendingComment = restoredComment?.takeIf { it.userId == session.userId && it.postId == post.id }
        loadPostDetail(session.accessToken, post.id, session.userId, post.userId)
    }
    fun editComment(value: String) { if (!isSubmittingComment && pendingComment == null) commentText = value }
    fun refreshAvatars() {
        if (isLoadingDetail) return
        val session = repositories.session.currentSession() ?: return
        val userIds = comments.map { it.userId } + listOf(authorId, session.userId)
        avatarRefreshJob?.cancel()
        avatarRefreshJob = viewModelScope.launch {
            try {
                val avatars = run { repositories.social.getAvatarUrls(session.accessToken, userIds) }
                avatars[authorId]?.let { authorAvatarUrl = it }
                avatars[session.userId]?.let { currentAvatarUrl = it }
                comments = comments.map { comment ->
                    avatars[comment.userId]?.let { comment.copy(avatarUrl = it) } ?: comment
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                handleSessionError(failure)
            }
        }
    }
    fun loadPostDetail(token: String, postId: String, userId: String, authorId: String) {
        viewModelScope.launch {
            isLoadingDetail = true
            try {
                val likesResult: Pair<Int, Boolean>
                val loadedComments: List<Comment>
                val avatars: Map<String, String>
                run {
                    likesResult = repositories.social.getLikesForPost(token, postId, userId)
                    loadedComments = repositories.social.getComments(token, postId)
                    avatars = repositories.social.getAvatarUrls(token, listOf(userId, authorId))
                }
                likesCount = likesResult.first
                likedByMe  = likesResult.second
                comments   = loadedComments
                authorAvatarUrl = avatars[authorId].orEmpty()
                currentAvatarUrl = avatars[userId].orEmpty()
                if (pendingComment?.id?.let { id -> loadedComments.any { it.id == id } } == true) {
                    pendingComment = null
                    commentText = ""
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                notify("Error al cargar detalles: ${e.message}")
            } finally {
                isLoadingDetail = false
            }
        }
    }

    fun handleLike(token: String, postId: String, userId: String, addOnly: Boolean = false) {
        if (addOnly && likedByMe) return
        if (isLoadingDetail || isLiking || isDeleting) return
        isLiking = true
        val wasLiked = likedByMe
        // Actualización optimista — respuesta instantánea en la UI
        likedByMe  = !wasLiked
        likesCount = if (wasLiked) (likesCount - 1).coerceAtLeast(0) else likesCount + 1
        viewModelScope.launch {
            try {
                run {
                    repositories.social.toggleLike(token, postId, userId, wasLiked)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                // Revertir si la operación remota falló
                likedByMe  = wasLiked
                likesCount = if (wasLiked) likesCount + 1 else (likesCount - 1).coerceAtLeast(0)
                notify("Error al actualizar like")
            } finally { isLiking = false }
        }
    }

    fun handleAddComment(session: UserSession, postId: String) {
        if (isSubmittingComment || isLoadingDetail || isDeleting) return
        val text = commentText.trim()
        if (text.isBlank()) return
        if (pendingComment == null) pendingComment = Comment(id = java.util.UUID.randomUUID().toString(),
            postId = postId, userId = session.userId, username = session.username.ifBlank { session.email },
            content = text, timestamp = System.currentTimeMillis())
        val draft = requireNotNull(pendingComment)
        isSubmittingComment = true
        viewModelScope.launch {
            try {
                val newComment = run {
                    repositories.social.addComment(
                        token     = session.accessToken,
                        postId    = postId,
                        userId    = session.userId,
                        username  = draft.username,
                        content   = draft.content,
                        timestamp = draft.timestamp,
                        id = draft.id
                    )
                }
                comments = comments.filter { it.id != newComment.id } + newComment.copy(avatarUrl = currentAvatarUrl)
                pendingComment = null
                commentText = ""
                // Disparar auto-scroll al último comentario
                scrollToBottom++
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                notify("Error al comentar: ${e.message}")
                if (e is ApiException && e.statusCode in 400..499 && e.statusCode != 408) pendingComment = null
            } finally {
                isSubmittingComment = false
            }
        }
    }

    fun handleDeleteComment(token: String, comment: Comment) {
        viewModelScope.launch {
            try {
                run { repositories.social.deleteComment(token, comment.id) }
                comments = comments.filter { it.id != comment.id }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                notify("Error al borrar comentario: ${e.message}")
            }
        }
    }

    fun handleDeletePost(session: UserSession, post: Post) {
        if (isDeleting) return
        isDeleting = true
        viewModelScope.launch {
            try {
                run {
                    repositories.media.deletePost(session, post)
                }
                notify("Publicacion eliminada")
                navigate(Destination.CLOSE)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                isDeleting = false
                notify("Error al eliminar: ${e.message}")
            }
        }
    }
}
