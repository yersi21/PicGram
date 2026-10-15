package com.example.picgram.ui.screens

import com.example.picgram.*
import com.example.picgram.presentation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.picgram.ui.theme.*

@Composable
fun ForgotPasswordScreen(initialEmail: String, sending: Boolean, sent: Boolean, error: String?,
                         onSend: (String) -> Unit, onBack: () -> Unit, onLogin: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    Scaffold(topBar = {
        GlassTopAppBar(title = { Text("Recuperar contraseña") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .imePadding().verticalScroll(rememberScrollState())
            .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().liquidGlass().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (sent) "Revisa tu correo" else "¿Olvidaste tu contraseña?",
                    style = MaterialTheme.typography.headlineSmall)
                Text(if (sent)
                    "Si existe una cuenta con ese correo, recibirás un enlace para elegir una contraseña nueva. Revisa también la carpeta de spam."
                else "Ingresa el correo de tu cuenta. Te enviaremos un enlace para restablecer tu contraseña.")
                if (!sent) {
                    OutlinedTextField(value = email, onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(), enabled = !sending, singleLine = true,
                        colors = glassTextFieldColors(), label = { Text("Correo electrónico") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    GlassButton(onClick = { onSend(email) }, enabled = !sending,
                        modifier = Modifier.fillMaxWidth()) {
                        if (sending) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (sending) "Enviando..." else "Enviar enlace")
                    }
                } else GlassButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Text("Volver al inicio de sesión")
                }
            }
        }
    }
}
