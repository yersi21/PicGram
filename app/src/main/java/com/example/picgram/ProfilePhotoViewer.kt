package com.example.picgram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage

@Composable
fun AvatarOptionsDialog(hasPhoto: Boolean, onDismiss: () -> Unit, onChange: () -> Unit,
                        onView: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Foto de perfil") }, text = {
        Column(Modifier.fillMaxWidth()) {
            TextButton(onClick = onChange, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddAPhoto, null)
                Spacer(Modifier.width(12.dp))
                Text("Cambiar foto", modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onView, enabled = hasPhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(12.dp))
                Text("Ver foto", modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onDelete, enabled = hasPhoto, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(12.dp))
                Text("Eliminar foto", modifier = Modifier.weight(1f))
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun ProfilePhotoViewer(url: String, username: String, onClose: () -> Unit) {
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(), contentAlignment = Alignment.Center) {
            key(url, retry) {
                AsyncImage(url, "Foto de perfil de $username", Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    onLoading = { loading = true; failed = false },
                    onSuccess = { loading = false; failed = false },
                    onError = { loading = false; failed = true })
            }
            if (loading) CircularProgressIndicator(color = Color.White)
            if (failed) Column(Modifier.background(Color.Black.copy(alpha = 0.85f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se pudo cargar la foto. Revisa tu conexión.", color = Color.White)
                TextButton(onClick = { loading = true; failed = false; retry++ }) { Text("Reintentar", color = Color.White) }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.60f), CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar foto", tint = Color.White)
            }
        }
    }
}
