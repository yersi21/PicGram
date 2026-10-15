package com.example.picgram.presentation

import android.net.Uri
import com.example.picgram.*
import com.example.picgram.data.AppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class CreatePostUiState(
    val selectedMediaUri: Uri? = null,
    val mediaType: String = "image",
    val isLoading: Boolean = false,
    val loadingMessage: String = "Publicando...",
    val pendingPost: Post? = null,
    val restoredCaption: String = ""
)

class CreatePostViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val mutableUiState = MutableStateFlow(CreatePostUiState())
    val uiState = mutableUiState.asStateFlow()
    private var selectedMediaUri: Uri?
        get() = uiState.value.selectedMediaUri
        set(value) { mutableUiState.update { it.copy(selectedMediaUri = value) } }
    private var mediaType: String
        get() = uiState.value.mediaType
        set(value) { mutableUiState.update { it.copy(mediaType = value) } }
    private var isLoading: Boolean
        get() = uiState.value.isLoading
        set(value) { mutableUiState.update { it.copy(isLoading = value) } }
    private var loadingMessage: String
        get() = uiState.value.loadingMessage
        set(value) { mutableUiState.update { it.copy(loadingMessage = value) } }
    private var pendingPost: Post?
        get() = uiState.value.pendingPost
        set(value) { mutableUiState.update { it.copy(pendingPost = value) } }
    private var restoredCaption: String
        get() = uiState.value.restoredCaption
        set(value) { mutableUiState.update { it.copy(restoredCaption = value) } }

    private var initialized = false
    fun initialize(uri: Uri?, type: String, restoredPost: Post?) {
        if (initialized) return
        initialized = true
        selectedMediaUri = uri
        mediaType = type
        val session = repositories.session.currentSession() ?: run { navigate(Destination.CLOSE); return }
        isLoading = true
        loadingMessage = "Recuperando borrador..."
        viewModelScope.launch {
            try {
                pendingPost = restoredPost?.takeIf { it.userId == session.userId } ?: repositories.drafts.load(session.userId)
                restoredCaption = pendingPost?.caption.orEmpty()
                if (pendingPost?.let { it.mediaUrl.isBlank() || it.mediaType !in listOf("image", "video") } == true) pendingPost = null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                notify("No se pudo recuperar el borrador: ${error.message}")
            } finally { isLoading = false }
        }
    }
    fun selectMedia(uri: Uri?) {
        if (isLoading || pendingPost != null) return
        if (uri == null) { notify("No se seleccionó ningún archivo"); return }
        selectedMediaUri = uri
        mediaType = if (repositories.media.mimeType(uri)?.startsWith("video") == true) "video" else "image"
    }
    fun startUpload(caption: String) {
        if (isLoading) return
        val uri = selectedMediaUri
        if (pendingPost == null && uri == null) {
            notify("Selecciona una imagen o un video para publicar")
            return
        }

        val session = repositories.session.currentSession() ?: run {
            notify("Sesion expirada. Inicia sesion de nuevo.")
            navigate(Destination.CLOSE)
            return
        }

        isLoading = true
        loadingMessage = "Preparando publicacion..."

        viewModelScope.launch {
            try {
                // 1. Usar username de la sesion (ya se resolvio al iniciar sesion).
                //    Fallback: parte antes del '@' del correo si por algun motivo esta vacio.
                val username = session.username.ifBlank {
                    session.email.substringBefore("@")
                }

                // 2. Toda publicacion requiere una imagen o un video.
                if (pendingPost == null) {
                    loadingMessage = "Subiendo archivo..."
                    val uploaded = repositories.media.uploadPostMedia(requireNotNull(uri), session)
                    pendingPost = Post(id = UUID.randomUUID().toString(), userId = session.userId, caption = caption.trim(),
                        mediaUrl = uploaded.first, mediaType = uploaded.second, timestamp = System.currentTimeMillis())
                }
                val draft = requireNotNull(pendingPost)

                // A retry uses the same UUID and file, even if the previous response was lost.
                loadingMessage = "Guardando publicacion..."
                run {
                    repositories.drafts.save(draft)
                    repositories.social.insertPost(
                        token = session.accessToken,
                        userId = session.userId,
                        userEmail = session.email,
                        username = username,
                        caption = draft.caption,
                        mediaUrl = draft.mediaUrl,
                        mediaType = draft.mediaType,
                        timestamp = draft.timestamp,
                        id = draft.id
                    )
                    repositories.drafts.remove(session.userId)
                }
                pendingPost = null

                // 4. Exito
                isLoading = false
                notify("Publicacion creada con exito!")
                navigate(Destination.CLOSE)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                isLoading = false
                if (handleSessionError(e)) return@launch
                notify("Error: ${e.message}")
            }
        }
    }
}
