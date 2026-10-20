package com.example.picgram.presentation

import com.example.picgram.*
import com.example.picgram.data.AppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LoginUiState(
    val isLoading: Boolean = false
)

class LoginViewModel(repositories: AppContainer) : ScreenViewModel(repositories) {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState = mutableUiState.asStateFlow()
    private var isLoading: Boolean
        get() = uiState.value.isLoading
        set(value) { mutableUiState.update { it.copy(isLoading = value) } }

    fun handleLogin(email: String, password: String) {
        if (isLoading) return
        if (!repositories.emailValidator.isValid(email.trim())) {
            notify("Formato de correo invalido")
            return
        }
        if (password.isEmpty()) {
            notify("Ingresa tu contraseña")
            return
        }

        isLoading = true

        viewModelScope.launch {
            try {
                val authResult: AuthResult
                val username: String
                val cleanEmail = email.trim()

                run {
                    // 1. Autenticar con Supabase Auth
                    authResult = repositories.auth.signIn(cleanEmail, password)

                    // 2. Obtener username desde public.users.
                    //    Si el perfil no existe (cuenta antes de la migracion),
                    //    usamos la parte del correo antes del '@' como nombre.
                    val fetchedUsername = repositories.social.getUsername(authResult.accessToken, authResult.userId)
                    username = fetchedUsername.ifBlank {
                        authResult.username.ifBlank { cleanEmail.substringBefore("@") }
                    }
                    if (fetchedUsername.isBlank()) repositories.social.insertUser(
                        authResult.accessToken, authResult.userId, cleanEmail, username, System.currentTimeMillis(), authResult.displayName)
                }

                // 3. Persistir sesion y navegar al feed
                repositories.session.saveSession(
                    UserSession(
                        accessToken = authResult.accessToken,
                        userId = authResult.userId,
                        email = authResult.email,
                        username = username,
                        refreshToken = authResult.refreshToken,
                        expiresAt = authResult.expiresAt
                    )
                )
                navigateToMain()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                isLoading = false
                val message = when {
                    e.message?.contains("Invalid login credentials", ignoreCase = true) == true ||
                    e.message?.contains("invalid_grant", ignoreCase = true) == true ->
                        "Correo o contrasena incorrectos"
                    e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                        "Confirma tu correo antes de iniciar sesion"
                    e.message?.contains("too many", ignoreCase = true) == true ->
                        "Demasiados intentos. Espera un momento."
                    else -> e.message ?: "Error al iniciar sesion"
                }
                notify(message)
            }
        }
    }

    fun navigateToMain() { navigate(Destination.FEED) }
}
