package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.picgram.ui.theme.*

class ForgotPasswordActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { ForgotPasswordViewModel(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState?.getBoolean("sent") == true) model.restoreSent()
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                ForgotPasswordScreen(initialEmail = intent.getStringExtra("email").orEmpty(),
                    sending = state.sending, sent = state.sent, error = state.error,
                    onSend = model::sendRecovery, onBack = { finish() }, onLogin = model::goToLogin)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("sent", model.sent)
        super.onSaveInstanceState(outState)
    }
}
