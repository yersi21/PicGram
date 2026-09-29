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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.PicGramTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private var posts by mutableStateOf<List<Post>>(emptyList())
    private var isLoadingFeed by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager.currentSession(this)
        if (session == null) { navigateToLogin(); return }

        enableEdgeToEdge()
        setContent {
            PicGramTheme {
                FeedScreen(
                    posts = posts,
                    isLoadingFeed = isLoadingFeed,
                    errorMessage = errorMessage,
                    currentUserId = session.userId,
                    onCreatePostClicked = { startActivity(Intent(this, CreatePostActivity::class.java)) },
                    onLogoutClicked = { handleLogout(session.accessToken) },
                    onRefreshClicked = { loadPosts() },
                    onDeletePost = { post -> handleDeletePost(post) },
                    onLikePost = { post -> handleLike(post) },
                    onSearchClicked = { startActivity(Intent(this, SearchActivity::class.java)) },
                    onOpenPost = { post ->
                        val intent = Intent(this, PostDetailActivity::class.java).apply {
                            putExtra("post_id",        post.id)
                            putExtra("post_user_id",   post.userId)
                            putExtra("post_username",  post.username)
                            putExtra("post_caption",   post.caption)
                            putExtra("post_media_url", post.mediaUrl)
                            putExtra("post_media_type",post.mediaType)
                            putExtra("post_timestamp", post.timestamp)
                        }
                        startActivity(intent)
                    }
                )
            }
        }

        // Validar token antes de cargar el feed
        lifecycleScope.launch {
            val valid = withContext(Dispatchers.IO) {
                SupabaseAuth.validateToken(session.accessToken)
            }
            if (!valid) {
                forceLogout()
            } else {
                loadPosts()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.currentSession(this) == null) {
            navigateToLogin()
            return
        }
        if (!isLoadingFeed) loadPosts()
    }

    // ─── Carga el feed de publicaciones ──────────────────────────────────────

    private fun loadPosts() {
        val session = SessionManager.currentSession(this) ?: return
        lifecycleScope.launch {
            isLoadingFeed = true; errorMessage = null
            try {
                val loaded = withContext(Dispatchers.IO) {
                    SupabaseDatabase.getPosts(session.accessToken, session.userId)
                }
                posts = loaded
            } catch (e: Exception) {
                if (isAuthError(e)) { forceLogout(); return@launch }
                errorMessage = "Error al cargar el feed: ${e.message}"
            } finally { isLoadingFeed = false }
        }
    }

    // ─── Like optimista ───────────────────────────────────────────────────────

    private fun handleLike(post: Post) {
        val session = SessionManager.currentSession(this) ?: return
        posts = posts.map {
            if (it.id == post.id) it.copy(
                likedByMe = !it.likedByMe,
                likesCount = if (it.likedByMe) (it.likesCount - 1).coerceAtLeast(0) else it.likesCount + 1
            ) else it
        }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseDatabase.toggleLike(session.accessToken, post.id, session.userId, post.likedByMe)
                }
            } catch (e: Exception) {
                if (isAuthError(e)) { forceLogout(); return@launch }
                // Revertir cambio optimista si hubo error
                posts = posts.map {
                    if (it.id == post.id) it.copy(likedByMe = post.likedByMe, likesCount = post.likesCount)
                    else it
                }
                Toast.makeText(this@MainActivity, "Error al actualizar like", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Borrar publicacion ───────────────────────────────────────────────────

    private fun handleDeletePost(post: Post) {
        val session = SessionManager.currentSession(this) ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (post.mediaUrl.isNotBlank()) {
                        val path = SupabaseStorage.extractPath(post.mediaUrl)
                        if (path != null) SupabaseStorage.delete(path, session.accessToken)
                    }
                    SupabaseDatabase.deletePost(session.accessToken, post.id)
                }
                posts = posts.filter { it.id != post.id }
                Toast.makeText(this@MainActivity, "Publicacion eliminada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (isAuthError(e)) { forceLogout(); return@launch }
                Toast.makeText(this@MainActivity, "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
                loadPosts()
            }
        }
    }

    // ─── Helpers de sesion ────────────────────────────────────────────────────

    /**
     * Determina si una excepcion es causada por un token invalido o usuario eliminado.
     * Supabase retorna HTTP 401 en ambos casos.
     */
    private fun isAuthError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("401") || msg.contains("JWT") ||
               msg.contains("Invalid JWT") || msg.contains("invalid_jwt") ||
               msg.contains("User not found") || msg.contains("session_not_found")
    }

    /**
     * Cierra la sesion localmente y navega al login.
     * Se llama cuando el servidor rechaza el token (usuario borrado o token expirado).
     */
    private fun forceLogout() {
        SessionManager.clearSession(this)
        Toast.makeText(this, "Tu sesion ha expirado. Inicia sesion de nuevo.", Toast.LENGTH_LONG).show()
        navigateToLogin()
    }

    private fun handleLogout(token: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { SupabaseAuth.signOut(token) }
            SessionManager.clearSession(this@MainActivity)
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}

// ─── Composables ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    posts: List<Post>,
    isLoadingFeed: Boolean,
    errorMessage: String?,
    currentUserId: String,
    onCreatePostClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    onRefreshClicked: () -> Unit,
    onDeletePost: (Post) -> Unit,
    onLikePost: (Post) -> Unit,
    onOpenPost: (Post) -> Unit,
    onSearchClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PicGram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClicked) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar usuarios")
                    }
                    IconButton(onClick = onRefreshClicked) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(onClick = onLogoutClicked) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesion")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreatePostClicked,
                icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo post") },
                text = { Text("Nueva publicacion") }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoadingFeed -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMessage != null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefreshClicked) { Text("Reintentar") }
                }
                posts.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Tu feed esta vacio", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Busca y sigue a otros usuarios para ver sus publicaciones aqui.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            currentUserId = currentUserId,
                            onCardClicked = { onOpenPost(post) },
                            onLikeClicked = { onLikePost(post) },
                            onDeleteClicked = { onDeletePost(post) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    currentUserId: String,
    onCardClicked: () -> Unit,
    onLikeClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar publicacion") },
            text = { Text("Esta accion eliminara la publicacion permanentemente. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteClicked() }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Card(
        onClick = onCardClicked,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
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
                    Text(post.username, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(formatTimestamp(post.timestamp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (post.userId == currentUserId) {
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Favorite.let { Icons.Default.FavoriteBorder },
                            contentDescription = "Borrar",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Imagen
            if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
                AsyncImage(
                    model = post.mediaUrl,
                    contentDescription = "Imagen del post",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // Caption
            if (post.caption.isNotBlank()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(post.username, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(post.caption, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Fila de acciones: Likes + Comentarios
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onLikeClicked() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (post.likedByMe) "Quitar like" else "Dar like",
                        tint = if (post.likedByMe) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${post.likesCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Comentarios",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${post.commentsCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "ahora mismo"
        diff < 3_600_000L -> "hace ${diff / 60_000} min"
        diff < 86_400_000L -> "hace ${diff / 3_600_000} h"
        else -> SimpleDateFormat("dd MMM yyyy", Locale("es")).format(Date(timestamp))
    }
}