package com.example.picgram.ui.screens

import com.example.picgram.*
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
import com.example.picgram.ui.theme.*
import com.example.picgram.presentation.*

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onLoginClicked: (email: String, password: String) -> Unit,
    onGoToRegisterClicked: () -> Unit,
    onForgotPasswordClicked: (String) -> Unit
) {
    var email by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .liquidGlass().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PicGramWordmark(
            modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().height(124.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            colors = glassTextFieldColors(),
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
            colors = glassTextFieldColors(),
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

        TextButton(onClick = { onForgotPasswordClicked(email) }, enabled = !isLoading,
            modifier = Modifier.align(Alignment.End)) {
            Text("¿Olvidaste tu contraseña?")
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(
            onClick = { onLoginClicked(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
