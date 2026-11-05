package com.example.picgram.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.picgram.ProfileAvatar
import com.example.picgram.VideoPlayer
import com.example.picgram.data.model.*
import com.example.picgram.presentation.*
import com.example.picgram.ui.NotificationsInbox
import com.example.picgram.ui.MessagesInbox
import com.example.picgram.ui.ChatMessageBubble
import com.example.picgram.ui.activeStoryGroups
import com.example.picgram.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CommunityScreen(page: CommunityPage, state: CommunityUiState, me: String, username: String,
    onNewMessage: () -> Unit,
    pushConfigured: Boolean, onPushSettings: () -> Unit,
    onBack: () -> Unit, onRefresh: () -> Unit, onSave: (ProfileDetails) -> Unit, onPhoto: () -> Unit,
    onPublish: (Uri?) -> Unit, onSend: (String) -> Unit, onOlder: () -> Unit,
    onChat: (String, String) -> Unit, onProfile: (String, String) -> Unit,
    onResolve: (FollowRequest, Boolean) -> Unit, onRead: (String) -> Unit,
    onNotificationPost: (AppNotification) -> Unit) {
    if (page == CommunityPage.CONVERSATIONS) {
        MessagesInbox(state, username, onBack, onRefresh, onNewMessage, onChat,
            onOwnProfile = { onProfile(me, state.profile?.username ?: username) })
        return
    }
    if (page == CommunityPage.NOTIFICATIONS) {
        NotificationsInbox(state, pushConfigured, onPushSettings, onBack, onRefresh, onProfile, onResolve,
            onOpen = { notification ->
                if (notification.kind in listOf("like", "comment")) onNotificationPost(notification)
                else {
                    onRead(notification.id)
                    if (notification.kind == "message") onChat(notification.actorId, notification.username)
                    else onProfile(notification.actorId, notification.username)
                }
            })
        return
    }
    val title = when (page) {
        CommunityPage.EDIT_PROFILE -> "Editar perfil"
        CommunityPage.CREATE_STORY -> "Crear historia"
        CommunityPage.CHAT -> username.ifBlank { "Mensajes" }
        CommunityPage.NOTIFICATIONS -> "Notificaciones y solicitudes"
        else -> "Mensajes"
    }
    Scaffold(topBar = { GlassTopAppBar(title = {
        if (page == CommunityPage.CHAT) {
            val profile = state.chatProfile
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable(enabled = profile != null) { profile?.let { onProfile(it.id, it.username) } }) {
                ProfileAvatar(profile?.username ?: username, profile?.avatarUrl.orEmpty(), Modifier.size(40.dp))
                Text(profile?.let { it.name.ifBlank { it.username.ifBlank { title } } } ?: title,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        } else Text(title)
    }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Reintentar carga") }
            }
            when (page) {
                CommunityPage.EDIT_PROFILE -> state.profile?.let { profile -> ProfileEditor(profile, state, onSave, onPhoto, onBack) }
                CommunityPage.CREATE_STORY -> StoryComposer(state, onPublish, onBack)
                CommunityPage.CHAT -> ChatPanel(state, me, onSend, onOlder)
                else -> Unit
            }
        }
    }
}

@Composable
private fun ProfileEditor(profile: ProfileDetails, state: CommunityUiState, onSave: (ProfileDetails) -> Unit, onPhoto: () -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var bio by rememberSaveable { mutableStateOf(profile.bio) }
    var phone by rememberSaveable { mutableStateOf(profile.phone) }
    var birthday by rememberSaveable { mutableStateOf(profile.birthday) }
    var location by rememberSaveable { mutableStateOf(profile.location) }
    var privateAccount by rememberSaveable { mutableStateOf(profile.isPrivate) }
    LaunchedEffect(state.profileSaved) { if (state.profileSaved > 0) onBack() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(profile.username, profile.avatarUrl, Modifier.size(80.dp))
        TextButton(onClick = onPhoto, enabled = !state.busy) { Text("Cambiar foto de perfil") }
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        Text("@${profile.username}")
        OutlinedTextField(bio, { bio = it }, label = { Text("Biografía (máx. 500)") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        Text("Datos personales: solo tú puedes verlos", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(birthday, { birthday = it }, label = { Text("Fecha de nacimiento (AAAA-MM-DD)") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(location, { location = it }, label = { Text("Ubicación") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cuenta privada", Modifier.weight(1f))
            Switch(privateAccount, { privateAccount = it }, enabled = !state.busy)
        }
        Text("En privado, solo tus seguidores aprobados ven tus publicaciones. Al hacerla pública se aceptan las solicitudes pendientes.")
        GlassButton(onClick = { onSave(profile.copy(name = name, bio = bio, phone = phone, birthday = birthday, location = location, isPrivate = privateAccount)) }, enabled = !state.busy) { Text("Guardar cambios") }
    }
}

@Composable
private fun ColumnScope.ChatPanel(state: CommunityUiState, me: String, onSend: (String) -> Unit, onOlder: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    LaunchedEffect(state.messageSent) { if (state.messageSent > 0) draft = "" }
    Text(if (state.connected) "En tiempo real" else "Reconectando…", Modifier.padding(8.dp))
    LazyColumn(Modifier.fillMaxWidth().weight(1f), reverseLayout = true) {
        state.pendingMessage?.takeIf { pending -> state.messages.none { it.id == pending.id } }?.let { pending ->
            item(key = "pending:${pending.id}") {
                ChatMessageBubble(pending, me, state.chatProfile, now,
                    pendingStatus = if (state.sendingMessage) "Enviando…" else "No enviado · Reintentar")
            }
        }
        items(state.messages.reversed(), key = { it.id }) { m ->
            ChatMessageBubble(m, me, state.chatProfile, now)
        }
        if (state.hasOlderMessages) item { TextButton(onClick = onOlder, enabled = !state.busy) { Text("Cargar anteriores") } }
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), label = { Text("Mensaje") },
            enabled = !state.busy && state.pendingMessage == null, maxLines = 4)
        TextButton(onClick = { onSend(draft) }, enabled = !state.busy && (draft.isNotBlank() || state.pendingMessage != null)) {
            Text(if (state.pendingMessage == null) "Enviar" else "Reintentar")
        }
    }
}

@Composable
private fun StoryComposer(state: CommunityUiState, onPublish: (Uri?) -> Unit, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var video by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> selected = uri?.toString() }
    LaunchedEffect(state.storyPublished) { if (state.storyPublished > 0) onBack() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("La historia será visible durante 24 horas para tus seguidores.")
        Row {
            TextButton(onClick = { video = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.busy && !state.hasPendingStory) { Text("Elegir imagen") }
            TextButton(onClick = { video = true; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, enabled = !state.busy && !state.hasPendingStory) { Text("Elegir video") }
        }
        selected?.let { if (video) VideoPlayer(it, Modifier.fillMaxWidth().heightIn(max = 320.dp))
            else AsyncImage(it, "Vista previa", Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit) }
        GlassButton(onClick = { onPublish(selected?.let(Uri::parse)) }, enabled = !state.busy && (selected != null || state.hasPendingStory)) { Text(if (state.hasPendingStory) "Reintentar publicación" else "Publicar historia") }
    }
}

@Composable
fun StoriesStrip(stories: List<Story>, currentUserId: String, currentUsername: String, currentAvatarUrl: String,
    onCreate: () -> Unit) {
    var now by remember { mutableLongStateOf(monotonicMillis()) }
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var storyIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { now = monotonicMillis(); delay(1000) } }
    val groups = activeStoryGroups(stories, now)
    val ring = Brush.sweepGradient(listOf(Color(0xFFEA335B), Color(0xFFFFAF45), Color(0xFFB638CF), Color(0xFFEA335B)))
    val own = groups[currentUserId].orEmpty()
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "own-story") {
            Column(Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(80.dp).clickable {
                    if (own.isEmpty()) onCreate() else { storyIndex = 0; selectedUser = currentUserId }
                }) {
                    val frame = if (own.isNotEmpty()) Modifier.border(3.dp, ring, CircleShape)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ProfileAvatar(currentUsername, currentAvatarUrl, Modifier.fillMaxSize().then(frame).padding(5.dp))
                    IconButton(onClick = onCreate, modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape)) {
                        Icon(Icons.Default.Add, "Crear historia", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
                    }
                }
                Text("Tu historia", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        items(groups.keys.filter { it != currentUserId }, key = { it }) { userId ->
            val story = groups.getValue(userId).first()
            Column(Modifier.width(82.dp).clickable { storyIndex = 0; selectedUser = userId },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(80.dp).border(3.dp, ring, CircleShape).padding(5.dp)) {
                    ProfileAvatar(story.username, story.avatarUrl, Modifier.fillMaxSize())
                }
                Text(story.username, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
    val selectedStories = groups[selectedUser].orEmpty()
    LaunchedEffect(selectedUser, selectedStories.isEmpty()) { if (selectedStories.isEmpty()) selectedUser = null }
    selectedStories.getOrNull(storyIndex.coerceAtMost(selectedStories.lastIndex))?.let { story ->
        Dialog(onDismissRequest = { selectedUser = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(story.username, story.avatarUrl, Modifier.size(36.dp))
                    Text(story.username, Modifier.weight(1f).padding(12.dp), maxLines = 1)
                    Text("${storyIndex.coerceAtMost(selectedStories.lastIndex) + 1}/${selectedStories.size}")
                    TextButton(onClick = { selectedUser = null }) { Text("Cerrar") }
                }
                if (story.mediaType == "video") Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { VideoPlayer(story.mediaUrl, Modifier.fillMaxWidth()) }
                else AsyncImage(story.mediaUrl, "Historia de ${story.username}", Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { storyIndex = (storyIndex - 1).coerceAtLeast(0) }, enabled = storyIndex > 0) { Text("Anterior") }
                    TextButton(onClick = {
                        val index = storyIndex.coerceAtMost(selectedStories.lastIndex)
                        if (index < selectedStories.lastIndex) storyIndex = index + 1 else selectedUser = null
                    }) { Text(if (storyIndex < selectedStories.lastIndex) "Siguiente" else "Finalizar") }
                }
            } }
        }
    }
}
