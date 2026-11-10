package com.example.picgram.ui.screens

import com.example.picgram.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.GridView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.*
import com.example.picgram.presentation.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    bio: String,
    isPrivate: Boolean,
    followRequested: Boolean,
    onEditProfile: () -> Unit,
    onMessage: () -> Unit,
    displayName: String,
    username: String,
    posts: List<Post>,
    isLoading: Boolean,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isTogglingFollow: Boolean,
    followersCount: Int,
    followingCount: Int,
    avatarUrl: String,
    isUploadingAvatar: Boolean,
    onBackClicked: () -> Unit,
    onFollowClicked: () -> Unit,
    onAvatarClicked: () -> Unit,
    onDeleteAvatar: () -> Unit,
    onFollowersClicked: () -> Unit,
    onFollowingClicked: () -> Unit,
    onPostClicked: (Post) -> Unit
) {
    var showVideos by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val visiblePosts = if (showVideos) posts.filter { it.mediaType == "video" } else posts
    val context = LocalContext.current
    var showAvatarOptions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showDeleteAvatar by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var photoToView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(isUploadingAvatar) {
        if (isUploadingAvatar) { showAvatarOptions = false; showDeleteAvatar = false }
    }
    if (showAvatarOptions && isOwnProfile) {
        AvatarOptionsDialog(hasPhoto = avatarUrl.isNotBlank(), onDismiss = { showAvatarOptions = false },
            onChange = { showAvatarOptions = false; onAvatarClicked() },
            onView = { showAvatarOptions = false; photoToView = avatarUrl },
            onDelete = { showAvatarOptions = false; showDeleteAvatar = true })
    }
    if (showDeleteAvatar && isOwnProfile) {
        AlertDialog(onDismissRequest = { showDeleteAvatar = false }, title = { Text("Eliminar foto de perfil") },
            text = { Text("Tu foto se eliminarÃ¡ del perfil y volverÃ¡ a mostrarse tu inicial.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAvatar = false; onDeleteAvatar() },
                    enabled = !isUploadingAvatar && avatarUrl.isNotBlank()) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }, dismissButton = { TextButton(onClick = { showDeleteAvatar = false }) { Text("Cancelar") } })
    }
    photoToView?.takeIf { it.isNotBlank() }?.let { url ->
        ProfilePhotoViewer(url, username, onClose = { photoToView = null })
    }
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text(username, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(span = { GridItemSpan(3) }) {
                Column {
                ProfileHeader(
                    followRequested = followRequested,
                    displayName = displayName,
                    username          = username,
                    postCount         = posts.size,
                    followersCount    = followersCount,
                    followingCount    = followingCount,
                    isLoading         = isLoading,
                    isOwnProfile      = isOwnProfile,
                    isFollowing       = isFollowing,
                    isTogglingFollow  = isTogglingFollow,
                    avatarUrl         = avatarUrl,
                    isUploadingAvatar = isUploadingAvatar,
                    onFollowClicked   = onFollowClicked,
                    onAvatarClicked   = {
                        if (isOwnProfile) showAvatarOptions = true
                        else if (avatarUrl.isNotBlank()) photoToView = avatarUrl
                    },
                    onFollowersClicked = onFollowersClicked,
                    onFollowingClicked = onFollowingClicked
                )
                if (bio.isNotBlank()) Text(bio, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (isPrivate) Text("Cuenta privada", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOwnProfile) {
                        FilledTonalButton(onClick = onEditProfile, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Editar perfil") }
                        FilledTonalButton(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Mira mi perfil en PicGram: @$username")
                            }, "Compartir perfil"))
                        }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Compartir perfil") }
                    } else {
                        FilledTonalButton(onClick = onMessage, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text("Enviar mensaje") }
                    }
                }

                }
            }

            item(span = { GridItemSpan(3) }) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        listOf(false, true).forEach { videos ->
                            Column(Modifier.weight(1f).clickable { showVideos = videos }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (videos) Icons.Outlined.VideoLibrary else Icons.Default.GridView,
                                    contentDescription = if (videos) "Videos" else "Publicaciones",
                                    tint = if (showVideos == videos) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp).size(24.dp))
                                HorizontalDivider(thickness = 2.dp, color = if (showVideos == videos) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item(span = { GridItemSpan(3) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!isLoading && visiblePosts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center) {
                        Text(if (isPrivate && !isOwnProfile && !isFollowing) "Sigue esta cuenta para ver sus publicaciones" else if (showVideos) "Sin videos" else "Sin publicaciones aun",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }

            items(visiblePosts, key = { it.id }) { post ->
                PostThumbnail(post = post, onClick = { onPostClicked(post) })
            }
        }
    }
}

@Composable
fun ProfileHeader(
    followRequested: Boolean,
    displayName: String,
    username: String,
    postCount: Int,
    followersCount: Int,
    followingCount: Int,
    isLoading: Boolean,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isTogglingFollow: Boolean,
    avatarUrl: String,
    isUploadingAvatar: Boolean,
    onFollowClicked: () -> Unit,
    onAvatarClicked: () -> Unit,
    onFollowersClicked: () -> Unit,
    onFollowingClicked: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .clickable(enabled = !isUploadingAvatar && (isOwnProfile || avatarUrl.isNotBlank()),
                    onClickLabel = if (isOwnProfile) "Opciones de foto" else "Ver foto de perfil") { onAvatarClicked() },
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Foto de perfil de $username",
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Spinner de subida superpuesto
            if (isUploadingAvatar) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp, color = Color.White)
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Text(displayName.ifBlank { username }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(if (isLoading) "…" else "$postCount", "publicaciones")
                StatItem(if (isLoading) "…" else "$followersCount", "seguidores", Modifier.clickable(enabled = !isLoading, onClick = onFollowersClicked))
                StatItem(if (isLoading) "…" else "$followingCount", "seguidos", Modifier.clickable(enabled = !isLoading, onClick = onFollowingClicked))
            }
        }
        }
        if (!isOwnProfile) {    
            Spacer(modifier = Modifier.height(16.dp))
            if (isFollowing) {
                OutlinedButton(onClick = onFollowClicked, enabled = !isTogglingFollow,
                    modifier = Modifier.fillMaxWidth()) {
                    if (isTogglingFollow) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Siguiendo", fontWeight = FontWeight.SemiBold)
                }
            } else {
                GlassButton(onClick = onFollowClicked, enabled = !isTogglingFollow,
                    modifier = Modifier.fillMaxWidth()) {
                    if (isTogglingFollow) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (followRequested) "Solicitud enviada Â· Cancelar" else "Seguir", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StatItem(count: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PostThumbnail(post: Post, onClick: () -> Unit) {
    Box(
        modifier = Modifier.aspectRatio(1f).clickable { onClick() }

            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
            AsyncImage(
                model = post.mediaUrl,
                contentDescription = "Publicacion de ${post.username}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (post.mediaUrl.isNotBlank() && post.mediaType == "video") {
            VideoThumbnail(post.mediaUrl, Modifier.fillMaxSize())
        } else {
            Text(text = post.caption, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp), textAlign = TextAlign.Center)
        }
    }
}
