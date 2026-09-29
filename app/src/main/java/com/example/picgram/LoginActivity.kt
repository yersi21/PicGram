package com.example.picgram

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.picgram.ui.theme.PicGramTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Actividad de Inicio de Sesion (RF-02).
 * Si hay sesion guardada en SessionManager, redirige directamente al feed.
 * Autentica con Supabase Auth y guarda el JWT en SessionManager.
 */
class LoginActivity : ComponentActivity() {

    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // RF-02: Auto-login si ya hay sesion activa guardada
        if (SessionManager.currentSession(this) != null) {
            navigateToMain()
            return
        }

        setContent {
            PicGramTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        isLoading = isLoading,
                        onLoginClicked = { email, password -> handleLogin(email, password) },
                        onGoToRegisterClicked = {
                            startActivity(Intent(this, RegisterActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    /**
     * Valida los campos y ejecuta el inicio de sesion con Supabase Auth.
     * Tras autenticarse, obtiene el username del perfil y guarda la sesion.
     */
    private fun handleLogin(email: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            Toast.makeText(this, "Formato de correo invalido", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isEmpty()) {
            Toast.makeText(this, "Ingresa tu contrasena", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        lifecycleScope.launch {
            try {
                val authResult: AuthResult
                val username: String
                val cleanEmail = email.trim()

                withContext(Dispatchers.IO) {
                    // 1. Autenticar con Supabase Auth
                    authResult = SupabaseAuth.signIn(cleanEmail, password)

                    // 2. Obtener username desde public.users.
                    //    Si el perfil no existe (cuenta antes de la migracion),
                    //    usamos la parte del correo antes del '@' como nombre.
                    val fetchedUsername = SupabaseDatabase.getUsername(authResult.accessToken, authResult.userId)
                    username = fetchedUsername.ifBlank {
                        cleanEmail.substringBefore("@")
                    }
                }

                // 3. Persistir sesion y navegar al feed
                SessionManager.saveSession(
                    this@LoginActivity,
                    UserSession(
                        accessToken = authResult.accessToken,
                        userId = authResult.userId,
                        email = authResult.email,
                        username = username
                    )
                )
                navigateToMain()

            } catch (e: Exception) {
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
                Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onLoginClicked: (email: String, password: String) -> Unit,
    onGoToRegisterClicked: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PicGram",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Inicia sesion para continuar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electronico") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contrasena") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Ocultar" else "Ver")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onLoginClicked(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ingresando...")
            } else {
                Text("Iniciar Sesion")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onGoToRegisterClicked, enabled = !isLoading) {
            Text("Crear cuenta nueva")
        }
    }
}