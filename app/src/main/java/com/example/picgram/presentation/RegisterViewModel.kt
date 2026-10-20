package com.example.picgram.presentation

import com.example.picgram.*
import com.example.picgram.data.AppContainer
import com.example.picgram.data.model.ProfileNames
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RegisterUiState(
    val isLoading: Boolean = false
)

class RegisterViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val mutableUiState = MutableStateFlow(RegisterUiState())
    val uiState = mutableUiState.asStateFlow()
    private var isLoading: Boolean
        get() = uiState.value.isLoading
        set(value) { mutableUiState.update { it.copy(isLoading = value) } }

    fun handleRegister(displayName: String, username: String, email: String, password: String) {
        if (isLoading) return
        val cleanName = try { ProfileNames.validatedName(displayName) } catch (error: IllegalArgumentException) {
            notify(error.message ?: "Ingresa tu nombre")
            return
        }
        // Validaciones locales antes de llamar a la API
        if (username.trim().length < 3) {
            notify("El nombre de usuario debe tener al menos 3 caracteres")
            return
        }
        if (!repositories.emailValidator.isValid(email.trim())) {
            notify("Formato de correo invalido")
            return
        }
        if (password.length < 6) {
            notify("La contrasena debe tener al menos 6 caracteres")
            return
        }

        isLoading = true

        viewModelScope.launch {
            var accountCreated = false
            try {
                val authResult: AuthResult
                val cleanEmail = email.trim()
                val cleanUsername = username.trim()

                run {
                    // 1. Crear cuenta en Supabase Auth
                    authResult = repositories.auth.signUp(cleanEmail, password, cleanUsername, cleanName)
                    accountCreated = true

                    // 2. Guardar perfil en public.users
                    repositories.social.insertUser(
                        token = authResult.accessToken,
                        id = authResult.userId,
                        email = cleanEmail,
                        username = cleanUsername,
                        createdAt = System.currentTimeMillis(),
                        displayName = cleanName
                    )
                }

                // 3. Guardar sesion localmente y navegar al feed
                repositories.session.saveSession(
                    UserSession(
                        accessToken = authResult.accessToken,
                        userId = authResult.userId,
                        email = authResult.email,
                        username = cleanUsername,
                        refreshToken = authResult.refreshToken,
                        expiresAt = authResult.expiresAt
                    )
                )
                navigateToMain()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                isLoading = false
                val message = when {
                    accountCreated -> "Tu cuenta se creo, pero el perfil no pudo guardarse. Inicia sesion para reintentar."
                    e.message?.contains("already registered", ignoreCase = true) == true ||
                    e.message?.contains("duplicate", ignoreCase = true) == true ->
                        "Este correo ya esta registrado"
                    e.message?.contains("correo", ignoreCase = true) == true ->
                        e.message ?: "Error en el registro"
                    else -> e.message ?: "Error al registrarse. Intenta de nuevo."
                }
                notify(message)
            }
        }
    }

    fun navigateToMain() { navigate(Destination.FEED) }
}
