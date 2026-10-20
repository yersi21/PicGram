package com.example.picgram.presentation

import androidx.lifecycle.viewModelScope
import com.example.picgram.UserProfile
import com.example.picgram.data.AppContainer
import com.example.picgram.data.model.ProfileNames
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SearchUiState(val query: String = "", val results: List<UserProfile> = emptyList(),
    val isSearching: Boolean = false, val hasSearched: Boolean = false, val searchError: String? = null)

class SearchViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val state = MutableStateFlow(SearchUiState())
    val uiState = state.asStateFlow()
    private var searchJob: Job? = null
    private var revision = 0
    fun search(query: String = state.value.query) {
        searchJob?.cancel()
        val currentRevision = ++revision
        val term = ProfileNames.searchTerm(query)
        state.value = SearchUiState(query = query, isSearching = term.isNotBlank())
        if (term.isBlank()) return
        if (term.length > ProfileNames.MAX_LENGTH) {
            state.value = SearchUiState(query = query, hasSearched = true,
                searchError = "La búsqueda admite hasta 80 caracteres.")
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            val session = repositories.session.currentSession() ?: run {
                state.update { it.copy(isSearching = false) }
                navigate(Destination.LOGIN)
                return@launch
            }
            state.update { it.copy(hasSearched = true) }
            try {
                val found = repositories.social.searchUsers(session.accessToken, term)
                if (currentRevision != revision) return@launch
                state.update { it.copy(results = found, isSearching = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (currentRevision != revision) return@launch
                state.update { it.copy(isSearching = false) }
                if (!handleSessionError(error)) state.update {
                    it.copy(searchError = "No se pudo buscar: ${error.message}", isSearching = false)
                }
            }
        }
    }
}
