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
 * Actividad de Registro de Nuevos Usuarios (RF-01).
 * Valida los campos, crea la cuenta en Supabase Auth y guarda el perfil
 * en la tabla public.users de Supabase PostgreSQL.
 */
class RegisterActivity : ComponentActivity() {

    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicGramTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RegisterScreen(
                        isLoading = isLoading,
                        onRegisterClicked = { username, email, password ->
                            handleRegister(username, email, password)
                        },
                        onGoToLoginClicked = { finish() }
                    )
                }
            }
        }
    }

    /**
     * Valida los campos y ejecuta el registro en Supabase Auth + Database.
     * La operacion de red se ejecuta en Dispatchers.IO para no bloquear la UI.
     */
    private fun handleRegister(username: String, email: String, password: String) {
        // Validaciones locales antes de llamar a la API
        if (username.trim().length < 3) {
            Toast.makeText(this, "El nombre de usuario debe tener al menos 3 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            Toast.makeText(this, "Formato de correo invalido", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "La contrasena debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        lifecycleScope.launch {
            try {
                val authResult: AuthResult
                val cleanEmail = email.trim()
                val cleanUsername = username.trim()

                withContext(Dispatchers.IO) {
                    // 1. Crear cuenta en Supabase Auth
                    authResult = SupabaseAuth.signUp(cleanEmail, password)

                    // 2. Guardar perfil en public.users
                    SupabaseDatabase.insertUser(
                        token = authResult.accessToken,
                        id = authResult.userId,
                        email = cleanEmail,
                        username = cleanUsername,
                        createdAt = System.currentTimeMillis()
                    )
                }

                // 3. Guardar sesion localmente y navegar al feed
                SessionManager.saveSession(
                    this@RegisterActivity,
                    UserSession(
                        accessToken = authResult.accessToken,
                        userId = authResult.userId,
                        email = authResult.email,
                        username = cleanUsername
                    )
                )
                navigateToMain()

            } catch (e: Exception) {
                isLoading = false
                val message = when {
                    e.message?.contains("already registered", ignoreCase = true) == true ||
                    e.message?.contains("duplicate", ignoreCase = true) == true ->
                        "Este correo ya esta registrado"
                    e.message?.contains("correo", ignoreCase = true) == true ->
                        e.message ?: "Error en el registro"
                    else -> e.message ?: "Error al registrarse. Intenta de nuevo."
                }
                Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_LONG).show()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    isLoading: Boolean,
    onRegisterClicked: (username: String, email: String, password: String) -> Unit,
    onGoToLoginClicked: () -> Unit
) {
    var username by remember { mutableStateOf("") }
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
            text = "Crear Cuenta",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nombre de usuario") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(12.dp))

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
            label = { Text("Contrasena (min. 6 caracteres)") },
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
            onClick = { onRegisterClicked(username, email, password) },
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
                Text("Registrando...")
            } else {
                Text("Registrarse")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onGoToLoginClicked, enabled = !isLoading) {
            Text("Ya tengo cuenta. Iniciar sesion")
        }
    }
}