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
import androidx.compose.material.icons.filled.GridView
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
            text = { Text("Tu foto se eliminará del perfil y volverá a mostrarse tu inicial.") },
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(span = { GridItemSpan(3) }) {
                ProfileHeader(
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
            }

            item(span = { GridItemSpan(3) }) {
                Column {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider()
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

            if (!isLoading && posts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Sin publicaciones aun",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }

            items(posts, key = { it.id }) { post ->
                PostThumbnail(post = post, onClick = { onPostClicked(post) })
            }
        }
    }
}

@Composable
fun ProfileHeader(
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .liquidGlass().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Avatar circular ─────────────────────────────────────────────────
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

        // Label "Agregar/Cambiar foto" — solo perfil propio
        if (isOwnProfile) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !isUploadingAvatar) { onAvatarClicked() }
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (avatarUrl.isBlank()) "Agregar foto" else "Opciones de foto",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(count = if (isLoading) "..." else "$postCount",      label = "publicaciones")
            StatItem(count = if (isLoading) "..." else "$followersCount", label = "seguidores",
                modifier = Modifier.clickable(enabled = !isLoading, onClickLabel = "Ver seguidores", onClick = onFollowersClicked))
            StatItem(count = if (isLoading) "..." else "$followingCount", label = "siguiendo",
                modifier = Modifier.clickable(enabled = !isLoading, onClickLabel = "Ver seguidos", onClick = onFollowingClicked))
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
                    Text("Seguir", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StatItem(count: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PostThumbnail(post: Post, onClick: () -> Unit) {
    Box(
        modifier = Modifier.aspectRatio(1f).clickable { onClick() }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
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
