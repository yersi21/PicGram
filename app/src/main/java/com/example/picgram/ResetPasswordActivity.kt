package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.picgram.ui.theme.*

class ResetPasswordActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { ResetPasswordViewModel(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!model.initialized) model.acceptLink(intent.data?.toString())
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                key(state.revision) {
                    ResetPasswordScreen(verifying = state.verifying, updating = state.updating,
                        ready = state.ready, success = state.success, email = state.email,
                        error = state.error, onSave = model::savePassword,
                        onBack = { finish() }, onLogin = ::goToLogin,
                        onNewLink = {
                            startActivity(Intent(this, ForgotPasswordActivity::class.java).putExtra("email", state.email))
                            finish()
                        })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (model.updating) {
            Toast.makeText(this, "Espera a que termine el cambio de contraseña", Toast.LENGTH_SHORT).show()
            return
        }
        setIntent(intent)
        model.acceptLink(intent.data?.toString())
    }

    private fun goToLogin() { model.goToLogin() }

}
