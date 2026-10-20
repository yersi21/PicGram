package com.example.picgram

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.picgram.data.model.ProfileNames

/** Blocking REST operations. Always call from Dispatchers.IO. */
object SupabaseDatabase {
    private fun request(method: String, endpoint: String, token: String,
                        body: JSONObject? = null, prefer: String? = null): String =
        authenticatedRequest(token) { validToken ->
            val connection = (URL("${BuildConfig.SUPABASE_URL}$endpoint").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $validToken")
                setRequestProperty("Content-Type", "application/json")
                if (prefer != null) setRequestProperty("Prefer", prefer)
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = body != null
            }
            try {
                if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw ApiException(code, "Database error ($code): $detail")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally { connection.disconnect() }
        }

    /** Explicit paging avoids silently truncating counts at the server row limit. */
    private fun rows(endpoint: String, token: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var offset = 0
        while (true) {
            val page = JSONArray(request("GET", "$endpoint&limit=200&offset=$offset", token))
            for (index in 0 until page.length()) result.add(page.getJSONObject(index))
            if (page.length() == 0) return result
            offset += page.length()
        }
    }

    private fun JSONObject.stringOrEmpty(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun post(obj: JSONObject) = Post(
        id = obj.getString("id"), userId = obj.getString("user_id"),
        userEmail = obj.stringOrEmpty("user_email"), username = obj.stringOrEmpty("username"),
        caption = obj.stringOrEmpty("caption"), mediaUrl = obj.stringOrEmpty("media_url"),
        mediaType = obj.optString("media_type", "image"), timestamp = obj.optLong("timestamp"))

    private fun comment(obj: JSONObject) = Comment(
        id = obj.getString("id"), postId = obj.getString("post_id"), userId = obj.getString("user_id"),
        username = obj.stringOrEmpty("username"), content = obj.getString("content"), timestamp = obj.optLong("timestamp"))

    fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long, displayName: String) {
        val name = if (displayName.isBlank()) "" else ProfileNames.validatedName(displayName)
        val body = JSONObject().put("id", id).put("email", email).put("username", username)
            .put("created_at", createdAt).put("display_name", name)
        request("POST", "/rest/v1/users?on_conflict=id", token, body,
            "resolution=ignore-duplicates,return=minimal")
    }

    fun getUsername(token: String, userId: String): String {
        val result = JSONArray(request("GET", "/rest/v1/users?id=eq.$userId&select=username", token))
        return if (result.length() == 0) "" else result.getJSONObject(0).stringOrEmpty("username")
    }

    fun getDisplayName(token: String, userId: String): String {
        val result = JSONArray(request("GET", "/rest/v1/users?id=eq.$userId&select=display_name", token))
        return if (result.length() == 0) "" else result.getJSONObject(0).stringOrEmpty("display_name")
    }

    fun getAvatarUrl(token: String, userId: String): String {
        val result = JSONArray(request("GET", "/rest/v1/users?id=eq.$userId&select=avatar_url", token))
        return if (result.length() == 0) "" else result.getJSONObject(0).stringOrEmpty("avatar_url")
    }

    fun getAvatarUrls(token: String, userIds: List<String>): Map<String, String> = try {
        userIds.filter { it.isNotBlank() }.distinct().chunked(100).flatMap { ids ->
            rows("/rest/v1/users?id=in.(${ids.joinToString(",")})&select=id,avatar_url&order=id", token)
        }.associate { it.getString("id") to it.stringOrEmpty("avatar_url") }
    } catch (error: Exception) {
        if (error is SessionExpiredException || (error is ApiException && error.statusCode == 401)) throw error
        android.util.Log.w("PicGram", "No se pudieron cargar las miniaturas de perfil", error)
        emptyMap()
    }

    fun updateAvatarUrl(token: String, userId: String, avatarUrl: String) {
        val result = JSONArray(request("PATCH", "/rest/v1/users?id=eq.$userId", token,
            JSONObject().put("avatar_url", avatarUrl), "return=representation"))
        check(result.length() == 1) { "No se pudo actualizar tu perfil. Verifica sus permisos." }
    }

    fun getPosts(token: String, currentUserId: String): List<Post> {
        val following = getFollowingIds(token, currentUserId)
        if (following.isEmpty()) return emptyList()
        val posts = following.chunked(100).flatMap { ids ->
            rows("/rest/v1/posts?user_id=in.(${ids.joinToString(",")})&select=*&order=timestamp.desc,id.desc", token).map(::post)
        }.sortedWith(compareByDescending<Post> { it.timestamp }.thenByDescending { it.id })
        if (posts.isEmpty()) return posts
        val likes = posts.map { it.id }.chunked(100).flatMap { ids ->
            rows("/rest/v1/likes?post_id=in.(${ids.joinToString(",")})&select=post_id,user_id&order=post_id,user_id", token)
        }
        val comments = posts.map { it.id }.chunked(100).flatMap { ids ->
            rows("/rest/v1/comments?post_id=in.(${ids.joinToString(",")})&select=id,post_id&order=id", token)
        }
        val avatars = posts.map { it.userId }.distinct().chunked(100).flatMap { ids ->
            rows("/rest/v1/users?id=in.(${ids.joinToString(",")})&select=id,avatar_url&order=id", token)
        }.associate { it.getString("id") to it.stringOrEmpty("avatar_url") }
        val likeCounts = likes.groupingBy { it.getString("post_id") }.eachCount()
        val commentCounts = comments.groupingBy { it.getString("post_id") }.eachCount()
        val likedIds = likes.filter { it.getString("user_id") == currentUserId }.map { it.getString("post_id") }.toSet()
        return posts.map { it.copy(likesCount = likeCounts[it.id] ?: 0,
            commentsCount = commentCounts[it.id] ?: 0, likedByMe = it.id in likedIds,
            avatarUrl = avatars[it.userId].orEmpty()) }
    }

    fun insertPost(token: String, userId: String, userEmail: String, username: String,
                   caption: String, mediaUrl: String, mediaType: String, timestamp: Long, id: String) {
        require(mediaUrl.isNotBlank() && mediaType in listOf("image", "video")) {
            "La publicación debe incluir una imagen o un video"
        }
        val body = JSONObject().put("id", id).put("user_id", userId).put("user_email", userEmail)
            .put("username", username).put("caption", caption).put("media_url", mediaUrl)
            .put("media_type", mediaType).put("timestamp", timestamp)
        request("POST", "/rest/v1/posts?on_conflict=id", token, body,
            "resolution=ignore-duplicates,return=minimal")
        val stored = JSONArray(request("GET", "/rest/v1/posts?id=eq.$id&select=id,user_id", token))
        check(stored.length() == 1 && stored.getJSONObject(0).getString("user_id") == userId) {
            "No se pudo confirmar la publicacion"
        }
    }

    fun deletePost(token: String, postId: String) {
        val deleted = JSONArray(request("DELETE", "/rest/v1/posts?id=eq.$postId", token, prefer = "return=representation"))
        check(deleted.length() == 1) { "La publicacion no existe o no tienes permiso para borrarla" }
    }

    fun getLikesForPost(token: String, postId: String, currentUserId: String): Pair<Int, Boolean> {
        val likes = rows("/rest/v1/likes?post_id=eq.$postId&select=user_id&order=user_id", token)
        return likes.size to likes.any { it.getString("user_id") == currentUserId }
    }

    fun toggleLike(token: String, postId: String, userId: String, currentlyLiked: Boolean) {
        if (currentlyLiked) request("DELETE", "/rest/v1/likes?post_id=eq.$postId&user_id=eq.$userId", token)
        else request("POST", "/rest/v1/likes?on_conflict=post_id,user_id", token,
            JSONObject().put("post_id", postId).put("user_id", userId),
            "resolution=ignore-duplicates,return=minimal")
    }

    fun addComment(token: String, postId: String, userId: String,
                   username: String, content: String, timestamp: Long, id: String): Comment {
        val body = JSONObject().put("id", id).put("post_id", postId).put("user_id", userId)
            .put("username", username).put("content", content).put("timestamp", timestamp)
        val result = JSONArray(request("POST", "/rest/v1/comments?on_conflict=id", token, body,
            "resolution=ignore-duplicates,return=representation"))
        val stored = if (result.length() > 0) result else
            JSONArray(request("GET", "/rest/v1/comments?id=eq.$id&select=*", token))
        val created = comment(stored.getJSONObject(0))
        check(created.userId == userId && created.postId == postId) { "No se pudo confirmar el comentario" }
        return created
    }

    fun getComments(token: String, postId: String): List<Comment> {
        val comments = rows("/rest/v1/comments?post_id=eq.$postId&select=*&order=timestamp.asc,id.asc", token).map(::comment)
        val avatars = getAvatarUrls(token, comments.map { it.userId })
        return comments.map { it.copy(avatarUrl = avatars[it.userId].orEmpty()) }
    }

    fun deleteComment(token: String, commentId: String) {
        val deleted = JSONArray(request("DELETE", "/rest/v1/comments?id=eq.$commentId", token, prefer = "return=representation"))
        check(deleted.length() == 1) { "El comentario no existe o no tienes permiso para borrarlo" }
    }

    fun searchUsers(token: String, query: String): List<UserProfile> {
        val term = ProfileNames.searchTerm(query)
        if (term.isBlank()) return emptyList()
        require(term.length <= ProfileNames.MAX_LENGTH) { "La búsqueda admite hasta 80 caracteres." }
        // JSON parameters, not interpolated PostgREST filters: punctuation is literal.
        val result = JSONArray(request("POST", "/rest/v1/rpc/picgram_search_users", token,
            JSONObject().put("query_text", term)))
        return (0 until result.length()).map { index ->
            val obj = result.getJSONObject(index)
            UserProfile(id = obj.getString("id"), username = obj.stringOrEmpty("username"),
                avatarUrl = obj.stringOrEmpty("avatar_url"), displayName = obj.stringOrEmpty("display_name"))
        }
    }

    fun getUserPosts(token: String, userId: String): List<Post> =
        rows("/rest/v1/posts?user_id=eq.$userId&select=*&order=timestamp.desc,id.desc", token).map(::post)

    private fun getFollowingIds(token: String, userId: String): List<String> =
        rows("/rest/v1/follows?follower_id=eq.$userId&select=following_id&order=following_id", token).map { it.getString("following_id") }

    fun getConnections(token: String, userId: String, type: ConnectionType): List<UserProfile> {
        val ids = rows("/rest/v1/follows?${type.ownerColumn}=eq.$userId&select=${type.userColumn}&order=${type.userColumn}", token)
            .map { it.getString(type.userColumn) }.distinct()
        return ids.chunked(100).flatMap { batch ->
            rows("/rest/v1/users?id=in.(${batch.joinToString(",")})&select=id,username,avatar_url&order=username,id", token)
                .map { row -> UserProfile(id = row.getString("id"), username = row.stringOrEmpty("username"),
                    avatarUrl = row.stringOrEmpty("avatar_url")) }
        }.sortedWith(compareBy<UserProfile> { it.username.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
    }

    fun isFollowing(token: String, followerId: String, followingId: String): Boolean =
        JSONArray(request("GET", "/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId&select=id", token)).length() > 0

    fun followUser(token: String, followerId: String, followingId: String) {
        require(followerId != followingId) { "No puedes seguirte a ti mismo" }
        request("POST", "/rest/v1/follows?on_conflict=follower_id,following_id", token,
            JSONObject().put("follower_id", followerId).put("following_id", followingId),
            "resolution=ignore-duplicates,return=minimal")
    }

    fun unfollowUser(token: String, followerId: String, followingId: String) {
        request("DELETE", "/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId", token)
    }

    fun getFollowersCount(token: String, userId: String): Int =
        rows("/rest/v1/follows?following_id=eq.$userId&select=id&order=id", token).size

    fun getFollowingCount(token: String, userId: String): Int =
        rows("/rest/v1/follows?follower_id=eq.$userId&select=id&order=id", token).size
}
