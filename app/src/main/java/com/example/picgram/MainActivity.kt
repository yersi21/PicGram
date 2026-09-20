package com.example.picgram

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.PicGramTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

/** Modelo de datos para una publicacion recuperada de Firestore. */
data class Post(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val username: String = "",
    val caption: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image",
    val timestamp: Long = 0L
)

/**
 * Actividad Principal — Feed de publicaciones en tiempo real (RF-02 continuacion + RF-10).
 * Verifica sesion activa, muestra el feed de posts de Firestore con actualizacion en tiempo real,
 * permite crear nuevas publicaciones (RF-10) y cerrar sesion (RF-02).
 */
class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Verificar sesion activa (RF-02)
        val currentUser = auth.currentUser
        if (currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            PicGramTheme {
                FeedScreen(
                    userEmail = currentUser.email ?: "Usuario",
                    displayName = currentUser.displayName ?: currentUser.email ?: "Usuario",
                    onCreatePostClicked = {
                        startActivity(Intent(this, CreatePostActivity::class.java))
                    },
                    onLogoutClicked = {
                        auth.signOut()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    db = db
                )
            }
        }
    }
}

/**
 * Pantalla principal que muestra el feed de publicaciones en tiempo real.
 * Usa addSnapshotListener de Firestore para actualizar automaticamente la lista.
 *
 * @param userEmail         Email del usuario autenticado.
 * @param displayName       Nombre para mostrar del usuario.
 * @param onCreatePostClicked Callback para abrir la pantalla de creacion de post.
 * @param onLogoutClicked   Callback para cerrar sesion.
 * @param db                Instancia de FirebaseFirestore para consultar la coleccion "posts".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    userEmail: String,
    displayName: String,
    onCreatePostClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    db: FirebaseFirestore
) {
    // Estado del feed — se actualiza en tiempo real con Firestore snapshot listener
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var isLoadingFeed by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Escuchar cambios en tiempo real de la coleccion "posts" ordenados por timestamp desc
    DisposableEffect(Unit) {
        val listenerRegistration = db.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                isLoadingFeed = false
                if (error != null) {
                    errorMessage = "Error al cargar el feed: ${error.message}"
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    posts = snapshot.documents.mapNotNull { doc ->
                        try {
                            Post(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                userEmail = doc.getString("userEmail") ?: "",
                                username = doc.getString("username") ?: doc.getString("userEmail") ?: "Usuario",
                                caption = doc.getString("caption") ?: "",
                                mediaUrl = doc.getString("mediaUrl") ?: "",
                                mediaType = doc.getString("mediaType") ?: "image",
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        onDispose { listenerRegistration.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PicGram",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onLogoutClicked) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar sesion"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreatePostClicked,
                icon = { Icon(Icons.Default.Add, contentDescription = "Nueva publicacion") },
                text = { Text("Nueva publicacion") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // Indicador de carga inicial
                isLoadingFeed -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                // Mensaje de error
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // Feed vacio
                posts.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No hay publicaciones aun",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Se el primero en publicar algo!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Lista de publicaciones
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                                db = db
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta que representa una publicacion individual en el feed.
 * Muestra: avatar con iniciales, username, timestamp, imagen (si existe) y caption.
 * Si el usuario autenticado es el dueno del post, muestra un boton de eliminar.
 *
 * @param post            Datos de la publicacion a renderizar.
 * @param currentUserId   UID del usuario autenticado.
 * @param db              Instancia de FirebaseFirestore para eliminar el post.
 */
@Composable
fun PostCard(
    post: Post,
    currentUserId: String,
    db: FirebaseFirestore
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isOwner = post.userId == currentUserId

    // Dialogo de confirmacion para eliminar
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar publicacion") },
            text = { Text("¿Estas seguro de que deseas eliminar esta publicacion?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    db.collection("posts").document(post.id).delete()
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Header: avatar + username + timestamp + (boton eliminar si es el dueno)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circular con la inicial del username
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.username,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatTimestamp(post.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Boton eliminar — solo visible para el dueno del post
                if (isOwner) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar publicacion",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Imagen del post (si existe)
            if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
                AsyncImage(
                    model = post.mediaUrl,
                    contentDescription = "Imagen de la publicacion",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // Caption
            if (post.caption.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = post.username,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.caption,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Formatea un timestamp Unix en milisegundos a una cadena legible relativa o absoluta.
 *
 * @param timestamp Marca de tiempo en milisegundos.
 * @return Cadena legible como "hace 5 minutos", "hace 2 horas", etc.
 */
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "ahora mismo"
        diff < 3_600_000L -> "hace ${diff / 60_000} min"
        diff < 86_400_000L -> "hace ${diff / 3_600_000} h"
        else -> {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es"))
            sdf.format(Date(timestamp))
        }
    }
}