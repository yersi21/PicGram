package com.example.picgram

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
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

/**
 * Vista de detalle de publicacion — pantalla completa con imagen, likes y comentarios.
 * Carga datos frescos desde Supabase al abrirse.
 */
class PostDetailActivity : ComponentActivity() {

    private var likesCount by mutableStateOf(0)
    private var likedByMe by mutableStateOf(false)
    private var comments by mutableStateOf<List<Comment>>(emptyList())
    private var isLoadingDetail by mutableStateOf(true)
    private var commentText by mutableStateOf("")
    private var isSubmittingComment by mutableStateOf(false)
    private var isDeleting by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val postId        = intent.getStringExtra("post_id")        ?: ""
        val postUserId    = intent.getStringExtra("post_user_id")   ?: ""
        val postUsername  = intent.getStringExtra("post_username")  ?: ""
        val postCaption   = intent.getStringExtra("post_caption")   ?: ""
        val postMediaUrl  = intent.getStringExtra("post_media_url") ?: ""
        val postMediaType = intent.getStringExtra("post_media_type")?: "image"
        val postTimestamp = intent.getLongExtra("post_timestamp", 0L)

        val session = SessionManager.currentSession(this)
        if (session == null) { finish(); return }

        val post = Post(
            id = postId, userId = postUserId, username = postUsername,
            caption = postCaption, mediaUrl = postMediaUrl,
            mediaType = postMediaType, timestamp = postTimestamp
        )
        val isOwner = session.userId == postUserId

