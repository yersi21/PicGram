package com.example.picgram.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onNotificationPost: (AppNotification) -> Unit, onNotificationStory: (AppNotification) -> Unit) {
    if (page == CommunityPage.CONVERSATIONS) {
        MessagesInbox(state, username, onBack, onRefresh, onNewMessage, onChat,
            onOwnProfile = { onProfile(me, state.profile?.username ?: username) })
        return
    }
    if (page == CommunityPage.NOTIFICATIONS) {
        NotificationsInbox(state, pushConfigured, onPushSettings, onBack, onRefresh, onProfile, onResolve,
            onOpen = { notification ->
                if (notification.kind in listOf("like", "comment")) onNotificationPost(notification)
                else if (notification.kind == "story_like") onNotificationStory(notification)
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
    pendingLikes: Set<String>, replies: Map<String, StoryReplyUiState>,
    onLike: (String) -> Unit, onReplyChanged: (String, String) -> Unit, onReply: (String) -> Unit,
    viewers: Map<String, StoryViewersUiState>, onStoryOpened: (String) -> Unit,
    onLoadViewers: (String) -> Unit, onViewerProfile: (String, String) -> Unit,
    onChat: (String, String) -> Unit,
    initialStoryId: String? = null,
    onCreate: () -> Unit) {
    var now by remember { mutableLongStateOf(monotonicMillis()) }
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var storyIndex by remember { mutableIntStateOf(0) }
    var initialStoryOpened by rememberSaveable(initialStoryId) { mutableStateOf(false) }
    LaunchedEffect(initialStoryId, stories) {
        if (!initialStoryOpened && initialStoryId != null) {
            val target = stories.firstOrNull { it.id == initialStoryId && it.isActive(monotonicMillis()) }
            if (target != null) {
                storyIndex = stories.filter { it.userId == target.userId && it.isActive(monotonicMillis()) }.indexOf(target)
                selectedUser = target.userId
                initialStoryOpened = true
            }
        }
    }
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
        fun moveStory(backward: Boolean) {
            val index = storyIndex.coerceAtMost(selectedStories.lastIndex)
            val next = index + if (backward) -1 else 1
            if (next in selectedStories.indices) storyIndex = next else selectedUser = null
        }
        val currentMoveStory by rememberUpdatedState<(Boolean) -> Unit>(::moveStory)
        // Key by story, not its refreshed object: live feed updates must not restart the timer.
        var deadline by rememberSaveable(story.id) { mutableLongStateOf(monotonicMillis() + 10_000L) }
        var pausedAt by remember(story.id) { mutableStateOf<Long?>(null) }
        var held by remember(story.id) { mutableStateOf(false) }
        var showViewers by remember(story.id) { mutableStateOf(false) }
        fun updatePause(pressing: Boolean = held, showing: Boolean = showViewers) {
            held = pressing
            showViewers = showing
            if (pressing || showing) {
                if (pausedAt == null) pausedAt = monotonicMillis()
            } else {
                pausedAt?.let { deadline += monotonicMillis() - it }
                pausedAt = null
            }
        }
        var remaining by remember(story.id) { mutableLongStateOf((deadline - monotonicMillis()).coerceAtLeast(0)) }
        LaunchedEffect(story.id) { onStoryOpened(story.id) }
        LaunchedEffect(story.id) {
            while (true) {
                remaining = (deadline - (pausedAt ?: monotonicMillis())).coerceAtLeast(0)
                if (remaining == 0L) { selectedUser = null; break }
                delay(minOf(remaining, 50L))
            }
        }
        Dialog(onDismissRequest = { selectedUser = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize().pointerInput(story.id) {
                // Observe without consuming: likes, replies and video controls keep their gestures.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    updatePause(pressing = true)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        updatePause(pressing = false)
                    }
                }
            }) { Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                val currentIndex = storyIndex.coerceAtMost(selectedStories.lastIndex)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).semantics {
                    contentDescription = "Historia ${currentIndex + 1} de ${selectedStories.size}"
                }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    selectedStories.indices.forEach { index ->
                        val progress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> (1f - remaining / 10_000f).coerceIn(0f, 1f)
                            else -> 0f
                        }
                        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(MaterialTheme.colorScheme.onSurface))
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(story.username, story.avatarUrl, Modifier.size(36.dp))
                    Text(story.username, Modifier.weight(1f).padding(12.dp), maxLines = 1)
                    TextButton(onClick = { selectedUser = null }) { Text("Cerrar") }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (story.mediaType == "video") VideoPlayer(story.mediaUrl, Modifier.fillMaxWidth(),
                        autoPlay = true, showControls = false)
                    else AsyncImage(story.mediaUrl, "Historia de ${story.username}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    // Keep navigation over the media; the reply, like and viewer controls retain their own taps.
                    Box(Modifier.matchParentSize().semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Historia anterior") { currentMoveStory(true); true },
                            CustomAccessibilityAction("Historia siguiente") { currentMoveStory(false); true })
                    }.pointerInput(story.id) {
                        detectTapGestures(
                            onLongPress = { /* Holding only pauses the timer; release must not navigate. */ },
                            onTap = { position -> currentMoveStory(position.x < size.width / 2f) })
                    })
                }
                if (story.userId == currentUserId) {
                    val viewState = viewers[story.id] ?: StoryViewersUiState()
                    TextButton(onClick = { updatePause(showing = true); onLoadViewers(story.id) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (viewState.loading) "Cargando vistas…" else if (viewState.error != null) "Ver vistas"
                            else "Visto por ${viewState.viewers.size}")
                    }
                } else {
                    val reply = replies[story.id] ?: StoryReplyUiState()
                    val canSend = !reply.sending && (reply.draft.isNotBlank() || reply.pending != null)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                        .liquidGlass(RoundedCornerShape(26.dp)).padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(reply.draft, { onReplyChanged(story.id, it) },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            placeholder = { Text("Enviar mensaje", maxLines = 1, style = MaterialTheme.typography.bodyMedium) },
                            shape = RoundedCornerShape(28.dp), maxLines = 3,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = glassTextFieldColors(),
                            enabled = !reply.sending && reply.pending == null)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { onLike(story.id) }, enabled = story.id !in pendingLikes) {
                            Icon(if (story.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (story.likedByMe) "Quitar like de la historia" else "Me gusta la historia",
                                modifier = Modifier.size(28.dp),
                                tint = if (story.likedByMe) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = if (story.id in pendingLikes) 0.4f else 1f))
                        }
                        IconButton(onClick = { selectedUser = null; onChat(story.userId, story.username) }) {
                            Icon(Icons.Outlined.ChatBubbleOutline, "Abrir conversación con ${story.username}",
                                modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { onReply(story.id) },
                            modifier = Modifier.liquidGlass(CircleShape, accent = canSend),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
                            enabled = canSend) {
                            if (reply.sending) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Outlined.Send,
                                if (reply.pending != null) "Reintentar envío" else "Enviar mensaje al autor",
                                modifier = Modifier.size(28.dp))
                        }
                    }
                    reply.feedback?.let { Text(it, Modifier.padding(top = 8.dp, start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } }
        }
        if (showViewers && story.userId == currentUserId) {
            val viewState = viewers[story.id] ?: StoryViewersUiState()
            Dialog(onDismissRequest = { updatePause(showing = false) }) {
                Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Visto por ${viewState.viewers.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { updatePause(showing = false) }) { Text("Cerrar") }
                        }
                        if (viewState.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        viewState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (!viewState.loading && viewState.error == null && viewState.viewers.isEmpty()) {
                            Text("Todavía nadie ha visto esta historia", Modifier.padding(vertical = 16.dp))
                        }
                        LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(viewState.viewers, key = { it.userId }) { viewer ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    selectedUser = null
                                    onViewerProfile(viewer.userId, viewer.username)
                                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ProfileAvatar(viewer.username, viewer.avatarUrl, Modifier.size(44.dp))
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(viewer.name.ifBlank { viewer.username }, maxLines = 1)
                                        Text("@${viewer.username}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { onLoadViewers(story.id) }, enabled = !viewState.loading,
                            modifier = Modifier.align(Alignment.End)) { Text(if (viewState.error != null) "Reintentar" else "Actualizar") }
                    }
                }
            }
        }
    }
}
