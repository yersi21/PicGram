package com.example.picgram.ui.screens

import com.example.picgram.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import com.example.picgram.presentation.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    posts: List<Post>,
    isLoadingFeed: Boolean,
    errorMessage: String?,
    currentUserId: String,
    currentUsername: String,
    currentAvatarUrl: String,
    onCreatePostClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    onRefreshClicked: () -> Unit,
    onDeletePost: (Post) -> Unit,
    onLikePost: (Post, Boolean) -> Unit,
    onOpenPost: (Post) -> Unit,
    onSearchClicked: () -> Unit,
    onProfileClicked: () -> Unit,
    onOpenProfile: (String, String) -> Unit
) {
    Scaffold(
        topBar = {
            BoxWithConstraints {
                val compactToolbar = maxWidth < 400.dp
                var showMore by remember { mutableStateOf(false) }
                GlassTopAppBar(
                    title = { PicGramWordmark(Modifier.width(112.dp).height(52.dp), compact = true) },
                    actions = {
                        IconToggleButton(checked = isDarkMode, onCheckedChange = onDarkModeChanged) {
                            Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkMode) "Desactivar modo oscuro" else "Activar modo oscuro",
                                modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onSearchClicked) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar usuarios")
                        }
                        if (!compactToolbar) {
                            IconButton(onClick = onRefreshClicked) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                            }
                        }
                        // Avatar circular — abre el perfil propio al tocarlo
                        IconButton(onClick = onProfileClicked) {
                            ProfileAvatar(currentUsername, currentAvatarUrl, Modifier.size(32.dp))
                        }
                        if (compactToolbar) {
                            Box {
                                IconButton(onClick = { showMore = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                                }
                                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                    DropdownMenuItem(text = { Text("Actualizar") },
                                        onClick = { showMore = false; onRefreshClicked() })
                                    DropdownMenuItem(text = { Text("Cerrar sesión") },
                                        onClick = { showMore = false; onLogoutClicked() })
                                }
                            }
                        } else {
                            IconButton(onClick = onLogoutClicked) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesion")
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreatePostClicked,
                modifier = Modifier.liquidGlass(RoundedCornerShape(24.dp), accent = true),
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo post") },
                text = { Text("Nueva publicacion") }
            )
        }
    ) { innerPadding ->
        // Pull-to-refresh envuelve todo el contenido del feed
        PullToRefreshBox(
            isRefreshing = isLoadingFeed,
            onRefresh = onRefreshClicked,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoadingFeed && posts.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        GlassButton(onClick = onRefreshClicked) { Text("Reintentar") }
                    }
                }
                posts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Tu feed esta vacio",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Busca y sigue a otros usuarios para ver sus publicaciones aqui.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onSearchClicked) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buscar usuarios")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                currentUserId = currentUserId,
                                onCardClicked = { onOpenPost(post) },
                                onLikeClicked = { onLikePost(post, false) },
                                onDoubleTapLike = { onLikePost(post, true) },
                                onDeleteClicked = { onDeletePost(post) },
                                onAuthorClicked = { onOpenProfile(post.userId, post.username) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: Post,
    currentUserId: String,
    onCardClicked: () -> Unit,
    onLikeClicked: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onDeleteClicked: () -> Unit,
    onAuthorClicked: () -> Unit
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .liquidGlass(RoundedCornerShape(26.dp))
            .combinedClickable(onClick = onCardClicked, onClickLabel = "Abrir publicación",
                onDoubleClick = onDoubleTapLike),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: avatar + username + timestamp + boton borrar (solo dueno)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar: foto real si existe, inicial si no
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = post.userId.isNotBlank(),
                            onClickLabel = "Ver perfil de ${post.username}",
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = onAuthorClicked),
                    contentAlignment = Alignment.Center
                ) {
                    if (post.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = post.avatarUrl,
                            contentDescription = "Foto de ${post.username}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.username, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatTimestamp(post.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Boton de borrar — solo visible para el dueno del post
                if (post.userId == currentUserId) {
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Borrar publicacion",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Imagen del post
            if (post.mediaUrl.isNotBlank() && post.mediaType == "video") {
                VideoPlayer(post.mediaUrl, Modifier.fillMaxWidth(),
                    onDoubleTap = onDoubleTapLike)
            }
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

/**
 * Formatea un timestamp Unix (ms) en texto relativo legible.
 * Ej.: "ahora mismo", "hace 3 min", "hace 2 h", "15 Sep 2026"
 */
fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L      -> "ahora mismo"
        diff < 3_600_000L   -> "hace ${diff / 60_000} min"
        diff < 86_400_000L  -> "hace ${diff / 3_600_000} h"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es")).format(Date(timestamp))
    }
}
