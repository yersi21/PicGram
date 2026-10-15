package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.picgram.ui.theme.*
import java.util.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { FeedViewModel(it) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = currentUserSession() ?: run {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        enableEdgeToEdge()
        model.initialize()
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                FeedScreen(
                    posts = state.posts,
                    isLoadingFeed = state.isLoadingFeed,
                    errorMessage = state.errorMessage,
                    currentUserId = session.userId,
                    currentUsername = session.username.ifBlank { session.email },
                    currentAvatarUrl = state.currentAvatarUrl,
                    onCreatePostClicked = { startActivity(Intent(this, CreatePostActivity::class.java)) },
                    onLogoutClicked = { model.handleLogout(session.accessToken) },
                    onRefreshClicked = { model.loadPosts(); model.loadCurrentAvatar() },
                    onDeletePost = { post -> model.handleDeletePost(post) },
                    onLikePost = { post, addOnly -> model.handleLike(post, addOnly) },
                    onOpenProfile = { userId, username -> openUserProfile(userId, username) },
                    onSearchClicked = { startActivity(Intent(this, SearchActivity::class.java)) },
                    onProfileClicked = {
                        startActivity(Intent(this, UserProfileActivity::class.java).apply {
                            putExtra("user_id",  session.userId)
                            putExtra("username", session.username.ifBlank { session.email })
                        })
                    },
                    onOpenPost = { post ->
                        val intent = Intent(this, PostDetailActivity::class.java).apply {
                            putExtra("post_id",        post.id)
                            putExtra("post_user_id",   post.userId)
                            putExtra("post_username",  post.username)
                            putExtra("post_caption",   post.caption)
                            putExtra("post_media_url", post.mediaUrl)
                            putExtra("post_media_type",post.mediaType)
                            putExtra("post_timestamp", post.timestamp)
                            putExtra("post_avatar_url", post.avatarUrl)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
    override fun onResume() { super.onResume(); model.refresh() }
}

// ─── Composables ─────────────────────────────────────────────────────────────
