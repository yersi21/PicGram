package com.example.picgram.presentation

import androidx.lifecycle.viewModelScope
import com.example.picgram.UserProfile
import com.example.picgram.data.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SearchUiState(val query: String = "", val results: List<UserProfile> = emptyList(),
    val isSearching: Boolean = false, val hasSearched: Boolean = false, val searchError: String? = null)

class SearchViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val state = MutableStateFlow(SearchUiState())
    val uiState = state.asStateFlow()
    private var searchJob: Job? = null
    fun search(query: String = state.value.query) {
        searchJob?.cancel()
        state.update { it.copy(query = query, searchError = null, isSearching = query.isNotBlank()) }
        if (query.isBlank()) { state.value = SearchUiState(query = query); return }
        searchJob = viewModelScope.launch {
            delay(400)
            val session = repositories.session.currentSession() ?: run { navigate(Destination.LOGIN); return@launch }
            state.update { it.copy(hasSearched = true) }
            try {
                val found = repositories.social.searchUsers(session.accessToken, query)
                state.update { it.copy(results = found, isSearching = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!handleSessionError(error)) state.update {
                    it.copy(searchError = "No se pudo buscar: ${error.message}", isSearching = false)
                }
            }
        }
    }
}
