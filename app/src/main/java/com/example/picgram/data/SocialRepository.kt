package com.example.picgram.data

import com.example.picgram.*
import kotlinx.coroutines.*

interface SocialRepository {
    suspend fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long): Unit
    suspend fun getUsername(token: String, userId: String): String
    suspend fun getAvatarUrl(token: String, userId: String): String
    suspend fun getAvatarUrls(token: String, userIds: List<String>): Map<String, String>
    suspend fun updateAvatarUrl(token: String, userId: String, avatarUrl: String): Unit
    suspend fun getPosts(token: String, currentUserId: String): List<Post>
    suspend fun insertPost(token: String, userId: String, userEmail: String, username: String, caption: String, mediaUrl: String, mediaType: String, timestamp: Long, id: String): Unit
    suspend fun getLikesForPost(token: String, postId: String, currentUserId: String): Pair<Int, Boolean>
    suspend fun toggleLike(token: String, postId: String, userId: String, currentlyLiked: Boolean): Unit
    suspend fun addComment(token: String, postId: String, userId: String, username: String, content: String, timestamp: Long, id: String): Comment
    suspend fun getComments(token: String, postId: String): List<Comment>
    suspend fun deleteComment(token: String, commentId: String): Unit
    suspend fun searchUsers(token: String, query: String): List<UserProfile>
    suspend fun getUserPosts(token: String, userId: String): List<Post>
    suspend fun getConnections(token: String, userId: String, type: ConnectionType): List<UserProfile>
    suspend fun isFollowing(token: String, followerId: String, followingId: String): Boolean
    suspend fun followUser(token: String, followerId: String, followingId: String): Unit
    suspend fun unfollowUser(token: String, followerId: String, followingId: String): Unit
    suspend fun getFollowersCount(token: String, userId: String): Int
    suspend fun getFollowingCount(token: String, userId: String): Int
}

class SupabaseSocialRepository(private val io: CoroutineDispatcher = Dispatchers.IO) : SocialRepository {
    override suspend fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long): Unit = withContext(io) { SupabaseDatabase.insertUser(token, id, email, username, createdAt) }
    override suspend fun getUsername(token: String, userId: String): String = withContext(io) { SupabaseDatabase.getUsername(token, userId) }
    override suspend fun getAvatarUrl(token: String, userId: String): String = withContext(io) { SupabaseDatabase.getAvatarUrl(token, userId) }
    override suspend fun getAvatarUrls(token: String, userIds: List<String>): Map<String, String> = withContext(io) { SupabaseDatabase.getAvatarUrls(token, userIds) }
    override suspend fun updateAvatarUrl(token: String, userId: String, avatarUrl: String): Unit = withContext(io) { SupabaseDatabase.updateAvatarUrl(token, userId, avatarUrl) }
    override suspend fun getPosts(token: String, currentUserId: String): List<Post> = withContext(io) { SupabaseDatabase.getPosts(token, currentUserId) }
    override suspend fun insertPost(token: String, userId: String, userEmail: String, username: String, caption: String, mediaUrl: String, mediaType: String, timestamp: Long, id: String): Unit = withContext(io) { SupabaseDatabase.insertPost(token, userId, userEmail, username, caption, mediaUrl, mediaType, timestamp, id) }
    override suspend fun getLikesForPost(token: String, postId: String, currentUserId: String): Pair<Int, Boolean> = withContext(io) { SupabaseDatabase.getLikesForPost(token, postId, currentUserId) }
    override suspend fun toggleLike(token: String, postId: String, userId: String, currentlyLiked: Boolean): Unit = withContext(io) { SupabaseDatabase.toggleLike(token, postId, userId, currentlyLiked) }
    override suspend fun addComment(token: String, postId: String, userId: String, username: String, content: String, timestamp: Long, id: String): Comment = withContext(io) { SupabaseDatabase.addComment(token, postId, userId, username, content, timestamp, id) }
    override suspend fun getComments(token: String, postId: String): List<Comment> = withContext(io) { SupabaseDatabase.getComments(token, postId) }
    override suspend fun deleteComment(token: String, commentId: String): Unit = withContext(io) { SupabaseDatabase.deleteComment(token, commentId) }
    override suspend fun searchUsers(token: String, query: String): List<UserProfile> = withContext(io) { SupabaseDatabase.searchUsers(token, query) }
    override suspend fun getUserPosts(token: String, userId: String): List<Post> = withContext(io) { SupabaseDatabase.getUserPosts(token, userId) }
    override suspend fun getConnections(token: String, userId: String, type: ConnectionType): List<UserProfile> = withContext(io) { SupabaseDatabase.getConnections(token, userId, type) }
    override suspend fun isFollowing(token: String, followerId: String, followingId: String): Boolean = withContext(io) { SupabaseDatabase.isFollowing(token, followerId, followingId) }
    override suspend fun followUser(token: String, followerId: String, followingId: String): Unit = withContext(io) { SupabaseDatabase.followUser(token, followerId, followingId) }
    override suspend fun unfollowUser(token: String, followerId: String, followingId: String): Unit = withContext(io) { SupabaseDatabase.unfollowUser(token, followerId, followingId) }
    override suspend fun getFollowersCount(token: String, userId: String): Int = withContext(io) { SupabaseDatabase.getFollowersCount(token, userId) }
    override suspend fun getFollowingCount(token: String, userId: String): Int = withContext(io) { SupabaseDatabase.getFollowingCount(token, userId) }
}
