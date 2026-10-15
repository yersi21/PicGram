package com.example.picgram.ui.screens

import com.example.picgram.*
import com.example.picgram.presentation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.picgram.ui.theme.*

@Composable
fun ResetPasswordScreen(verifying: Boolean, updating: Boolean, ready: Boolean, success: Boolean,
                        email: String, error: String?, onSave: (String, String) -> Unit,
                        onBack: () -> Unit, onLogin: () -> Unit, onNewLink: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = updating && !success) { }
    LaunchedEffect(success) { if (success) { password = ""; confirmation = "" } }
    Scaffold(topBar = {
        GlassTopAppBar(title = { Text("Nueva contraseña") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !updating || success) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Column(Modifier.fillMaxWidth().liquidGlass().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    verifying -> {
                        CircularProgressIndicator()
                        Text("Verificando enlace...")
                    }
                    success -> {
                        Text("Contraseña actualizada", style = MaterialTheme.typography.headlineSmall)
                        Text("Ya puedes iniciar sesión con tu contraseña nueva.")
                        GlassButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
                    }
                    ready -> {
                        Text("Elige una contraseña nueva", style = MaterialTheme.typography.headlineSmall)
                        if (email.isNotBlank()) Text(email, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(value = password, onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(), enabled = !updating, singleLine = true,
                            colors = glassTextFieldColors(), label = { Text("Nueva contraseña") },
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver") } })
                        OutlinedTextField(value = confirmation, onValueChange = { confirmation = it },
                            modifier = Modifier.fillMaxWidth(), enabled = !updating, singleLine = true,
                            colors = glassTextFieldColors(), label = { Text("Confirmar contraseña") },
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        Text("Mínimo 6 caracteres. Usa una contraseña larga y única.",
                            style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        GlassButton(onClick = { keyboard?.hide(); onSave(password, confirmation) }, enabled = !updating,
                            modifier = Modifier.fillMaxWidth()) {
                            if (updating) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (updating) "Guardando..." else "Guardar contraseña")
                        }
                    }
                    else -> {
                        Text(error ?: "Abre el enlace de recuperación recibido por correo.",
                            color = MaterialTheme.colorScheme.error)
                        GlassButton(onClick = onNewLink, modifier = Modifier.fillMaxWidth()) { Text("Solicitar nuevo enlace") }
                    }
                }
            }
        }
    }
}
