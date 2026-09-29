package com.example.picgram

/**
 * Publicacion del feed de PicGram.
 */
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
    val likedByMe: Boolean = false
)

/**
 * Comentario en una publicacion.
 */
data class Comment(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val content: String = "",
    val timestamp: Long = 0L
)

/**
 * Resultado de busqueda de usuario (RF-06).
 * Contiene solo los datos del perfil publico.
 */
data class UserProfile(
    val id: String = "",
    val username: String = "",
    val email: String = ""
)