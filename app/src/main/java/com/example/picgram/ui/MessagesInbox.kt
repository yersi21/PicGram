package com.example.picgram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.picgram.ProfileAvatar
import com.example.picgram.data.model.Conversation
import com.example.picgram.data.model.ProfileDetails
import com.example.picgram.presentation.CommunityUiState
import com.example.picgram.ui.theme.LocalGlassDark
import com.example.picgram.ui.theme.PicGramTheme
import kotlinx.coroutines.delay

internal fun filterConversations(conversations: List<Conversation>, query: String, unreadOnly: Boolean): List<Conversation> {
    val term = query.trim().removePrefix("@")
    return conversations.filter { conversation ->
        (!unreadOnly || conversation.unread > 0) && (term.isBlank() ||
            conversation.username.contains(term, ignoreCase = true) ||
            conversation.displayName.contains(term, ignoreCase = true))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesInbox(state: CommunityUiState, username: String, onBack: () -> Unit, onRefresh: () -> Unit,
    onNewMessage: () -> Unit, onChat: (String, String) -> Unit, onOwnProfile: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    val conversations = remember(state.conversations, query, unreadOnly) {
        filterConversations(state.conversations, query, unreadOnly)
    }
    val account = state.profile?.username?.ifBlank { username } ?: username
    val background = if (LocalGlassDark.current) Color(0xFF0D1015) else Color(0xFFFAFAFC)
    Scaffold(containerColor = background, topBar = {
        CenterAlignedTopAppBar(
            title = { Text(account.ifBlank { "Mensajes" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = { IconButton(onClick = onNewMessage) { Icon(Icons.Outlined.Edit, "Nuevo mensaje") } },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = background)
        )
    }, bottomBar = {
        Row(Modifier.fillMaxWidth().background(background).navigationBarsPadding().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.Home, "Volver al inicio") }
            Box(Modifier.size(width = 64.dp, height = 44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .semantics { contentDescription = "Mensajes, sección actual" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChatBubbleOutline, null)
            }
            IconButton(onClick = onOwnProfile, modifier = Modifier.semantics { contentDescription = "Mi perfil" }) {
                ProfileAvatar(account, state.profile?.avatarUrl.orEmpty(), Modifier.size(30.dp))
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "search") {
                TextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(28.dp), placeholder = { Text("Buscar mensajes") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Borrar búsqueda") } },
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            }
            if (state.loading || state.busy) item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            state.error?.let { error -> item(key = "error") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh, enabled = !state.loading && !state.busy) { Text("Reintentar") }
                }
            } }
            if (query.isBlank() && !unreadOnly) item(key = "contacts") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    item(key = "me") {
                        InboxContact(account, state.profile?.avatarUrl.orEmpty(), "Tu perfil", onOwnProfile)
                    }
                    items(state.conversations.take(12), key = { it.peerId }) { conversation ->
                        InboxContact(conversation.username, conversation.avatarUrl,
                            conversation.displayName.ifBlank { conversation.username }) { onChat(conversation.peerId, conversation.username) }
                    }
                }
            }
            item(key = "heading") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mensajes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { unreadOnly = !unreadOnly }) {
                        Text(if (unreadOnly) "Ver todos" else "Sin leer${if (state.unreadMessageCount > 0) " (${state.unreadMessageCount})" else ""}")
                    }
                    IconButton(onClick = onRefresh, enabled = !state.loading && !state.busy) { Icon(Icons.Outlined.Refresh, "Actualizar mensajes") }
                }
            }
            if (conversations.isEmpty() && !state.loading) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(40.dp))
                    Text(when { query.isNotBlank() -> "No se encontraron conversaciones"; unreadOnly -> "No tienes mensajes sin leer"; else -> "Tus mensajes aparecerán aquí" }, fontWeight = FontWeight.SemiBold)
                    if (query.isBlank() && !unreadOnly) {
                        Text("Busca a una persona para iniciar una conversación.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onNewMessage) { Text("Nuevo mensaje") }
                    }
                }
            }
            items(conversations, key = { "chat:${it.peerId}" }) { conversation ->
                InboxConversation(conversation, now) { onChat(conversation.peerId, conversation.username) }
            }
        }
    }
}

@Composable
private fun InboxContact(username: String, avatar: String, label: String, onClick: () -> Unit) {
    Column(Modifier.width(76.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileAvatar(username, avatar, Modifier.size(70.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InboxConversation(conversation: Conversation, now: Long, onClick: () -> Unit) {
    val unread = conversation.unread > 0
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ProfileAvatar(conversation.username, conversation.avatarUrl, Modifier.size(62.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(conversation.displayName.ifBlank { conversation.username }.ifBlank { "Usuario" },
                style = MaterialTheme.typography.bodyLarge, fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(conversation.lastContent, style = MaterialTheme.typography.bodyMedium,
                color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(notificationRelativeTime(conversation.lastAt, now), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (unread) Badge(containerColor = Color(0xFFD92332), contentColor = Color.White,
            modifier = Modifier.semantics { contentDescription = "${conversation.unread} mensajes sin leer" }) {
            Text(conversation.unread.toString(), fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun MessagesInboxDarkPreview() {
    PicGramTheme(darkTheme = true) { InboxPreviewContent() }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820, fontScale = 1.3f)
@Composable
private fun MessagesInboxLightPreview() {
    PicGramTheme(darkTheme = false) { InboxPreviewContent() }
}

@Composable
private fun InboxPreviewContent() {
    val state = CommunityUiState(profile = ProfileDetails(username = "picgram"), conversations = listOf(
        Conversation("1", "¡Hola! ¿Cómo estás?", "2026-10-29T12:00:00Z", 3, "anderson", displayName = "Anderson"),
        Conversation("2", "Nos vemos mañana", "2026-10-29T10:00:00Z", 0, "luis", displayName = "Luis Fernando")))
    MessagesInbox(state, "picgram", {}, {}, {}, { _, _ -> }, {})
}
