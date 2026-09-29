package com.example.picgram

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.PicGramTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Actividad de creacion de publicaciones (RF-10).
 * Sube el archivo multimedia a Supabase Storage y guarda los metadatos
 * en la tabla public.posts de Supabase PostgreSQL.
 *
 * Arquitectura:
 *   - Sesion: SessionManager (Supabase JWT)
 *   - Almacenamiento de archivos: SupabaseStorage (REST)
 *   - Metadatos del post: SupabaseDatabase (REST)
 */
class CreatePostActivity : ComponentActivity() {

    private val BUCKET_NAME = "picgram-posts"

    private var selectedMediaUri by mutableStateOf<Uri?>(null)
    private var mediaType by mutableStateOf("image")
    private var isLoading by mutableStateOf(false)
    private var loadingMessage by mutableStateOf("Publicando...")

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            val mimeType = contentResolver.getType(uri)
            mediaType = if (mimeType?.startsWith("video") == true) "video" else "image"
        } else {
            Toast.makeText(this, "No se selecciono ningun archivo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar sesion activa
        if (SessionManager.currentSession(this) == null) {
            Toast.makeText(this, "Sesion expirada. Inicia sesion de nuevo.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            PicGramTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CreatePostScreen(
                        selectedMediaUri = selectedMediaUri,
                        isLoading = isLoading,
                        loadingMessage = loadingMessage,
                        onSelectMediaClicked = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onSubmitPost = { caption -> startUpload(caption) },
                        onBackClicked = { finish() }
                    )
                }
            }
        }
    }

    /**
     * Obtiene el username desde Supabase y lanza la subida del post.
     */
    private fun startUpload(caption: String) {
        val uri = selectedMediaUri
        if (uri == null && caption.isBlank()) {
            Toast.makeText(this, "Agrega una foto/video o escribe una descripcion", Toast.LENGTH_SHORT).show()
            return
        }

        val session = SessionManager.currentSession(this) ?: run {
            Toast.makeText(this, "Sesion expirada. Inicia sesion de nuevo.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        isLoading = true
        loadingMessage = "Preparando publicacion..."

        lifecycleScope.launch {
            try {
                // 1. Usar username de la sesion (ya se resolvio al iniciar sesion).
                //    Fallback: parte antes del '@' del correo si por algun motivo esta vacio.
                val username = session.username.ifBlank {
                    session.email.substringBefore("@")
                }

                // 2. Subir archivo a Supabase Storage (si hay uno seleccionado)
                val mediaUrl: String
                if (uri != null) {
                    val extension = if (mediaType == "video") "mp4" else "jpg"
                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                    val storagePath = "posts/${session.userId}/${UUID.randomUUID()}.$extension"

                    loadingMessage = "Subiendo a Supabase Storage..."
                    mediaUrl = withContext(Dispatchers.IO) {
                        val bytes = contentResolver.openInputStream(uri)
                            ?.use { it.readBytes() }
                            ?: throw Exception("No se pudo leer el archivo seleccionado")
                        SupabaseStorage.upload(storagePath, bytes, mimeType)
                    }
                } else {
                    mediaUrl = ""
                }

                // 3. Guardar metadatos en Supabase PostgreSQL (fire-and-forget)
                loadingMessage = "Guardando publicacion..."
                val timestamp = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    SupabaseDatabase.insertPost(
                        token = session.accessToken,
                        userId = session.userId,
                        userEmail = session.email,
                        username = username,
                        caption = caption,
                        mediaUrl = mediaUrl,
                        mediaType = mediaType,
                        timestamp = timestamp
                    )
                }

                // 4. Exito
                isLoading = false
                Toast.makeText(this@CreatePostActivity, "Publicacion creada con exito!", Toast.LENGTH_SHORT).show()
                finish()

            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(
                    this@CreatePostActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    selectedMediaUri: Uri?,
    isLoading: Boolean,
    loadingMessage: String,
    onSelectMediaClicked: () -> Unit,
    onSubmitPost: (String) -> Unit,
    onBackClicked: () -> Unit
) {
    var caption by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Publicacion") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, enabled = !isLoading) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
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
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedMediaUri != null) {
                        AsyncImage(
                            model = selectedMediaUri,
                            contentDescription = "Vista previa multimedia",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Ninguna imagen seleccionada",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(onClick = onSelectMediaClicked, enabled = !isLoading) {
                                Text("Seleccionar Foto o Video")
                            }
                        }
                    }
                }

                if (selectedMediaUri != null) {
                    TextButton(
                        onClick = onSelectMediaClicked,
                        modifier = Modifier.padding(top = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("Cambiar archivo multimedia")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Escribe una descripcion...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onSubmitPost(caption) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
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