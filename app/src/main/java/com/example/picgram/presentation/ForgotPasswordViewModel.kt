package com.example.picgram.presentation

import com.example.picgram.*
import com.example.picgram.data.AppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ForgotPasswordUiState(
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
)

class ForgotPasswordViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val state = MutableStateFlow(ForgotPasswordUiState())
    val uiState = state.asStateFlow()
    var sending: Boolean
        get() = uiState.value.sending
        private set(value) { state.update { it.copy(sending = value) } }
    var sent: Boolean
        get() = uiState.value.sent
        private set(value) { state.update { it.copy(sent = value) } }
    var error: String?
        get() = uiState.value.error
        private set(value) { state.update { it.copy(error = value) } }

    fun restoreSent() { sent = true }

    fun sendRecovery(email: String) {
        if (sending || sent) return
        val cleanEmail = email.trim()
        if (!repositories.emailValidator.isValid(cleanEmail)) {
            error = "Ingresa un correo electrónico válido"
            return
        }
        sending = true
        error = null
        viewModelScope.launch {
            try {
                run { repositories.auth.requestPasswordRecovery(cleanEmail) }
                sent = true
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = if (failure is ApiException && failure.statusCode == 429)
                    "Se solicitaron demasiados correos. Espera un momento e inténtalo de nuevo."
                else "No se pudo enviar el enlace. Revisa tu conexión e inténtalo de nuevo."
            } finally { sending = false }
        }
    }

    fun goToLogin() { repositories.session.clearSession(); navigate(Destination.LOGIN) }
}
