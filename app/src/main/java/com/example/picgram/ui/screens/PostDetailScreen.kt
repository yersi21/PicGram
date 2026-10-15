package com.example.picgram.ui.screens

import com.example.picgram.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.*
import kotlinx.coroutines.flow.first
import com.example.picgram.presentation.*

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
    hasPendingComment: Boolean,
    scrollToBottom: Int,
    onCommentTextChange: (String) -> Unit,
    onBackClicked: () -> Unit,
    onLikeClicked: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onDeletePost: () -> Unit,
    onSendComment: () -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onOpenProfile: (String, String) -> Unit
) {
    var showDeletePostDialog by remember { mutableStateOf(false) }
    val doubleTapLike by rememberUpdatedState(onDoubleTapLike)
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll al último comentario cuando scrollToBottom cambia
    LaunchedEffect(scrollToBottom, comments.size) {
        if (scrollToBottom > 0 && comments.isNotEmpty()) {
            // Los items del LazyColumn son: imagen(0) + acciones(1) + divider(2) + caption(3?) + fecha(4?) + divider(5?) + comentarios
            // Usamos el índice del último item total
            val mediaItems = if (post.mediaUrl.isNotBlank() && post.mediaType in listOf("image", "video")) 1 else 0
            val captionItems = if (post.caption.isNotBlank()) 1 else 0
            val expectedCount = 3 + mediaItems + captionItems + comments.size
            val count = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= expectedCount }
            listState.animateScrollToItem(count - 1)
        }
    }

    if (showDeletePostDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeletePostDialog = false },
            title = { Text("Borrar publicacion") },
            text = { Text("Esta accion eliminara la publicacion y su imagen permanentemente.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeletePostDialog = false; onDeletePost() },
                    enabled = !isDeleting
                ) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePostDialog = false }, enabled = !isDeleting) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar del autor del post
                        ProfileAvatar(post.username, post.avatarUrl, Modifier.size(32.dp),
                            onClick = { onOpenProfile(post.userId, post.username) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            post.username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, enabled = !isDeleting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    if (isOwner) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 12.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { showDeletePostDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Borrar publicacion",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            )
        },
        // ── Barra inferior fija: campo de comentario + botón enviar ───────────
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding().imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp).liquidGlass(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        colors = glassTextFieldColors(),
                        value = commentText,
                        onValueChange = onCommentTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un comentario...") },
                        maxLines = 4,
                        enabled = !hasPendingComment && !isSubmittingComment && !isLoadingDetail && !isDeleting,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            onSendComment()
                        },
                        enabled = commentText.isNotBlank() && !isSubmittingComment && !isLoadingDetail && !isDeleting
                    ) {
                        if (isSubmittingComment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar comentario",
                                tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ── Imagen del post ──────────────────────────────────────────────
            if (post.mediaUrl.isNotBlank() && post.mediaType == "video") {
                item(key = "video") {
                    VideoPlayer(post.mediaUrl, Modifier.fillMaxWidth(), onDoubleTap = { doubleTapLike() })
                }
            }
            if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
                item(key = "image") {
                    AsyncImage(
                        model = post.mediaUrl,
                        contentDescription = "Imagen de la publicacion",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .pointerInput(post.id) { detectTapGestures(onDoubleTap = { doubleTapLike() }) },
                        contentScale = ContentScale.FillWidth
                    )
                }
            }

            // ── Acciones: likes y conteo de comentarios ──────────────────────
            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comentarios",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${comments.size} ${if (comments.size == 1) "comentario" else "comentarios"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }

            // ── Caption y fecha ──────────────────────────────────────────────
            if (post.caption.isNotBlank()) {
                item(key = "caption") {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            post.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(post.caption, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item(key = "timestamp") {
                Text(
                    text = formatTimestamp(post.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
            }

            // ── Cabecera de sección de comentarios ───────────────────────────
            item(key = "comments_header") {
                Text(
                    text = "Comentarios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            // ── Estado de carga ──────────────────────────────────────────────
            if (isLoadingDetail) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (comments.isEmpty()) {
                // ── Feed vacío ───────────────────────────────────────────────
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sin comentarios aun.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "¡Se el primero en comentar!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // ── Lista de comentarios — cada uno con autor, texto y fecha ─
                items(comments, key = { it.id }) { comment ->
                    CommentItem(
                        comment = comment,
                        canDelete = comment.userId == currentUserId,
                        onDeleteClicked = { onDeleteComment(comment) },
                        onAuthorClicked = { onOpenProfile(comment.userId, comment.username) }
                    )
                }
            }
        }
    }
}

/**
 * Item de comentario individual:
 * - Avatar circular con inicial del autor
 * - Username en negrita + texto del comentario
 * - Fecha relativa debajo
 * - Botón de borrar (solo visible para el autor del comentario)
 */
@Composable
fun CommentItem(
    comment: Comment,
    canDelete: Boolean,
    onDeleteClicked: () -> Unit,
    onAuthorClicked: () -> Unit
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
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        ProfileAvatar(comment.username, comment.avatarUrl, Modifier.size(32.dp), onClick = onAuthorClicked)

        Spacer(modifier = Modifier.width(8.dp))

        // Contenido: username + texto + fecha
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    comment.username,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    comment.content,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTimestamp(comment.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Botón de borrar — solo para el autor del comentario
        if (canDelete) {
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Borrar comentario",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
