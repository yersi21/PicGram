package com.example.picgram.data.model

data class Post(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val username: String = "",
    val caption: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image",
    val timestamp: Long = 0L,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val likedByMe: Boolean = false,
    val avatarUrl: String = ""
)

data class Comment(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val avatarUrl: String = ""
)

data class UserProfile(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val displayName: String = ""
)

data class UserSession(
    val accessToken: String,
    val userId: String,
    val email: String,
    val username: String,
    val refreshToken: String = "",
    val expiresAt: Long = 0L
)

data class AuthResult(
    val accessToken: String,
    val userId: String,
    val email: String,
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val username: String = "",
    val displayName: String = ""
)
