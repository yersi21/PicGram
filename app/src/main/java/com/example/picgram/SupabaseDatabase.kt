package com.example.picgram

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de Supabase Database (PostgREST) via REST API.
 * Todos los metodos son bloqueantes â€” llamar desde Dispatchers.IO.
 */
object SupabaseDatabase {

    private fun request(
        method: String,
        endpoint: String,
        token: String,
        body: JSONObject? = null,
        prefer: String? = null
    ): String {
        val url = URL("${BuildConfig.SUPABASE_URL}$endpoint")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            connectTimeout = 15_000
            readTimeout = 15_000
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            return if (code in 200..299) {
                connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw Exception("Database error ($code): $err")
            }
        } finally {
            connection.disconnect()
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // USERS
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long) {
        val body = JSONObject().apply {
            put("id", id); put("email", email)
            put("username", username); put("created_at", createdAt)
        }
        request("POST", "/rest/v1/users", token, body, prefer = "return=minimal")
    }

    fun getUsername(token: String, userId: String): String {
        val response = request("GET", "/rest/v1/users?id=eq.$userId&select=username", token)
        return try {
            val array = JSONArray(response)
            if (array.length() > 0) array.getJSONObject(0).optString("username", "")
            else ""   // Perfil no encontrado â€” el caller decide el fallback
        } catch (_: Exception) { "" }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // POSTS
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Obtiene publicaciones del feed personalizado: solo posts del usuario actual y
     * de los usuarios que sigue. Si no sigue a nadie, solo muestra sus propios posts.
     *
     * Flujo:
     *  1. GET /follows → IDs de usuarios seguidos + currentUserId
     *  2. GET /posts?user_id=in.(ids) → posts filtrados
     *  3. GET /likes + /comments → contadores en Kotlin
     */
    fun getPosts(token: String, currentUserId: String): List<Post> {
        // 1. IDs de usuarios cuyo contenido debe aparecer: yo + a quienes sigo
        val feedUserIds = mutableListOf(currentUserId)
        try {
            val followsJson = request("GET", "/rest/v1/follows?follower_id=eq.$currentUserId&select=following_id", token)
            val arr = JSONArray(followsJson)
            for (i in 0 until arr.length()) {
                feedUserIds.add(arr.getJSONObject(i).optString("following_id"))
            }
        } catch (_: Exception) { /* tabla follows aun no existe — solo posts propios */ }

        val idsFilter = feedUserIds.joinToString(",")
        val postsJson = request("GET", "/rest/v1/posts?user_id=in.($idsFilter)&select=*&order=timestamp.desc", token)

        // 2. Likes â€” si la tabla no existe aun retorna lista vacia graciosamente
        val likesCountByPost = mutableMapOf<String, Int>()
        val likedIds = mutableSetOf<String>()
        try {
            val arr = JSONArray(request("GET", "/rest/v1/likes?select=post_id,user_id", token))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val postId = obj.optString("post_id")
                val userId = obj.optString("user_id")
                likesCountByPost[postId] = (likesCountByPost[postId] ?: 0) + 1
                if (userId == currentUserId) likedIds.add(postId)
            }
        } catch (_: Exception) { /* tabla aun no creada â€” mostrar ceros */ }

        // 3. Conteo de comentarios por post
        val commentsCountByPost = mutableMapOf<String, Int>()
        try {
            val arr = JSONArray(request("GET", "/rest/v1/comments?select=post_id", token))
            for (i in 0 until arr.length()) {
                val postId = arr.getJSONObject(i).optString("post_id")
                commentsCountByPost[postId] = (commentsCountByPost[postId] ?: 0) + 1
            }
        } catch (_: Exception) { /* tabla aun no creada â€” mostrar ceros */ }

        return try {
            val arr = JSONArray(postsJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val postId = obj.optString("id")
                Post(
                    id = postId,
                    userId = obj.optString("user_id"),
                    userEmail = obj.optString("user_email"),
                    username = obj.optString("username"),
                    caption = obj.optString("caption"),
                    mediaUrl = obj.optString("media_url"),
                    mediaType = obj.optString("media_type", "image"),
                    timestamp = obj.optLong("timestamp"),
                    likesCount = likesCountByPost[postId] ?: 0,
                    commentsCount = commentsCountByPost[postId] ?: 0,
                    likedByMe = postId in likedIds
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun insertPost(
        token: String, userId: String, userEmail: String, username: String,
        caption: String, mediaUrl: String, mediaType: String, timestamp: Long
    ) {
        val body = JSONObject().apply {
            put("user_id", userId); put("user_email", userEmail)
            put("username", username); put("caption", caption)
            put("media_url", mediaUrl); put("media_type", mediaType)
            put("timestamp", timestamp)
        }
        request("POST", "/rest/v1/posts", token, body, prefer = "return=minimal")
    }

    fun deletePost(token: String, postId: String) {
        request("DELETE", "/rest/v1/posts?id=eq.$postId", token)
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // LIKES
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Retorna (likesCount, likedByMe) para un post especifico.
     * Se usa en PostDetailActivity para cargar el estado inicial de likes.
     */
    fun getLikesForPost(token: String, postId: String, currentUserId: String): Pair<Int, Boolean> {
        val response = request("GET", "/rest/v1/likes?post_id=eq.$postId&select=user_id", token)
        return try {
            val arr = JSONArray(response)
            val count = arr.length()
            val likedByMe = (0 until count).any { arr.getJSONObject(it).optString("user_id") == currentUserId }
            Pair(count, likedByMe)
        } catch (_: Exception) { Pair(0, false) }
    }

    /**
     * Alterna el like en un post. Si [currentlyLiked] es true elimina el like, si no lo agrega.
     */
    fun toggleLike(token: String, postId: String, userId: String, currentlyLiked: Boolean) {
        if (currentlyLiked) {
            request("DELETE", "/rest/v1/likes?post_id=eq.$postId&user_id=eq.$userId", token)
        } else {
            val body = JSONObject().apply { put("post_id", postId); put("user_id", userId) }
            request("POST", "/rest/v1/likes", token, body, prefer = "return=minimal")
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // COMMENTS
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Agrega un comentario a un post. */
    fun addComment(
        token: String, postId: String, userId: String,
        username: String, content: String, timestamp: Long
    ) {
        val body = JSONObject().apply {
            put("post_id", postId); put("user_id", userId)
            put("username", username); put("content", content)
            put("timestamp", timestamp)
        }
        request("POST", "/rest/v1/comments", token, body, prefer = "return=minimal")
    }

    /** Obtiene todos los comentarios de un post, ordenados del mas antiguo al mas nuevo. */
    fun getComments(token: String, postId: String): List<Comment> {
        val response = request("GET", "/rest/v1/comments?post_id=eq.$postId&order=timestamp.asc", token)
        return try {
            val arr = JSONArray(response)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Comment(
                    id = obj.optString("id"),
                    postId = obj.optString("post_id"),
                    userId = obj.optString("user_id"),
                    username = obj.optString("username"),
                    content = obj.optString("content"),
                    timestamp = obj.optLong("timestamp")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Elimina un comentario (solo el autor puede hacerlo por RLS). */
    fun deleteComment(token: String, commentId: String) {
        request("DELETE", "/rest/v1/comments?id=eq.$commentId", token)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUSQUEDA DE USUARIOS (RF-06)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Busca usuarios en public.users cuyo username coincida (parcialmente, sin distincion
     * de mayusculas) con [query]. Usa el operador ilike de PostgREST con wildcard *.
     *
     * Ejemplo: searchUsers(token, "mar") retorna "maria", "mario", "smarty", etc.
     * Limit: 20 resultados maximo.
     *
     * IMPORTANTE: Debe llamarse desde un hilo de IO (Dispatchers.IO).
     */
    fun searchUsers(token: String, query: String): List<UserProfile> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val response = request(
            "GET",
            "/rest/v1/users?username=ilike.*$q*&select=id,username,email&limit=20",
            token
        )
        return try {
            val arr = JSONArray(response)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                UserProfile(
                    id = obj.optString("id"),
                    username = obj.optString("username"),
                    email = obj.optString("email")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Obtiene todas las publicaciones de un usuario especifico ordenadas por
     * fecha descendente. Se usa en la pantalla de perfil (UserProfileActivity).
     *
     * Los contadores de likes y comentarios se dejan en 0 intencionalmente:
     * se cargan al abrir el detalle de cada publicacion (PostDetailActivity).
     *
     * IMPORTANTE: Debe llamarse desde un hilo de IO (Dispatchers.IO).
     */
    fun getUserPosts(token: String, userId: String): List<Post> {
        val response = request(
            "GET",
            "/rest/v1/posts?user_id=eq.$userId&select=*&order=timestamp.desc",
            token
        )
        return try {
            val arr = JSONArray(response)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Post(
                    id = obj.optString("id"),
                    userId = obj.optString("user_id"),
                    userEmail = obj.optString("user_email"),
                    username = obj.optString("username"),
                    caption = obj.optString("caption"),
                    mediaUrl = obj.optString("media_url"),
                    mediaType = obj.optString("media_type", "image"),
                    timestamp = obj.optLong("timestamp")
                )
            }
        } catch (_: Exception) { emptyList() }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // FOLLOWS (Sistema de seguimiento)
    // ─────────────────────────────────────────────────────────────────────────

    /** Retorna los IDs de los usuarios que sigue [userId]. */
    fun getFollowingIds(token: String, userId: String): List<String> {
        return try {
            val arr = JSONArray(request("GET", "/rest/v1/follows?follower_id=eq.$userId&select=following_id", token))
            (0 until arr.length()).map { arr.getJSONObject(it).optString("following_id") }
        } catch (_: Exception) { emptyList() }
    }

    /** Retorna true si [followerId] sigue a [followingId]. */
    fun isFollowing(token: String, followerId: String, followingId: String): Boolean {
        return try {
            JSONArray(request("GET",
                "/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId&select=id",
                token)).length() > 0
        } catch (_: Exception) { false }
    }

    /** Sigue a un usuario. Idempotente por la restriccion UNIQUE en la tabla. */
    fun followUser(token: String, followerId: String, followingId: String) {
        val body = JSONObject().apply {
            put("follower_id", followerId)
            put("following_id", followingId)
        }
        request("POST", "/rest/v1/follows", token, body, prefer = "return=minimal")
    }

    /** Deja de seguir a un usuario. */
    fun unfollowUser(token: String, followerId: String, followingId: String) {
        request("DELETE", "/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId", token)
    }

    /** Cuenta cuantos seguidores tiene [userId]. */
    fun getFollowersCount(token: String, userId: String): Int {
        return try {
            JSONArray(request("GET", "/rest/v1/follows?following_id=eq.$userId&select=id", token)).length()
        } catch (_: Exception) { 0 }
    }

    /** Cuenta a cuantos usuarios sigue [userId]. */
    fun getFollowingCount(token: String, userId: String): Int {
        return try {
            JSONArray(request("GET", "/rest/v1/follows?follower_id=eq.$userId&select=id", token)).length()
        } catch (_: Exception) { 0 }
    }
}