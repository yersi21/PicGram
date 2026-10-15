package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.picgram.ui.theme.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class LoginActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { LoginViewModel(it) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (currentUserSession() != null) { model.navigateToMain() }
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        isLoading = state.isLoading,
                        onLoginClicked = { email, password -> model.handleLogin(email, password) },
                        onGoToRegisterClicked = {
                            startActivity(Intent(this, RegisterActivity::class.java))
                        },
                        onForgotPasswordClicked = { email ->
                            startActivity(Intent(this, ForgotPasswordActivity::class.java).putExtra("email", email.trim()))
                        }
                    )
                }
            }
        }
    }
}
