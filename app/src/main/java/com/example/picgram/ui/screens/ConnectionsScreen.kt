package com.example.picgram.ui.screens

import com.example.picgram.*
import com.example.picgram.presentation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.picgram.ui.theme.GlassTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(title: String, username: String, users: List<UserProfile>, loading: Boolean,
    error: String?, onBack: () -> Unit, onRetry: () -> Unit, onUser: (UserProfile) -> Unit) {
    Scaffold(topBar = {
        GlassTopAppBar(title = { Column { Text(title); Text(username, style = MaterialTheme.typography.labelMedium) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar") } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                error != null -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error)
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                }
                users.isEmpty() -> Text("No hay ${title.lowercase()} todavía.", Modifier.padding(24.dp))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users, key = { it.id }) { user -> UserResultItem(user) { onUser(user) } }
                }
            }
        }
    }
}
