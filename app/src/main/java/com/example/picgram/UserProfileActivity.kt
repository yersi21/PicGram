package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.picgram.ui.theme.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class UserProfileActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { ProfileViewModel(it) } }
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.handleAvatarUpload(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val profileUserId = intent.getStringExtra("user_id").orEmpty()
        val username = intent.getStringExtra("username") ?: "Usuario"
        val session = currentUserSession() ?: run { finish(); return }
        model.initialize(profileUserId)
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UserProfileScreen(
                        displayName = state.displayName,
                        username          = username,
                        posts             = state.posts,
                        isLoading         = state.isLoading,
                        isOwnProfile      = state.isOwnProfile,
                        isFollowing       = state.isFollowing,
                        isTogglingFollow  = state.isTogglingFollow,
                        followersCount    = state.followersCount,
                        followingCount    = state.followingCount,
                        avatarUrl         = state.avatarUrl,
                        isUploadingAvatar = state.isUploadingAvatar || state.isRemovingAvatar || state.isAvatarLoading,
                        onBackClicked     = { finish() },
                        onFollowClicked   = {
                            model.handleFollowToggle(session.accessToken, session.userId, profileUserId)
                        },
                        onAvatarClicked   = {
                            if (state.isOwnProfile && !state.isUploadingAvatar && !state.isRemovingAvatar && !state.isAvatarLoading) {
                                pickAvatarLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        onDeleteAvatar = model::handleAvatarDelete,
                        onFollowersClicked = { openConnections(profileUserId, username, ConnectionType.FOLLOWERS) },
                        onFollowingClicked = { openConnections(profileUserId, username, ConnectionType.FOLLOWING) },
                        onPostClicked     = { post ->
                            startActivity(Intent(this, PostDetailActivity::class.java).apply {
                                putExtra("post_id",        post.id)
                                putExtra("post_user_id",   post.userId)
                                putExtra("post_username",  post.username)
                                putExtra("post_caption",   post.caption)
                                putExtra("post_media_url", post.mediaUrl)
                                putExtra("post_media_type",post.mediaType)
                                putExtra("post_timestamp", post.timestamp)
                                putExtra("post_avatar_url", state.avatarUrl)
                            })
                        }
                    )
                }
            }
        }
    }
    override fun onResume() { super.onResume(); model.refresh() }
}
