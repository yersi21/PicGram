package com.example.picgram.ui.screens

import com.example.picgram.*
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.*
import com.example.picgram.presentation.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    selectedMediaUri: Uri?,
    isLoading: Boolean,
    loadingMessage: String,
    hasPendingPost: Boolean,
    initialCaption: String,
    previewUrl: String,
    previewType: String,
    onSelectMediaClicked: () -> Unit,
    onSubmitPost: (String) -> Unit,
    onBackClicked: () -> Unit
) {
    var caption by androidx.compose.runtime.saveable.rememberSaveable(initialCaption) { mutableStateOf(initialCaption) }
    BackHandler(enabled = isLoading) { }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text("Nueva Publicacion") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, enabled = !isLoading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (previewUrl.isNotBlank() && previewType == "video") Modifier else Modifier.height(260.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewUrl.isNotBlank()) {
                        if (previewType == "video") {
                            VideoPlayer(previewUrl, Modifier.fillMaxWidth(), playbackEnabled = !isLoading)
                        } else {
                        AsyncImage(
                            model = previewUrl,
                            contentDescription = "Vista previa multimedia",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Selecciona una imagen o un video para publicar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            GlassButton(onClick = onSelectMediaClicked, enabled = !isLoading && !hasPendingPost) {
                                Text("Seleccionar Foto o Video")
                            }
                        }
                    }
                }

                if (selectedMediaUri != null) {
                    TextButton(
                        onClick = onSelectMediaClicked,
                        modifier = Modifier.padding(top = 4.dp),
                        enabled = !isLoading && !hasPendingPost
                    ) {
                        Text("Cambiar archivo multimedia")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    colors = glassTextFieldColors(),
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    enabled = !isLoading && !hasPendingPost
                )

                Spacer(modifier = Modifier.height(24.dp))
                if (hasPendingPost && !isLoading) {
                    Text("La publicacion esta preparada. Reintenta para confirmar que se guardo.")
                }

                GlassButton(
                    onClick = { onSubmitPost(caption) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && (if (hasPendingPost)
                        previewUrl.isNotBlank() && previewType in listOf("image", "video")
                        else selectedMediaUri != null)
                ) {
                    Text("Compartir Publicacion")
                }
            }

            // Overlay de progreso
            if (isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = loadingMessage, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
