package com.example.picgram.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picgram.ui.theme.PicGramTheme
import com.example.picgram.ui.theme.liquidGlass
import com.example.picgram.ProfileAvatar
import com.example.picgram.ui.theme.LocalGlassDark

@Composable
fun FeedNavigation(unreadMessages: Long, username: String, avatarUrl: String,
    onHome: () -> Unit, onMessages: () -> Unit, onSearch: () -> Unit, onProfile: () -> Unit,
    selectedSection: String = "home") {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (LocalGlassDark.current) Color(0xFF0D1015) else Color(0xFFFAFAFC))
            .navigationBarsPadding().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onHome, modifier = Modifier.size(width = 64.dp, height = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .semantics { stateDescription = "Inicio, sección actual" }) {
            Icon(Icons.Default.Home, "Inicio", Modifier.size(28.dp))
        }
        UnreadNavigationIcon(Icons.Outlined.ChatBubbleOutline, "Mensajes", unreadMessages,
            when (unreadMessages) {
                0L -> "No hay mensajes sin leer"
                1L -> "1 mensaje sin leer"
                else -> "$unreadMessages mensajes sin leer"
            }, onMessages, selectedSection == "messages")
        IconButton(onClick = onSearch, modifier = Modifier.background(if (selectedSection == "search") MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, CircleShape).semantics { selected = selectedSection == "search" }) { Icon(Icons.Outlined.Search, "Buscar usuarios", Modifier.size(28.dp)) }
        IconButton(onClick = onProfile, modifier = Modifier.semantics {
            contentDescription = "Mi perfil"; selected = selectedSection == "profile"
        }) {
            ProfileAvatar(username, avatarUrl, Modifier.size(30.dp).then(
                if (selectedSection == "profile") Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(3.dp) else Modifier))
        }
    }
}

@Composable
fun FeedNotificationAction(unreadNotifications: Long, onNotifications: () -> Unit) {
    UnreadNavigationIcon(Icons.Outlined.Notifications, "Notificaciones", unreadNotifications,
            when (unreadNotifications) {
                0L -> "No hay notificaciones sin leer"
                1L -> "1 notificación sin leer"
                else -> "$unreadNotifications notificaciones sin leer"
            }, onNotifications)
}

@Composable
private fun UnreadNavigationIcon(icon: ImageVector, label: String, unreadCount: Long,
    description: String, onClick: () -> Unit, isSelected: Boolean = false) {
    val text = unreadCount.coerceAtLeast(0).toString()
    // A square badge with CircleShape stays circular even for multi-digit counts.
    val badgeDiameter = maxOf(20f, (text.length * 7f + 8f) * LocalDensity.current.fontScale).dp
    val buttonSize = maxOf(56.dp, badgeDiameter + 20.dp)
    Box(Modifier.size(buttonSize)) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize().background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, CircleShape).semantics { stateDescription = description; selected = isSelected }) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurface)
        }
        if (unreadCount > 0) {
            Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp)
                .size(badgeDiameter).background(Color(0xFFD92332), CircleShape)
                .clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Text(text, color = Color.White, fontSize = 11.sp, lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Preview(name = "Inicio · iconos con actividad", widthDp = 360, heightDp = 90)
@Composable
private fun UnreadCommunityNavigationPreview() {
    PicGramTheme(darkTheme = false) { FeedNavigation(8L, "yo", "", {}, {}, {}, {}) }
}

@Preview(name = "Inicio oscuro · sin actividad", widthDp = 360, heightDp = 90)
@Composable
private fun ReadCommunityNavigationPreview() {
    PicGramTheme(darkTheme = true) { FeedNavigation(0L, "yo", "", {}, {}, {}, {}) }
}

@Preview(name = "Inicio oscuro · contadores", widthDp = 360, heightDp = 100)
@Composable
private fun NumberedCommunityNavigationPreview() {
    PicGramTheme(darkTheme = true) { FeedNavigation(125L, "yo", "", {}, {}, {}, {}) }
}
