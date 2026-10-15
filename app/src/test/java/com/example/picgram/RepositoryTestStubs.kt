package com.example.picgram

import android.net.Uri
import com.example.picgram.data.*

/** Strict fakes: an unexpected repository call fails instead of touching the network. */

open class StubAuthRepository : AuthRepository {
    override suspend fun signUp(email: String, password: String, username: String): AuthResult = error("Unexpected AuthRepository.signUp")
    override suspend fun signIn(email: String, password: String): AuthResult = error("Unexpected AuthRepository.signIn")
    override suspend fun requestPasswordRecovery(email: String): Unit = error("Unexpected AuthRepository.requestPasswordRecovery")
    override suspend fun verifyRecoveryLink(link: RecoveryLink): AuthResult = error("Unexpected AuthRepository.verifyRecoveryLink")
    override suspend fun updateRecoveredPassword(session: AuthResult, password: String): String = error("Unexpected AuthRepository.updateRecoveredPassword")
    override suspend fun closeRecoverySession(token: String): Unit = error("Unexpected AuthRepository.closeRecoverySession")
    override suspend fun signOut(token: String): Unit = error("Unexpected AuthRepository.signOut")
    override suspend fun validateToken(token: String): Boolean = error("Unexpected AuthRepository.validateToken")
}

open class StubSocialRepository : SocialRepository {
    override suspend fun insertUser(token: String, id: String, email: String, username: String, createdAt: Long): Unit = error("Unexpected SocialRepository.insertUser")
    override suspend fun getUsername(token: String, userId: String): String = error("Unexpected SocialRepository.getUsername")
    override suspend fun getAvatarUrl(token: String, userId: String): String = error("Unexpected SocialRepository.getAvatarUrl")
    override suspend fun getAvatarUrls(token: String, userIds: List<String>): Map<String, String> = error("Unexpected SocialRepository.getAvatarUrls")
    override suspend fun updateAvatarUrl(token: String, userId: String, avatarUrl: String): Unit = error("Unexpected SocialRepository.updateAvatarUrl")
    override suspend fun getPosts(token: String, currentUserId: String): List<Post> = error("Unexpected SocialRepository.getPosts")
    override suspend fun insertPost(token: String, userId: String, userEmail: String, username: String, caption: String, mediaUrl: String, mediaType: String, timestamp: Long, id: String): Unit = error("Unexpected SocialRepository.insertPost")
    override suspend fun getLikesForPost(token: String, postId: String, currentUserId: String): Pair<Int, Boolean> = error("Unexpected SocialRepository.getLikesForPost")
    override suspend fun toggleLike(token: String, postId: String, userId: String, currentlyLiked: Boolean): Unit = error("Unexpected SocialRepository.toggleLike")
    override suspend fun addComment(token: String, postId: String, userId: String, username: String, content: String, timestamp: Long, id: String): Comment = error("Unexpected SocialRepository.addComment")
    override suspend fun getComments(token: String, postId: String): List<Comment> = error("Unexpected SocialRepository.getComments")
    override suspend fun deleteComment(token: String, commentId: String): Unit = error("Unexpected SocialRepository.deleteComment")
    override suspend fun searchUsers(token: String, query: String): List<UserProfile> = error("Unexpected SocialRepository.searchUsers")
    override suspend fun getUserPosts(token: String, userId: String): List<Post> = error("Unexpected SocialRepository.getUserPosts")
    override suspend fun getConnections(token: String, userId: String, type: ConnectionType): List<UserProfile> = error("Unexpected SocialRepository.getConnections")
    override suspend fun isFollowing(token: String, followerId: String, followingId: String): Boolean = error("Unexpected SocialRepository.isFollowing")
    override suspend fun followUser(token: String, followerId: String, followingId: String): Unit = error("Unexpected SocialRepository.followUser")
    override suspend fun unfollowUser(token: String, followerId: String, followingId: String): Unit = error("Unexpected SocialRepository.unfollowUser")
    override suspend fun getFollowersCount(token: String, userId: String): Int = error("Unexpected SocialRepository.getFollowersCount")
    override suspend fun getFollowingCount(token: String, userId: String): Int = error("Unexpected SocialRepository.getFollowingCount")
}

open class StubMediaRepository : MediaRepository {
    override suspend fun uploadPostMedia(uri: Uri, session: UserSession): Pair<String, String> = error("Unexpected MediaRepository.uploadPostMedia")
    override suspend fun uploadProfilePhoto(uri: Uri, session: UserSession): String = error("Unexpected MediaRepository.uploadProfilePhoto")
    override fun extractPath(url: String): String? = error("Unexpected MediaRepository.extractPath")
    override suspend fun scheduleCleanup(userId: String, path: String): Unit = error("Unexpected MediaRepository.scheduleCleanup")
    override suspend fun flushCleanup(session: UserSession): Unit = error("Unexpected MediaRepository.flushCleanup")
    override suspend fun deletePost(session: UserSession, post: Post): Unit = error("Unexpected MediaRepository.deletePost")
    override fun mimeType(uri: Uri): String? = error("Unexpected MediaRepository.mimeType")
}
