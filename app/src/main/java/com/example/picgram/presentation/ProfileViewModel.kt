package com.example.picgram.presentation

import android.net.Uri
import com.example.picgram.*
import com.example.picgram.data.AppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProfileUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isFollowing: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val avatarUrl: String = "",
    val isUploadingAvatar: Boolean = false,
    val isRemovingAvatar: Boolean = false,
    val isAvatarLoading: Boolean = true,
    val isOwnProfile: Boolean = false
)

class ProfileViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val mutableUiState = MutableStateFlow(ProfileUiState())
    val uiState = mutableUiState.asStateFlow()
    private var posts: List<Post>
        get() = uiState.value.posts
        set(value) { mutableUiState.update { it.copy(posts = value) } }
    private var isLoading: Boolean
        get() = uiState.value.isLoading
        set(value) { mutableUiState.update { it.copy(isLoading = value) } }
    private var isFollowing: Boolean
        get() = uiState.value.isFollowing
        set(value) { mutableUiState.update { it.copy(isFollowing = value) } }
    private var isTogglingFollow: Boolean
        get() = uiState.value.isTogglingFollow
        set(value) { mutableUiState.update { it.copy(isTogglingFollow = value) } }
    private var followersCount: Int
        get() = uiState.value.followersCount
        set(value) { mutableUiState.update { it.copy(followersCount = value) } }
    private var followingCount: Int
        get() = uiState.value.followingCount
        set(value) { mutableUiState.update { it.copy(followingCount = value) } }
    private var avatarUrl: String
        get() = uiState.value.avatarUrl
        set(value) { mutableUiState.update { it.copy(avatarUrl = value) } }
    private var isUploadingAvatar: Boolean
        get() = uiState.value.isUploadingAvatar
        set(value) { mutableUiState.update { it.copy(isUploadingAvatar = value) } }
    private var isRemovingAvatar: Boolean
        get() = uiState.value.isRemovingAvatar
        set(value) { mutableUiState.update { it.copy(isRemovingAvatar = value) } }
    private var isAvatarLoading: Boolean
        get() = uiState.value.isAvatarLoading
        set(value) { mutableUiState.update { it.copy(isAvatarLoading = value) } }
    private var isOwnProfile: Boolean
        get() = uiState.value.isOwnProfile
        set(value) { mutableUiState.update { it.copy(isOwnProfile = value) } }
    private var avatarRevision = 0
    private var avatarReloadJob: kotlinx.coroutines.Job? = null

    private var profileUserId: String = ""

    private var initialized = false
    fun initialize(userId: String) {
        if (initialized) return
        initialized = true
        profileUserId = userId
        val session = repositories.session.currentSession() ?: run { navigate(Destination.CLOSE); return }
        isOwnProfile = session.userId == userId
        reloadAvatar(session.accessToken, userId)
        loadProfileData(session.accessToken, session.userId, userId)
    }
    fun refresh() {
        val s = repositories.session.currentSession() ?: return
        if (isLoading) return
        if (isOwnProfile) {
            reloadOwnPosts(s.accessToken)
            reloadAvatar(s.accessToken, profileUserId)   // Recargar avatar al volver
        }
        refreshCounters(s.accessToken, s.userId, profileUserId)
    }
    fun loadProfileData(token: String, currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                val result = run {
                    val p   = repositories.social.getUserPosts(token, targetUserId)
                    val f   = repositories.social.isFollowing(token, currentUserId, targetUserId)
                    val fc  = repositories.social.getFollowersCount(token, targetUserId)
                    val fwc = repositories.social.getFollowingCount(token, targetUserId)
                    Data5(p, f, fc, fwc, "")   // Avatar se carga por separado
                }
                posts          = result.a
                isFollowing    = result.b
                followersCount = result.c
                followingCount = result.d
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                notify("Error al cargar perfil: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun reloadAvatar(token: String, targetUserId: String) {
        if (isUploadingAvatar || isRemovingAvatar) return
        avatarReloadJob?.cancel()
        val revision = ++avatarRevision
        isAvatarLoading = true
        avatarReloadJob = viewModelScope.launch {
            try {
                val url = run {
                    repositories.social.getAvatarUrl(token, targetUserId)
                }
                if (revision == avatarRevision && !isUploadingAvatar && !isRemovingAvatar) avatarUrl = url
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (revision != avatarRevision) return@launch
                if (handleSessionError(e)) return@launch
                // Muestra el error real (ej: "column avatar_url does not exist")
                notify("Avatar: ${e.message}")
            } finally { if (revision == avatarRevision) isAvatarLoading = false }
        }
    }

    fun reloadOwnPosts(token: String) {
        viewModelScope.launch {
            try {
                posts = run {
                    repositories.social.getUserPosts(token, profileUserId)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                notify("Error al actualizar perfil: ${e.message}")
            }
        }
    }

    fun refreshCounters(token: String, currentUserId: String, targetUserId: String) {
        if (targetUserId.isBlank()) return
        viewModelScope.launch {
            try {
                val (following, followers, followingCnt) = run {
                    Triple(
                        repositories.social.isFollowing(token, currentUserId, targetUserId),
                        repositories.social.getFollowersCount(token, targetUserId),
                        repositories.social.getFollowingCount(token, targetUserId)
                    )
                }
                isFollowing    = following
                followersCount = followers
                followingCount = followingCnt
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                notify("Error al actualizar perfil: ${e.message}")
            }
        }
    }

    fun handleAvatarUpload(uri: Uri) {
        if (isUploadingAvatar || isRemovingAvatar || !isOwnProfile) return
        val session = repositories.session.currentSession() ?: return
        if (session.userId != profileUserId) return
        avatarRevision++
        avatarReloadJob?.cancel()
        isAvatarLoading = false
        isUploadingAvatar = true

        viewModelScope.launch {
            try {
                val previousUrl = avatarUrl
                val newUrl = repositories.media.uploadProfilePhoto(uri, session)

                // 3. Guardar URL en la tabla users
                try {
                    run {
                        repositories.social.updateAvatarUrl(session.accessToken, session.userId, newUrl)
                    }
                    avatarUrl = newUrl
                    run {
                        repositories.media.extractPath(previousUrl)?.takeIf { isOwnedAvatarPath(session.userId, it) }?.let { path ->
                            repositories.media.scheduleCleanup(session.userId, path)
                            repositories.media.flushCleanup(session)
                        }
                    }
                    notify("Foto de perfil actualizada")
                } catch (dbError: Exception) {
                    if (dbError is kotlinx.coroutines.CancellationException) throw dbError
                    if (handleSessionError(dbError)) return@launch
                    if (dbError is ApiException && dbError.statusCode in 400..499 && dbError.statusCode != 408) {
                        run {
                            repositories.media.extractPath(newUrl)?.takeIf { isOwnedAvatarPath(session.userId, it) }?.let { path ->
                                repositories.media.scheduleCleanup(session.userId, path)
                                repositories.media.flushCleanup(session)
                            }
                        }
                    }
                    // Storage OK pero BD falló — probablemente falta la columna avatar_url
                    notify("Error BD: ${dbError.message}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                notify("Error al subir foto: ${e.message}")
            } finally {
                isUploadingAvatar = false
            }
        }
    }

    fun handleAvatarDelete() {
        if (!isOwnProfile || isUploadingAvatar || isRemovingAvatar || avatarUrl.isBlank()) return
        val session = repositories.session.currentSession() ?: return
        if (session.userId != profileUserId) return
        avatarRevision++
        avatarReloadJob?.cancel()
        isAvatarLoading = false
        isRemovingAvatar = true
        viewModelScope.launch {
            try {
                // Clear the profile reference first; a failed DB write must not destroy its image.
                run {
                    val currentUrl = repositories.social.getAvatarUrl(session.accessToken, session.userId)
                    repositories.social.updateAvatarUrl(session.accessToken, session.userId, "")
                    // Queue cleanup before returning to the UI, even if the screen closes meanwhile.
                    try {
                        repositories.media.extractPath(currentUrl)?.takeIf { isOwnedAvatarPath(session.userId, it) }?.let { path ->
                            repositories.media.scheduleCleanup(session.userId, path)
                        }
                    } catch (failure: Exception) {
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        android.util.Log.w("PicGram", "No se pudo guardar la limpieza del avatar", failure)
                    }
                }
                avatarUrl = ""
                notify("Foto de perfil eliminada")
                run {
                    try {
                        repositories.media.flushCleanup(session)
                    } catch (failure: Exception) {
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        android.util.Log.w("PicGram", "Limpieza del avatar pendiente", failure)
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (handleSessionError(error)) return@launch
                notify("No se pudo completar la eliminación: ${error.message}")
            } finally {
                isRemovingAvatar = false
                repositories.session.currentSession()
                    ?.takeIf { it.userId == profileUserId }?.let { reloadAvatar(it.accessToken, profileUserId) }
            }
        }
    }

    fun handleFollowToggle(token: String, currentUserId: String, targetUserId: String) {
        if (isLoading || isTogglingFollow || isOwnProfile) return
        isTogglingFollow = true
        val wasFollowing = isFollowing
        isFollowing    = !wasFollowing
        followersCount = if (wasFollowing) (followersCount - 1).coerceAtLeast(0) else followersCount + 1

        viewModelScope.launch {
            try {
                run {
                    if (wasFollowing) repositories.social.unfollowUser(token, currentUserId, targetUserId)
                    else              repositories.social.followUser(token, currentUserId, targetUserId)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (handleSessionError(e)) return@launch
                isFollowing    = wasFollowing
                followersCount = if (wasFollowing) followersCount + 1
                                 else (followersCount - 1).coerceAtLeast(0)
                notify("Error: ${e.message}")
            } finally {
                isTogglingFollow = false
            }
        }
    }
}

private data class Data5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
