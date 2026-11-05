package com.example.picgram.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.picgram.ProfileAvatar
import com.example.picgram.data.model.DirectMessage
import com.example.picgram.data.model.ProfileDetails

/** Only a persisted outgoing message can be shown as sent or seen. */
internal fun messageReceipt(message: DirectMessage, currentUserId: String): String? = when {
    message.senderId != currentUserId || message.createdAt.isBlank() -> null
    !message.readAt.isNullOrBlank() -> "Visto"
    else -> "Enviado"
}

@Composable
fun ChatMessageBubble(message: DirectMessage, currentUserId: String, peer: ProfileDetails?, now: Long,
    pendingStatus: String? = null) {
    val outgoing = message.senderId == currentUserId
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom) {
        if (!outgoing) {
            ProfileAvatar(peer?.username.orEmpty(), peer?.avatarUrl.orEmpty(), Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.fillMaxWidth(0.84f), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
            Surface(shape = RoundedCornerShape(20.dp),
                color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(message.content, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
            }
            val receipt = pendingStatus ?: messageReceipt(message, currentUserId)
            val age = if (message.createdAt.isNotBlank()) notificationRelativeTime(message.createdAt, now) else null
            Text(listOfNotNull(age, receipt).joinToString(" · "),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
