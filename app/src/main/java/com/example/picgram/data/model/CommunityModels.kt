package com.example.picgram.data.model

import java.time.LocalDate
import java.time.Instant

data class ProfileDetails(val id: String = "", val username: String = "", val name: String = "",
    val bio: String = "", val avatarUrl: String = "", val isPrivate: Boolean = false,
    val phone: String = "", val birthday: String = "", val location: String = "") {
    fun validated(): ProfileDetails {
        val clean = copy(name = ProfileNames.validatedName(name), bio = bio.trim(), phone = phone.trim(), location = location.trim())
        require(clean.bio.length <= 500) { "La biografía admite hasta 500 caracteres" }
        require(clean.phone.length <= 30 && clean.location.length <= 120) { "Datos personales demasiado largos" }
        if (birthday.isNotBlank()) require(!LocalDate.parse(birthday).isAfter(LocalDate.now())) { "La fecha no puede ser futura" }
        return clean
    }
}
data class Story(val id: String, val userId: String, val mediaUrl: String, val mediaType: String,
    val expiresAt: Long, val username: String = "", val avatarUrl: String = "", val likedByMe: Boolean = false) {
    fun isActive(now: Long) = now < expiresAt
}
data class DirectMessage(val id: String, val senderId: String, val recipientId: String,
    val content: String, val createdAt: String, val readAt: String? = null)

data class StoryViewer(val userId: String, val username: String, val name: String,
    val avatarUrl: String, val viewedAt: String)
data class Conversation(val peerId: String, val lastContent: String, val lastAt: String,
    val unread: Int, val username: String = "", val avatarUrl: String = "", val displayName: String = "")
data class AppNotification(val id: String, val actorId: String, val kind: String,
    val createdAt: String, val read: Boolean, val postId: String = "", val username: String = "",
    val avatarUrl: String = "", val postMediaUrl: String = "", val postMediaType: String = "", val storyId: String = "")
data class FollowRequest(val userId: String, val username: String, val avatarUrl: String = "")
enum class FollowStatus { NONE, REQUESTED, FOLLOWING }
data class UserPresence(val online: Boolean, val lastSeenAgeMillis: Long?, val fetchedAt: Long = monotonicMillis())
fun validatedMessage(value: String): String = value.trim().also {
    require(it.length in 1..4000) { "El mensaje debe tener entre 1 y 4000 caracteres" }
}
fun epochMillis(value: String): Long = Instant.parse(value).toEpochMilli()
fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
