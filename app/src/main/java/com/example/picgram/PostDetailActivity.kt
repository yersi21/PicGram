package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.picgram.ui.theme.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class PostDetailActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { PostDetailViewModel(it) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = currentUserSession() ?: run { finish(); return }
        val post = Post(id = intent.getStringExtra("post_id").orEmpty(), userId = intent.getStringExtra("post_user_id").orEmpty(),
            username = intent.getStringExtra("post_username").orEmpty(), caption = intent.getStringExtra("post_caption").orEmpty(),
            mediaUrl = intent.getStringExtra("post_media_url").orEmpty(), mediaType = intent.getStringExtra("post_media_type") ?: "image",
            timestamp = intent.getLongExtra("post_timestamp",0L), avatarUrl = intent.getStringExtra("post_avatar_url").orEmpty())
        val postId = post.id
        val isOwner = session.userId == post.userId
        val restored = savedInstanceState?.getString("pending_comment_id")?.let { id -> Comment(id = id,
            userId = savedInstanceState.getString("pending_comment_user").orEmpty(),
            postId = savedInstanceState.getString("pending_comment_post").orEmpty(), username = session.username,
            content = savedInstanceState.getString("pending_comment_text").orEmpty(), timestamp = savedInstanceState.getLong("pending_comment_timestamp")) }
        model.initialize(post, savedInstanceState?.getString("comment_text").orEmpty(), restored)
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PostDetailScreen(
                        post = post.copy(avatarUrl = state.authorAvatarUrl),
                        isOwner = isOwner,
                        currentUserId = session.userId,
                        likesCount = state.likesCount,
                        likedByMe = state.likedByMe,
                        comments = state.comments,
                        isLoadingDetail = state.isLoadingDetail,
                        isDeleting = state.isDeleting,
                        commentText = state.commentText,
                        isSubmittingComment = state.isSubmittingComment,
                        hasPendingComment = state.pendingComment != null,
                        scrollToBottom = state.scrollToBottom,
                        onCommentTextChange = model::editComment,
                        onBackClicked = { finish() },
                        onLikeClicked = { model.handleLike(session.accessToken, postId, session.userId) },
                        onDoubleTapLike = { model.handleLike(session.accessToken, postId, session.userId, addOnly = true) },
                        onDeletePost = { model.handleDeletePost(session, post) },
                        onSendComment = { model.handleAddComment(session, postId) },
                        onOpenProfile = { userId, username -> openUserProfile(userId, username) },
                        onDeleteComment = { comment -> model.handleDeleteComment(session.accessToken, comment) }
                    )
                }
            }
        }
    }
    override fun onResume() { super.onResume(); model.refreshAvatars() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("comment_text", model.uiState.value.commentText)
        model.uiState.value.pendingComment?.let {
            outState.putString("pending_comment_id", it.id)
            outState.putString("pending_comment_user", it.userId)
            outState.putString("pending_comment_post", it.postId)
            outState.putString("pending_comment_text", it.content)
            outState.putLong("pending_comment_timestamp", it.timestamp)
        }
        super.onSaveInstanceState(outState)
    }
}

// ─── Composables ──────────────────────────────────────────────────────────────
