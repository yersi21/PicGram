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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Actividad encargada de la creacion y publicacion de contenido estilo Instagram (RF-10).
 * Permite seleccionar fotos o videos de la galeria mediante el Photo Picker de Android,
 * subirlos a Supabase Storage via API REST y registrar la publicacion en Cloud Firestore.
 *
 * Arquitectura hibrida:
 * - Firebase Auth   â†’ identificacion del usuario autenticado
 * - Firebase Firestore â†’ metadatos del post (username, caption, URL de imagen, timestamp)
 * - Supabase Storage   â†’ almacenamiento del archivo de imagen/video
 */
class CreatePostActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // URI del archivo multimedia seleccionado de la galeria
    private var selectedMediaUri by mutableStateOf<Uri?>(null)
    // Tipo de medio detectado por MIME: "image" o "video"
    private var mediaType by mutableStateOf("image")
    // Indica si hay una operacion de subida en progreso
    private var isLoading by mutableStateOf(false)
    // Mensaje descriptivo del paso actual de la subida
    private var loadingMessage by mutableStateOf("Publicando...")

    // Contrato de Activity Result para seleccionar fotos y videos (Android Photo Picker)
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

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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
                        onSubmitPost = { caption -> fetchUsernameAndUpload(caption) },
                        onBackClicked = { finish() }
                    )
                }
            }
        }
    }

    /**
     * Recupera el username del usuario desde Firestore y luego orquesta la subida del post.
     * Si no existe el documento de usuario, usa el displayName o email como fallback.
     *
     * @param caption Texto descriptivo ingresado por el usuario para el post.
     */
    private fun fetchUsernameAndUpload(caption: String) {
        val uri = selectedMediaUri
        if (uri == null && caption.isBlank()) {
            Toast.makeText(this, "Agrega una foto/video o escribe una descripcion", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        loadingMessage = "Preparando publicacion..."

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val username = document.getString("username")
                    ?: currentUser.displayName
                    ?: currentUser.email
                    ?: "Usuario"
                uploadAndSavePost(caption, currentUser.uid, currentUser.email ?: "", username)
            }
            .addOnFailureListener {
                val username = currentUser.displayName ?: currentUser.email ?: "Usuario"
                uploadAndSavePost(caption, currentUser.uid, currentUser.email ?: "", username)
            }
    }

    /**
     * Sube el archivo a Supabase Storage via REST (en Dispatchers.IO) y guarda el post en Firestore.
     * Usa lifecycleScope para respetar el ciclo de vida de la Activity.
     *
     * @param caption   Descripcion del post.
     * @param userId    UID del usuario autor.
     * @param userEmail Correo del autor.
     * @param username  Nombre de usuario del autor.
     */
    private fun uploadAndSavePost(
        caption: String, userId: String, userEmail: String, username: String
    ) {
        val uri = selectedMediaUri

        lifecycleScope.launch {
            try {
                // â”€â”€ 1. Subir archivo a Supabase (si hay uno seleccionado) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                val mediaUrl: String

                if (uri != null) {
                    val extension = if (mediaType == "video") "mp4" else "jpg"
                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                    val storagePath = "posts/$userId/${UUID.randomUUID()}.$extension"

                    loadingMessage = "Subiendo a Supabase Storage..."

                    mediaUrl = withContext(Dispatchers.IO) {
                        val bytes = contentResolver.openInputStream(uri)
                            ?.use { it.readBytes() }
                            ?: throw Exception("No se pudo leer el archivo seleccionado")

                        SupabaseStorage.upload(storagePath, bytes, mimeType)
                    }
                } else {
                    mediaUrl = ""  // Publicacion solo de texto
                }

                // ── 2. Guardar en Firestore: fire-and-forget ──────────────────────
                // NO usamos await() porque espera confirmacion del SERVIDOR y se cuelga.
                // Firestore tiene persistencia offline: escribe al cache local de inmediato
                // y sincroniza al servidor en background automaticamente.
                // El snapshotListener de MainActivity detectara el cambio desde el cache local.
                loadingMessage = "Guardando publicacion..."

                val postMap = hashMapOf(
                    "userId" to userId,
                    "userEmail" to userEmail,
                    "username" to username,
                    "caption" to caption,
                    "mediaUrl" to mediaUrl,
                    "mediaType" to mediaType,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("posts").add(postMap)
                    .addOnFailureListener { e ->
                        // Si falla el servidor loggeamos el error (raro con persistencia offline)
                        android.util.Log.e("CreatePost", "Firestore sync error: ${e.message}")
                    }

                // ── 3. Exito — cerrar Activity de inmediato (UI nunca se bloquea) ─
                isLoading = false
                Toast.makeText(
                    this@CreatePostActivity,
                    "Publicacion creada con exito!",
                    Toast.LENGTH_SHORT
                ).show()
                finish()

            } catch (e: Exception) {
                // Captura errores de Supabase Storage Y de Firestore
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

/**
 * Componente Composable que dibuja la interfaz de creacion de publicaciones (RF-10).
 */
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
                // Vista previa del archivo seleccionado o placeholder para seleccionar
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

            // Overlay de progreso durante la subida a Supabase y guardado en Firestore
            if (isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = loadingMessage,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}