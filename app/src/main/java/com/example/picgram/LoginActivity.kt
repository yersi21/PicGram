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
import com.example.picgram.ui.theme.PicGramTheme
import com.google.firebase.auth.FirebaseAuth

/**
 * Actividad encargada del Inicio de Sesion de Usuarios (RF-02).
 * Si ya existe una sesion activa, redirige directamente a MainActivity.
 * Valida y autentica las credenciales del usuario con Firebase Authentication.
 */
class LoginActivity : ComponentActivity() {

    // Instancia de Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // Estado de carga observable desde el Composable
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // RF-02: Si ya hay sesion activa, saltar directamente al feed principal
        if (auth.currentUser != null) {
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
                        onLoginClicked = { email, password ->
                            loginUser(email, password)
                        },
                        onNavigateToRegister = {
                            startActivity(Intent(this, RegisterActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    /**
     * Valida los campos y autentica al usuario en Firebase con su correo y contrasena.
     *
     * @param email    Correo electronico ingresado.
     * @param password Contrasena ingresada.
     */
    private fun loginUser(email: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Ingresa un correo electronico valido", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isBlank()) {
            Toast.makeText(this, "Ingresa tu contrasena", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                isLoading = false
                if (task.isSuccessful) {
                    Toast.makeText(this, "Bienvenido de vuelta!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    val errorMsg = when {
                        task.exception?.message?.contains("no user record") == true ||
                        task.exception?.message?.contains("user-not-found") == true ->
                            "No existe una cuenta con ese correo"
                        task.exception?.message?.contains("password is invalid") == true ||
                        task.exception?.message?.contains("wrong-password") == true ->
                            "Contrasena incorrecta"
                        task.exception?.message?.contains("too-many-requests") == true ->
                            "Demasiados intentos fallidos. Intenta mas tarde"
                        else -> "Error al iniciar sesion: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
    }

    /** Navega a MainActivity limpiando el back stack para evitar volver al Login. */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

/**
 * Componente Composable que dibuja la interfaz grafica de la pantalla de Login (RF-02).
 *
 * @param isLoading           Indica si hay una autenticacion en curso.
 * @param onLoginClicked      Callback con (email, password) al presionar Iniciar Sesion.
 * @param onNavigateToRegister Callback para abrir la pantalla de registro.
 */
@Composable
fun LoginScreen(
    isLoading: Boolean,
    onLoginClicked: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PicGram",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Inicia sesion para continuar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electronico") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contrasena") },
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
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onLoginClicked(email.trim(), password) },
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

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.padding(top = 8.dp),
                enabled = !isLoading
            ) {
                Text("No tienes cuenta? Registrate aqui")
            }
        }
    }
}