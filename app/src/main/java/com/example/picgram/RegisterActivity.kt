package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

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

class RegisterActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { RegisterViewModel(it) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RegisterScreen(
                        isLoading = state.isLoading,
                        onRegisterClicked = { displayName, username, email, password ->
                            model.handleRegister(displayName, username, email, password)
                        },
                        onGoToLoginClicked = { finish() }
                    )
                }
            }
        }
    }
}
