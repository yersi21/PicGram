package com.example.picgram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.picgram.ProfileAvatar
import com.example.picgram.VideoThumbnail
import com.example.picgram.data.model.AppNotification
import com.example.picgram.data.model.FollowRequest
import com.example.picgram.presentation.CommunityUiState
import com.example.picgram.ui.theme.LocalGlassDark
import com.example.picgram.ui.theme.PicGramTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsInbox(state: CommunityUiState, pushConfigured: Boolean, onPushSettings: () -> Unit,
    onBack: () -> Unit, onRefresh: () -> Unit, onProfile: (String, String) -> Unit,
    onResolve: (FollowRequest, Boolean) -> Unit, onOpen: (AppNotification) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var visibleCount by rememberSaveable { mutableIntStateOf(30) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    val sections = remember(state.notifications, now, visibleCount) {
        notificationSections(state.notifications, now, maxItems = visibleCount)
    }
    val background = if (LocalGlassDark.current) Color(0xFF0D1015) else Color(0xFFFAFAFC)
    Scaffold(containerColor = background, topBar = {
        TopAppBar(title = { Text("Notificaciones", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = { IconButton(onClick = onPushSettings) { Icon(Icons.Outlined.Notifications, "Ajustes de notificaciones") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = background))
    }) { padding ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.busy) item(key = "busy") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item(key = "error") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Reintentar") }
                    }
                } }
                if (!pushConfigured) item(key = "push-info") {
                    Text("Puedes consultar tu actividad aquí en PicGram.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (state.requests.isNotEmpty()) {
                    item(key = "requests-heading") { NotificationHeading("Solicitudes de seguimiento") }
                    items(state.requests, key = { "request:${it.userId}" }) { request ->
                        FollowRequestRow(request, state.busy, onProfile, onResolve)
                    }
                }
                sections.forEach { section ->
                    item(key = "period:${section.period}") { NotificationHeading(section.period.label) }
                    items(section.notifications, key = { "notification:${it.id}" }) { notification ->
                        NotificationActivityRow(notification, now, onProfile, onOpen)
                    }
                }
                if (state.notifications.isEmpty() && state.requests.isEmpty() && !state.loading && state.error == null) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.Notifications, null, Modifier.size(44.dp))
                        Text("Aún no tienes notificaciones", fontWeight = FontWeight.SemiBold)
                        Text("Aquí verás los Me gusta, comentarios, seguidores y mensajes que recibas.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (state.notifications.size > visibleCount) item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { visibleCount += 30 }) { Text("Mostrar más") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHeading(title: String) {
    Text(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun NotificationActivityRow(notification: AppNotification, now: Long,
    onProfile: (String, String) -> Unit, onOpen: (AppNotification) -> Unit) {
    val action = when (notification.kind) {
        "like" -> "indicó que le gusta tu publicación."
        "story_like" -> "indicó que le gusta tu historia."
        "comment" -> "comentó tu publicación."
        "message" -> "te envió un mensaje."
        "request" -> "solicitó seguirte."
        "follow" -> "comenzó a seguirte."
        else -> "tiene nueva actividad para ti."
    }
    Row(Modifier.fillMaxWidth().clickable { onOpen(notification) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ProfileAvatar(notification.username, notification.avatarUrl, Modifier.size(56.dp),
            onClick = { onProfile(notification.actorId, notification.username) })
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(notification.username.ifBlank { "Usuario" }) }
            append(" "); append(action); append(" ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(notificationRelativeTime(notification.createdAt, now))
            }
        }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (notification.postMediaUrl.isNotBlank() && notification.kind in listOf("like", "comment", "story_like")) {
            Box(Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (notification.postMediaType == "video") VideoThumbnail(notification.postMediaUrl, Modifier.fillMaxSize())
                else AsyncImage(notification.postMediaUrl, if (notification.kind == "story_like") "Historia relacionada" else "Publicación relacionada", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        } else if (notification.kind == "message") {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!notification.read) Box(Modifier.size(8.dp).background(Color(0xFFD92332), CircleShape)
            .semantics { contentDescription = "Sin leer" })
    }
}

@Composable
private fun FollowRequestRow(request: FollowRequest, busy: Boolean, onProfile: (String, String) -> Unit,
    onResolve: (FollowRequest, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        ProfileAvatar(request.username, request.avatarUrl, Modifier.size(56.dp), onClick = { onProfile(request.userId, request.username) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(request.username.ifBlank { "Usuario" }) }
                append(" quiere seguirte.")
            }, Modifier.clickable { onProfile(request.userId, request.username) }, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onResolve(request, true) }, enabled = !busy, shape = RoundedCornerShape(8.dp)) { Text("Aceptar") }
                OutlinedButton(onClick = { onResolve(request, false) }, enabled = !busy, shape = RoundedCornerShape(8.dp)) { Text("Rechazar") }
            }
        }
    }
}

@Preview(name = "Notificaciones · oscuro", widthDp = 390, heightDp = 820)
@Composable
private fun NotificationsDarkPreview() { NotificationsPreview(true) }

@Preview(name = "Notificaciones · claro", widthDp = 360, heightDp = 800, fontScale = 1.2f)
@Composable
private fun NotificationsLightPreview() { NotificationsPreview(false) }

@Composable
private fun NotificationsPreview(dark: Boolean) {
    val now = java.time.Instant.now()
    PicGramTheme(darkTheme = dark) {
        NotificationsInbox(CommunityUiState(notifications = listOf(
            AppNotification("1", "ana", "like", now.minusSeconds(300).toString(), false, "post", "ana"),
            AppNotification("2", "luis", "comment", now.minusSeconds(86_400).toString(), true, "post", "luis"),
            AppNotification("3", "sol", "follow", now.minusSeconds(259_200).toString(), true, username = "sol"))),
            true, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
    }
}