        setContent {
            PicGramTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PostDetailScreen(
                        post = post,
                        isOwner = isOwner,
                        currentUserId = session.userId,
                        likesCount = likesCount,
                        likedByMe = likedByMe,
                        comments = comments,
                        isLoadingDetail = isLoadingDetail,
                        isDeleting = isDeleting,
                        commentText = commentText,
                        isSubmittingComment = isSubmittingComment,
                        onCommentTextChange = { commentText = it },
                        onBackClicked = { finish() },
                        onLikeClicked = { handleLike(session.accessToken, postId, session.userId) },
                        onDeletePost = { handleDeletePost(session.accessToken, post) },
                        onSendComment = { handleAddComment(session, postId) },
                        onDeleteComment = { comment -> handleDeleteComment(session.accessToken, comment) }
                    )
                }
            }
        }

        // Cargar datos frescos de likes y comentarios
        loadPostDetail(session.accessToken, postId, session.userId)
    }

    private fun loadPostDetail(token: String, postId: String, userId: String) {
        lifecycleScope.launch {
            isLoadingDetail = true
            try {
                val likesResult: Pair<Int, Boolean>
                val loadedComments: List<Comment>
                withContext(Dispatchers.IO) {
                    likesResult = SupabaseDatabase.getLikesForPost(token, postId, userId)
                    loadedComments = SupabaseDatabase.getComments(token, postId)
                }
                likesCount = likesResult.first
                likedByMe = likesResult.second
                comments = loadedComments
            } catch (e: Exception) {
                Toast.makeText(this@PostDetailActivity, "Error al cargar detalles: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingDetail = false
            }
        }
    }

    private fun handleLike(token: String, postId: String, userId: String) {
        val wasLiked = likedByMe
        // Optimistic update
        likedByMe = !wasLiked
        likesCount = if (wasLiked) (likesCount - 1).coerceAtLeast(0) else likesCount + 1
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseDatabase.toggleLike(token, postId, userId, wasLiked)
                }
            } catch (e: Exception) {
                likedByMe = wasLiked
                likesCount = if (wasLiked) likesCount + 1 else (likesCount - 1).coerceAtLeast(0)
                Toast.makeText(this@PostDetailActivity, "Error al actualizar like", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleAddComment(session: UserSession, postId: String) {
        val text = commentText.trim()
        if (text.isBlank()) return
        isSubmittingComment = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseDatabase.addComment(
                        token = session.accessToken,
                        postId = postId,
                        userId = session.userId,
                        username = session.username.ifBlank { session.email },
                        content = text,
                        timestamp = System.currentTimeMillis()
                    )
                }
                // Agregar el comentario localmente para respuesta inmediata
                val newComment = Comment(
                    id = System.currentTimeMillis().toString(), // ID temporal
                    postId = postId,
                    userId = session.userId,
                    username = session.username.ifBlank { session.email },
                    content = text,
                    timestamp = System.currentTimeMillis()
                )
                comments = comments + newComment
                commentText = ""
            } catch (e: Exception) {
                Toast.makeText(this@PostDetailActivity, "Error al comentar: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isSubmittingComment = false
            }
        }
    }

    private fun handleDeleteComment(token: String, comment: Comment) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { SupabaseDatabase.deleteComment(token, comment.id) }
                comments = comments.filter { it.id != comment.id }
            } catch (e: Exception) {
                Toast.makeText(this@PostDetailActivity, "Error al borrar comentario: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDeletePost(token: String, post: Post) {
        isDeleting = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (post.mediaUrl.isNotBlank()) {
                        val path = SupabaseStorage.extractPath(post.mediaUrl)
                        if (path != null) SupabaseStorage.delete(path, token)
                    }
                    SupabaseDatabase.deletePost(token, post.id)
                }
                Toast.makeText(this@PostDetailActivity, "Publicacion eliminada", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                isDeleting = false
                Toast.makeText(this@PostDetailActivity, "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    post: Post,
    isOwner: Boolean,
    currentUserId: String,
    likesCount: Int,
    likedByMe: Boolean,
    comments: List<Comment>,
    isLoadingDetail: Boolean,
    isDeleting: Boolean,
    commentText: String,
    isSubmittingComment: Boolean,
    onCommentTextChange: (String) -> Unit,
    onBackClicked: () -> Unit,
    onLikeClicked: () -> Unit,
    onDeletePost: () -> Unit,
    onSendComment: () -> Unit,
    onDeleteComment: (Comment) -> Unit
) {
    var showDeletePostDialog by remember { mutableStateOf(false) }

    if (showDeletePostDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeletePostDialog = false },
            title = { Text("Borrar publicacion") },
            text = { Text("Esta accion eliminara la publicacion y su imagen permanentemente.") },
            confirmButton = {
                TextButton(onClick = { showDeletePostDialog = false; onDeletePost() }, enabled = !isDeleting) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePostDialog = false }, enabled = !isDeleting) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = post.username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(post.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, enabled = !isDeleting) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    if (isOwner) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { showDeletePostDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            )
        },
        // ── Input de comentario fijo en la parte inferior ─────────────────────
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = onCommentTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un comentario...") },
                        singleLine = true,
                        enabled = !isSubmittingComment,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onSendComment,
                        enabled = commentText.isNotBlank() && !isSubmittingComment
                    ) {
                        if (isSubmittingComment) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Send, contentDescription = "Enviar",
                                tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
        ) {
            // ── Imagen a pantalla completa ─────────────────────────────────────
            if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
                AsyncImage(
                    model = post.mediaUrl,
                    contentDescription = "Imagen de la publicacion",
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    contentScale = ContentScale.FillWidth
                )
            }

            // ── Fila de likes y comentarios ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onLikeClicked, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (likedByMe) "Quitar like" else "Dar like",
                        tint = if (likedByMe) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "$likesCount ${if (likesCount == 1) "like" else "likes"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Icon(
                    Icons.Default.ChatBubbleOutline, contentDescription = "Comentarios",
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${comments.size} comentarios",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ── Caption ────────────────────────────────────────────────────────
            if (post.caption.isNotBlank()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(post.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(post.caption, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                text = formatTimestamp(post.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            // ── Lista de comentarios ───────────────────────────────────────────
            if (isLoadingDetail) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (comments.isEmpty()) {
                Text(
                    text = "Sin comentarios aun. Se el primero!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                )
            } else {
                comments.forEach { comment ->
                    CommentItem(
                        comment = comment,
                        canDelete = comment.userId == currentUserId,
                        onDeleteClicked = { onDeleteComment(comment) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    canDelete: Boolean,
    onDeleteClicked: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar comentario") },
            text = { Text("Se eliminara este comentario permanentemente.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteClicked() }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar del comentarista
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = comment.username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(comment.username, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(comment.content, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = formatTimestamp(comment.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Boton borrar solo para el autor del comentario
        if (canDelete) {
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Delete, contentDescription = "Borrar comentario",
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}