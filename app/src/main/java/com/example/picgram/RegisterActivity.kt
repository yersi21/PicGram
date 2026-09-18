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
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Actividad encargada del Registro de Nuevos Usuarios (RF-01).
 * Valida los datos, crea la cuenta en Firebase Auth, actualiza el displayName
 * y guarda el perfil completo en Cloud Firestore (coleccion "users").
 */
class RegisterActivity : ComponentActivity() {

    // Instancia de Firebase Authentication para gestionar el registro de credenciales
    private lateinit var auth: FirebaseAuth
    // Instancia de Cloud Firestore para almacenar datos adicionales del perfil de usuario
    private lateinit var db: FirebaseFirestore

    // Estado de carga observable desde el Composable
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar servicios de Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setContent {
            PicGramTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RegisterScreen(
                        isLoading = isLoading,
                        onRegisterClicked = { email, password, username ->
                            registerUser(email, password, username)
                        },
                        onNavigateToLogin = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * Valida los campos y registra un nuevo usuario en Firebase Authentication con
     * correo y contrasena; luego actualiza el displayName y guarda el perfil en Firestore.
     *
     * @param email    Correo electronico ingresado por el usuario.
     * @param password Contrasena ingresada por el usuario.
     * @param username Nombre de usuario visible en la red social.
     */
    private fun registerUser(email: String, password: String, username: String) {
        if (username.isBlank()) {
            Toast.makeText(this, "El nombre de usuario no puede estar vacio", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Ingresa un correo electronico valido", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "La contrasena debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser ?: run {
                        isLoading = false
                        return@addOnCompleteListener
                    }
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()
                    firebaseUser.updateProfile(profileUpdates)
                        .addOnCompleteListener {
                            saveUserToFirestore(firebaseUser.uid, email, username)
                        }
                } else {
                    isLoading = false
                    val errorMsg = when {
                        task.exception?.message?.contains("email address is already in use") == true ->
                            "Este correo ya esta registrado"
                        task.exception?.message?.contains("badly formatted") == true ->
                            "Formato de correo invalido"
                        else -> "Error en el registro: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Guarda los datos complementarios del usuario en la coleccion "users" de Cloud Firestore.
     *
     * @param userId   Identificador unico (UID) generado por Firebase Auth.
     * @param email    Correo electronico registrado.
     * @param username Nombre de usuario de la plataforma.
     */
    private fun saveUserToFirestore(userId: String, email: String, username: String) {
        val userMap = hashMapOf(
            "uid" to userId,
            "email" to email,
            "username" to username,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(userId)
            .set(userMap)
            .addOnSuccessListener {
                isLoading = false
                Toast.makeText(this, "Registro exitoso! Bienvenido a PicGram", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                isLoading = false
                Toast.makeText(this, "Error al guardar perfil: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

/**
 * Componente Composable que dibuja la interfaz grafica de la pantalla de Registro (RF-01).
 *
 * @param isLoading         Indica si hay una operacion en curso (deshabilita botones y muestra spinner).
 * @param onRegisterClicked Callback con (email, password, username) al presionar Registrarse.
 * @param onNavigateToLogin Callback para ir a la pantalla de inicio de sesion.
 */
@Composable
fun RegisterScreen(
    isLoading: Boolean,
    onRegisterClicked: (String, String, String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
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
                text = "Crea tu cuenta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nombre de usuario") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                label = { Text("Contrasena (min. 6 caracteres)") },
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
                onClick = { onRegisterClicked(email.trim(), password, username.trim()) },
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
                    Text("Creando cuenta...")
                } else {
                    Text("Registrarse")
                }
            }

            TextButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.padding(top = 8.dp),
                enabled = !isLoading
            ) {
                Text("Ya tienes cuenta? Inicia sesion")
            }
        }
    }
}