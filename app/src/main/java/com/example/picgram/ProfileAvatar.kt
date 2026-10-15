package com.example.picgram

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** A consistent round avatar, with an initial while loading or if the photo is unavailable. */
@Composable
fun ProfileAvatar(username: String, avatarUrl: String, modifier: Modifier = Modifier.size(40.dp),
                  onClick: (() -> Unit)? = null) {
    var loaded by remember(avatarUrl) { mutableStateOf(false) }
    var failed by remember(avatarUrl) { mutableStateOf(false) }
    val clickModifier = if (onClick != null) Modifier.clickable(
        role = Role.Button, onClickLabel = "Ver perfil de $username", onClick = onClick
    ) else Modifier
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).then(clickModifier),
        contentAlignment = Alignment.Center) {
        if (!loaded) Text(
            username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
        if (avatarUrl.isNotBlank() && !failed) AsyncImage(
            model = avatarUrl,
            contentDescription = "Foto de perfil de $username",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            onError = { loaded = false; failed = true }
        )
    }
}
