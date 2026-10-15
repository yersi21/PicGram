package com.example.picgram.presentation

import androidx.lifecycle.viewModelScope
import com.example.picgram.*
import com.example.picgram.data.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ConnectionsUiState(val users: List<UserProfile> = emptyList(), val loading: Boolean = false,
    val error: String? = null)

class ConnectionsViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val state = MutableStateFlow(ConnectionsUiState())
    val uiState = state.asStateFlow()
    private var profileId = ""
    private var type: ConnectionType? = null
    fun initialize(profileId: String, type: ConnectionType) { this.profileId = profileId; this.type = type }
    fun reload() {
        val selectedType = type ?: return
        if (state.value.loading) return
        val session = repositories.session.currentSession() ?: run { navigate(Destination.LOGIN); return }
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val users = repositories.social.getConnections(session.accessToken, profileId, selectedType)
                state.value = ConnectionsUiState(users = users)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!handleSessionError(error)) state.update { it.copy(loading = false,
                    error = "No se pudo cargar la lista. Comprueba tu conexión e inténtalo de nuevo.") }
            }
        }
    }
}
