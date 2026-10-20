package com.example.picgram.presentation

import com.example.picgram.*
import com.example.picgram.data.AppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FeedUiState(
    val posts: List<Post> = emptyList(),
    val isLoadingFeed: Boolean = true,
    val errorMessage: String? = null,
    val currentAvatarUrl: String = ""
)

class FeedViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val mutableUiState = MutableStateFlow(FeedUiState())
    val uiState = mutableUiState.asStateFlow()
    fun setDarkMode(enabled: Boolean) {
        try {
            repositories.theme.setDarkMode(enabled)
        } catch (error: Exception) {
            notify("No se pudo cambiar el tema: ${error.message}")
        }
    }
    private var posts: List<Post>
        get() = uiState.value.posts
        set(value) { mutableUiState.update { it.copy(posts = value) } }
    private var isLoadingFeed: Boolean
        get() = uiState.value.isLoadingFeed
        set(value) { mutableUiState.update { it.copy(isLoadingFeed = value) } }
    private var errorMessage: String?
        get() = uiState.value.errorMessage
        set(value) { mutableUiState.update { it.copy(errorMessage = value) } }
    private var currentAvatarUrl: String
        get() = uiState.value.currentAvatarUrl
        set(value) { mutableUiState.update { it.copy(currentAvatarUrl = value) } }
    private val pendingLikes = mutableSetOf<String>()

    private var avatarJob: kotlinx.coroutines.Job? = null
    private var initialized = false
    fun initialize() {
        if (initialized) return
        initialized = true
        val session = repositories.session.currentSession() ?: run { navigateToLogin(); return }
        viewModelScope.launch {
            try {
                val valid = repositories.auth.validateToken(repositories.session.validAccessToken(session.accessToken))
                if (!valid) forceLogout() else loadPosts()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!handleSessionError(error)) { isLoadingFeed = false; errorMessage = "No se pudo verificar la sesión: " + error.message }
            }
        }
    }
    fun refresh() {
        if (repositories.session.currentSession() == null) { navigateToLogin(); return }
        if (!isLoadingFeed) loadPosts()
        loadCurrentAvatar()
    }
    private var feedJob: Job? = null
    private var isLoggingOut = false
    fun loadCurrentAvatar() {
        val session = repositories.session.currentSession() ?: return
        avatarJob?.cancel()
        avatarJob = viewModelScope.launch {
            try {
                currentAvatarUrl = run {
                    repositories.social.getAvatarUrl(session.accessToken, session.userId)
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (!handleSessionError(error)) android.util.Log.w("PicGram", "No se pudo cargar tu avatar", error)
            }
        }
    }

    fun loadPosts() {
        if (feedJob?.isActive == true || pendingLikes.isNotEmpty()) return
        val session = repositories.session.currentSession() ?: return
        feedJob = viewModelScope.launch {
            isLoadingFeed = true; errorMessage = null
            try {
                val loaded = run {
                    repositories.media.flushCleanup(session)
                    repositories.social.getPosts(session.accessToken, session.userId)
                }
                posts = loaded
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (isAuthError(e)) { forceLogout(); return@launch }
                errorMessage = "Error al cargar el feed: ${e.message}"
            } finally { isLoadingFeed = false }
        }
    }

    fun handleLike(requestedPost: Post, addOnly: Boolean = false) {
        val session = repositories.session.currentSession() ?: return
        val post = posts.firstOrNull { it.id == requestedPost.id } ?: return
        if (addOnly && post.likedByMe) return
        if (isLoadingFeed || !pendingLikes.add(post.id)) return
        posts = posts.map {
            if (it.id == post.id) it.copy(
                likedByMe = !it.likedByMe,
                likesCount = if (it.likedByMe) (it.likesCount - 1).coerceAtLeast(0) else it.likesCount + 1
            ) else it
        }
        viewModelScope.launch {
            try {
                run {
                    repositories.social.toggleLike(session.accessToken, post.id, session.userId, post.likedByMe)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (isAuthError(e)) { forceLogout(); return@launch }
                // Revertir cambio optimista si hubo error
                posts = posts.map {
                    if (it.id == post.id) it.copy(likedByMe = post.likedByMe, likesCount = post.likesCount)
                    else it
                }
                notify("Error al actualizar like")
            } finally { pendingLikes.remove(post.id) }
        }
    }

    fun handleDeletePost(post: Post) {
        val session = repositories.session.currentSession() ?: return
        viewModelScope.launch {
            try {
                run {
                    repositories.media.deletePost(session, post)
                }
                posts = posts.filter { it.id != post.id }
                notify("Publicacion eliminada")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                if (isAuthError(e)) { forceLogout(); return@launch }
                notify("Error al eliminar: ${e.message}")
                loadPosts()
            }
        }
    }

    fun isAuthError(e: Exception): Boolean {
        return e is SessionExpiredException || (e is ApiException && e.statusCode == 401)
    }

    fun forceLogout() {
        repositories.session.clearSession()
        notify("Tu sesion ha expirado. Inicia sesion de nuevo.")
        navigateToLogin()
    }

    fun handleLogout(token: String) {
        if (isLoggingOut) return
        isLoggingOut = true
        val latestToken = repositories.session.currentSession()?.accessToken ?: token
        repositories.session.clearSession()
        viewModelScope.launch {
            run { repositories.auth.signOut(latestToken) }
            navigateToLogin()
        }
    }

    fun navigateToLogin() { navigate(Destination.LOGIN) }
}
