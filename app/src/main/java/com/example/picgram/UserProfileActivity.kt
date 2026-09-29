package com.example.picgram

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.picgram.ui.theme.PicGramTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserProfileActivity : ComponentActivity() {

    private var posts by mutableStateOf<List<Post>>(emptyList())
    private var isLoading by mutableStateOf(true)
    private var isFollowing by mutableStateOf(false)
    private var isTogglingFollow by mutableStateOf(false)
    private var followersCount by mutableStateOf(0)
    private var followingCount by mutableStateOf(0)

    // Se inicializa en onCreate y se reutiliza en onResume
    private var profileUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileUserId = intent.getStringExtra("user_id")  ?: ""
        val username      = intent.getStringExtra("username") ?: "Usuario"

        val session = SessionManager.currentSession(this)
        if (session == null) { finish(); return }

        val isOwnProfile = session.userId == profileUserId

        setContent {
            PicGramTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UserProfileScreen(
                        username = username,
                        posts = posts,
                        isLoading = isLoading,
                        isOwnProfile = isOwnProfile,
                        isFollowing = isFollowing,
                        isTogglingFollow = isTogglingFollow,
                        followersCount = followersCount,
                        followingCount = followingCount,
                        onBackClicked = { finish() },
                        onFollowClicked = { handleFollowToggle(session.accessToken, session.userId, profileUserId) },
                        onPostClicked = { post ->
                            startActivity(Intent(this, PostDetailActivity::class.java).apply {
                                putExtra("post_id",        post.id)
                                putExtra("post_user_id",   post.userId)
                                putExtra("post_username",  post.username)
                                putExtra("post_caption",   post.caption)
                                putExtra("post_media_url", post.mediaUrl)
                                putExtra("post_media_type",post.mediaType)
                                putExtra("post_timestamp", post.timestamp)
                            })
                        }
                    )
                }
            }
        }

        loadProfileData(session.accessToken, session.userId, profileUserId)
    }

    /**
     * Al volver a la pantalla (desde PostDetailActivity o al retomar la app),
     * recarga los contadores de seguidores/siguiendo e isFollowing en tiempo real.
     * No recarga los posts completos para evitar parpadeo de la cuadricula.
     */
    override fun onResume() {
        super.onResume()
        val s = SessionManager.currentSession(this) ?: return
        if (!isLoading) {
            refreshCounters(s.accessToken, s.userId, profileUserId)
        }
    }

    private fun loadProfileData(token: String, currentUserId: String, profileUserId: String) {
        lifecycleScope.launch {
            isLoading = true
            try {
                val (loadedPosts, following, followers, following2) =
                    withContext(Dispatchers.IO) {
                        val p = SupabaseDatabase.getUserPosts(token, profileUserId)
                        val f = SupabaseDatabase.isFollowing(token, currentUserId, profileUserId)
                        val fc = SupabaseDatabase.getFollowersCount(token, profileUserId)
                        val fwc = SupabaseDatabase.getFollowingCount(token, profileUserId)
                        Data4(p, f, fc, fwc)
                    }
                posts = loadedPosts
                isFollowing = following
                followersCount = followers
                followingCount = following2
            } catch (e: Exception) {
                Toast.makeText(this@UserProfileActivity,
                    "Error al cargar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Recarga solo los contadores (seguidores, siguiendo, isFollowing) sin tocar
     * la lista de posts. Llamado desde onResume para mantener los numeros frescos.
     */
    private fun refreshCounters(token: String, currentUserId: String, profileUserId: String) {
        lifecycleScope.launch {
            try {
                val (following, followers, followingCnt) = withContext(Dispatchers.IO) {
                    val f  = SupabaseDatabase.isFollowing(token, currentUserId, profileUserId)
                    val fc = SupabaseDatabase.getFollowersCount(token, profileUserId)
                    val fw = SupabaseDatabase.getFollowingCount(token, profileUserId)
                    Triple(f, fc, fw)
                }
                isFollowing = following
                followersCount = followers
                followingCount = followingCnt
            } catch (_: Exception) { /* silencioso — los valores locales son suficientes */ }
        }
    }

    private fun handleFollowToggle(token: String, currentUserId: String, profileUserId: String) {
        if (isTogglingFollow) return
        isTogglingFollow = true
        val wasFollowing = isFollowing
        // Actualizar UI optimistamente
        isFollowing = !wasFollowing
        followersCount = if (wasFollowing) (followersCount - 1).coerceAtLeast(0) else followersCount + 1

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (wasFollowing) SupabaseDatabase.unfollowUser(token, currentUserId, profileUserId)
                    else              SupabaseDatabase.followUser(token, currentUserId, profileUserId)
                }
            } catch (e: Exception) {
                // Revertir si falla
                isFollowing = wasFollowing
                followersCount = if (wasFollowing) followersCount + 1 else (followersCount - 1).coerceAtLeast(0)
                Toast.makeText(this@UserProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isTogglingFollow = false
            }
        }
    }
}

// Clase auxiliar para retornar 4 valores de withContext
private data class Data4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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
    onBackClicked: () -> Unit,
    onFollowClicked: () -> Unit,
    onPostClicked: (Post) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(username, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
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
            // ── Cabecera del perfil ────────────────────────────────────────────
            item(span = { GridItemSpan(3) }) {
                ProfileHeader(
                    username = username,
                    postCount = posts.size,
                    followersCount = followersCount,
                    followingCount = followingCount,
                    isLoading = isLoading,
                    isOwnProfile = isOwnProfile,
                    isFollowing = isFollowing,
                    isTogglingFollow = isTogglingFollow,
                    onFollowClicked = onFollowClicked
                )
            }

            // ── Separador con icono de cuadricula ──────────────────────────────
            item(span = { GridItemSpan(3) }) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
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
    onFollowClicked: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(90.dp).clip(CircleShape)
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Estadisticas: publicaciones | seguidores | siguiendo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(count = if (isLoading) "..." else "$postCount",
                label = "publicaciones")
            StatItem(count = if (isLoading) "..." else "$followersCount",
                label = "seguidores")
            StatItem(count = if (isLoading) "..." else "$followingCount",
                label = "siguiendo")
        }

        // Boton Seguir / Siguiendo (no aparece en tu propio perfil)
        if (!isOwnProfile) {
            Spacer(modifier = Modifier.height(16.dp))
            if (isFollowing) {
                OutlinedButton(
                    onClick = onFollowClicked,
                    enabled = !isTogglingFollow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTogglingFollow) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Siguiendo", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onFollowClicked,
                    enabled = !isTogglingFollow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTogglingFollow) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Seguir", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        } else {
            Text(text = post.caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp),
                textAlign = TextAlign.Center)
        }
    }
}