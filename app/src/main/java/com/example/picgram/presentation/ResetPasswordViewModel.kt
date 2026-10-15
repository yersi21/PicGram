package com.example.picgram.presentation

import com.example.picgram.*
import com.example.picgram.data.AppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ResetPasswordUiState(
    val revision: Int = 0,
    val verifying: Boolean = false,
    val updating: Boolean = false,
    val ready: Boolean = false,
    val success: Boolean = false,
    val email: String = "",
    val error: String? = null,
    val initialized: Boolean = false,
    val userId: String = ""
)

class ResetPasswordViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val state = MutableStateFlow(ResetPasswordUiState())
    val uiState = state.asStateFlow()
    var initialized: Boolean
        get() = uiState.value.initialized
        private set(value) { state.update { it.copy(initialized = value) } }
    var revision: Int
        get() = uiState.value.revision
        private set(value) { state.update { it.copy(revision = value) } }
    var verifying: Boolean
        get() = uiState.value.verifying
        private set(value) { state.update { it.copy(verifying = value) } }
    var updating: Boolean
        get() = uiState.value.updating
        private set(value) { state.update { it.copy(updating = value) } }
    var ready: Boolean
        get() = uiState.value.ready
        private set(value) { state.update { it.copy(ready = value) } }
    var success: Boolean
        get() = uiState.value.success
        private set(value) { state.update { it.copy(success = value) } }
    var email: String
        get() = uiState.value.email
        private set(value) { state.update { it.copy(email = value) } }
    var userId: String
        get() = uiState.value.userId
        private set(value) { state.update { it.copy(userId = value) } }
    var error: String?
        get() = uiState.value.error
        private set(value) { state.update { it.copy(error = value) } }
    private var session: AuthResult? = null
    private var verificationJob: Job? = null

    fun acceptLink(raw: String?) {
        if (updating) return
        initialized = true
        verificationJob?.cancel()
        revision++
        session = null
        email = ""
        userId = ""
        ready = false
        success = false
        error = null
        verifying = true
        val linkRevision = revision
        verificationJob = viewModelScope.launch {
            try {
                val link = RecoveryLinks.parse(raw)
                val verified = run { repositories.auth.verifyRecoveryLink(link) }
                session = verified
                email = verified.email
                userId = verified.userId
                ready = true
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                if (revision != linkRevision) return@launch
                error = when {
                    failure is IllegalArgumentException -> failure.message
                    failure is ApiException && failure.statusCode in listOf(400, 401, 403, 410, 422) ->
                        "El enlace caducó o ya fue utilizado. Solicita uno nuevo."
                    else -> "No se pudo verificar el enlace. Revisa tu conexión y vuelve a abrirlo."
                }
            } finally { if (revision == linkRevision) verifying = false }
        }
    }

    fun savePassword(password: String, confirmation: String) {
        if (updating || !ready || success) return
        val active = session ?: return
        newPasswordError(password, confirmation)?.let { error = it; return }
        updating = true
        error = null
        viewModelScope.launch {
            try {
                val token = run { repositories.auth.updateRecoveredPassword(active, password) }
                if (repositories.session.currentSession()?.userId == userId) repositories.session.clearSession()
                success = true
                ready = false
                session = null
                run { repositories.auth.closeRecoverySession(token) }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = when {
                    failure is RecoveryExpiredException || (failure is ApiException && failure.statusCode in listOf(401, 403)) -> {
                        ready = false
                        "La sesión de recuperación caducó. Solicita otro enlace."
                    }
                    failure is ApiException && failure.statusCode == 429 -> "Espera un momento antes de reintentar."
                    failure is ApiException && failure.message.orEmpty().contains("different", ignoreCase = true) ->
                        "Elige una contraseña diferente de la anterior."
                    failure is ApiException && failure.statusCode in listOf(400, 422) ->
                        "La contraseña no cumple los requisitos de seguridad de tu cuenta. Prueba una más larga con letras, números y símbolos."
                    else -> "No se pudo confirmar el cambio. Revisa tu conexión antes de reintentar."
                }
            } finally { updating = false }
        }
    }
    fun goToLogin() { repositories.session.clearSession(); navigate(Destination.LOGIN) }
}
