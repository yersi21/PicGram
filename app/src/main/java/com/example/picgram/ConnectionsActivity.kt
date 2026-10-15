package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.picgram.ui.theme.PicGramTheme

fun Activity.openConnections(userId: String, username: String, type: ConnectionType) {
    if (userId.isBlank()) return
    startActivity(Intent(this, ConnectionsActivity::class.java).apply {
        putExtra("user_id", userId)
        putExtra("username", username)
        putExtra("connection_type", type.name)
    })
}

class ConnectionsActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { ConnectionsViewModel(it) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val profileId = intent.getStringExtra("user_id").orEmpty()
        val type = ConnectionType.fromExtra(intent.getStringExtra("connection_type"))
        if (profileId.isBlank() || type == null) { finish(); return }
        model.initialize(profileId, type)
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                ConnectionsScreen(type.title, intent.getStringExtra("username").orEmpty(), state.users, state.loading, state.error,
                    onBack = { finish() }, onRetry = model::reload, onUser = { openUserProfile(it.id, it.username) })
            }
        }
    }
    override fun onResume() { super.onResume(); model.reload() }
}
