package com.example.picgram.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.picgram.PicGramWordmark
import com.example.picgram.Post
import com.example.picgram.ProfileAvatar
import com.example.picgram.VideoPlayer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onOpenProfile: (String, String) -> Unit,
    storiesHeader: @Composable () -> Unit = {},
    communityActions: @Composable () -> Unit = {},
    notificationAction: @Composable () -> Unit = {}
) {
    val background = if (isDarkMode) Color(0xFF0D1015) else Color(0xFFFAFAFC)
    var showMore by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    Scaffold(containerColor = background, bottomBar = communityActions, topBar = {
        CenterAlignedTopAppBar(
            title = { PicGramWordmark(Modifier.width(130.dp).height(48.dp), compact = true,
                tint = if (isDarkMode) Color.White else Color.Black) },
            navigationIcon = { IconButton(onClick = onCreatePostClicked) { Icon(Icons.Default.Add, "Crear publicación", Modifier.size(30.dp)) } },
            actions = {
                notificationAction()
                Box {
                    IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(text = { Text(if (isDarkMode) "Desactivar modo oscuro" else "Activar modo oscuro") },
                            leadingIcon = { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                            onClick = { showMore = false; onDarkModeChanged(!isDarkMode) })
                        DropdownMenuItem(text = { Text("Actualizar inicio") }, onClick = { showMore = false; onRefreshClicked() })
                        DropdownMenuItem(text = { Text("Mi perfil") }, onClick = { showMore = false; onProfileClicked() })
                        DropdownMenuItem(text = { Text("Cerrar sesión") }, onClick = { showMore = false; onLogoutClicked() })
                    }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = background))
    }) { padding ->
        PullToRefreshBox(isRefreshing = isLoadingFeed, onRefresh = onRefreshClicked,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                item(key = "stories") { storiesHeader() }
                if (isLoadingFeed && posts.isEmpty()) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                errorMessage?.let { error -> item(key = "error") {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        TextButton(onClick = onRefreshClicked) { Text("Reintentar") }
                    }
                } }
                if (posts.isEmpty() && !isLoadingFeed && errorMessage == null) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Tu feed está vacío", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Busca y sigue a otros usuarios para ver sus publicaciones aquí.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        OutlinedButton(onClick = onSearchClicked) {
                            Icon(Icons.Default.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Buscar usuarios")
                        }
                    }
                }
                items(posts, key = { "post:${it.id}" }) { post ->
                    PostCard(post, currentUserId, onCardClicked = { onOpenPost(post) },
                        onLikeClicked = { onLikePost(post, false) }, onDoubleTapLike = { onLikePost(post, true) },
                        onDeleteClicked = { onDeletePost(post) }, onAuthorClicked = { onOpenProfile(post.userId, post.username) },
                        nowMillis = now)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PostCard(post: Post, currentUserId: String, onCardClicked: () -> Unit, onLikeClicked: () -> Unit,
    onDoubleTapLike: () -> Unit, onDeleteClicked: () -> Unit, onAuthorClicked: () -> Unit,
    nowMillis: Long = System.currentTimeMillis()) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    if (showDeleteDialog) AlertDialog(onDismissRequest = { showDeleteDialog = false },
        title = { Text("Borrar publicación") }, text = { Text("Esta acción eliminará la publicación permanentemente. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { showDeleteDialog = false; onDeleteClicked() }) { Text("Borrar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } })
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileAvatar(post.username, post.avatarUrl, Modifier.size(38.dp), onClick = onAuthorClicked)
            Column(Modifier.weight(1f).clickable(enabled = post.userId.isNotBlank(), onClick = onAuthorClicked)) {
                Text(post.username.ifBlank { post.displayName }, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (post.displayName.isNotBlank() && post.displayName != post.username) {
                    Text(post.displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton(onClick = { showPostMenu = true }) { Icon(Icons.Default.MoreVert, "Opciones de publicación") }
                DropdownMenu(expanded = showPostMenu, onDismissRequest = { showPostMenu = false }) {
                    DropdownMenuItem(text = { Text("Ver publicación") }, onClick = { showPostMenu = false; onCardClicked() })
                    DropdownMenuItem(text = { Text("Ver perfil") }, onClick = { showPostMenu = false; onAuthorClicked() })
                    if (post.userId == currentUserId) DropdownMenuItem(text = { Text("Eliminar publicación") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showPostMenu = false; showDeleteDialog = true })
                }
            }
        }
        if (post.mediaUrl.isNotBlank() && post.mediaType == "video") {
            VideoPlayer(post.mediaUrl, Modifier.fillMaxWidth(), onDoubleTap = onDoubleTapLike)
        } else if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
            var aspect by remember(post.mediaUrl) { mutableFloatStateOf(1f) }
            AsyncImage(post.mediaUrl, "Publicación de ${post.username}",
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(onClick = onCardClicked, onClickLabel = "Abrir publicación", onDoubleClick = onDoubleTapLike),
                contentScale = ContentScale.Fit,
                onSuccess = { loaded ->
                    val drawable = loaded.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
                        aspect = (drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight).coerceIn(0.8f, 1.91f)
                })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLikeClicked) {
                Icon(if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (post.likedByMe) "Quitar Me gusta" else "Dar Me gusta",
                    tint = if (post.likedByMe) Color(0xFFED334F) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
            }
            Text(post.likesCount.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onCardClicked) { Icon(Icons.Default.ChatBubbleOutline, "Ver comentarios", Modifier.size(27.dp)) }
            Text(post.commentsCount.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        if (post.caption.isNotBlank()) {
            Text(androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(post.username); pop(); append("  "); append(post.caption)
            }, Modifier.padding(horizontal = 14.dp).clickable(onClick = onCardClicked), style = MaterialTheme.typography.bodyMedium)
        }
        if (post.commentsCount > 0) Text("Ver los ${post.commentsCount} comentarios",
            Modifier.clickable(onClick = onCardClicked).padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatTimestamp(post.timestamp, nowMillis), Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatTimestamp(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = nowMillis - timestamp
    return when {
        diff < 60_000L -> "ahora mismo"
        diff < 3_600_000L -> "hace ${diff / 60_000} min"
        diff < 86_400_000L -> "hace ${diff / 3_600_000} h"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es")).format(Date(timestamp))
    }
}
