package com.example.picgram

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.picgram.presentation.*
import com.example.picgram.ui.*
import com.example.picgram.ui.screens.CommunityScreen
import com.example.picgram.ui.theme.PicGramTheme
import com.example.picgram.data.PushManager
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

fun ComponentActivity.openCommunity(page: CommunityPage, peer: String = "", username: String = "") {
    startActivity(Intent(this, CommunityActivity::class.java).putExtra("page", page.name)
        .putExtra("peer", peer).putExtra("username", username))
}
class CommunityActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { CommunityViewModel(it) } }
    private val avatar = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(model::uploadAvatar) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = currentUserSession() ?: run { finish(); return }
        val page = runCatching { CommunityPage.valueOf(intent.getStringExtra("page").orEmpty()) }.getOrDefault(CommunityPage.CONVERSATIONS)
        val peer = intent.getStringExtra("peer").orEmpty()
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.start(page, peer)
                try { awaitCancellation() } finally { model.stop() }
            }
        }
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.postToOpen) {
                state.postToOpen?.let { post ->
                    startActivity(Intent(this@CommunityActivity, PostDetailActivity::class.java).apply {
                        putExtra("post_id", post.id)
                        putExtra("post_user_id", post.userId)
                        putExtra("post_username", post.username)
                        putExtra("post_caption", post.caption)
                        putExtra("post_media_url", post.mediaUrl)
                        putExtra("post_media_type", post.mediaType)
                        putExtra("post_timestamp", post.timestamp)
                        putExtra("post_avatar_url", post.avatarUrl)
                    })
                    model.consumePostNavigation()
                }
            }
            PicGramTheme {
                CommunityScreen(page, state, session.userId,
                    if (page == CommunityPage.CONVERSATIONS) session.username else intent.getStringExtra("username").orEmpty(),
                    onNewMessage = { startActivity(Intent(this, SearchActivity::class.java).putExtra("select_message_recipient", true)) },
                    pushConfigured = PushManager.configured,
                    onPushSettings = { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)) },
                    onBack = { finish() }, onRefresh = model::refresh, onSave = model::saveProfile,
                    onPhoto = { avatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPublish = model::publishStory, onSend = model::sendMessage, onOlder = model::loadOlder,
                    onChat = { id, name -> openCommunity(CommunityPage.CHAT, id, name) },
                    onProfile = { id, name -> openUserProfile(id, name) }, onResolve = model::resolveRequest,
                    onRead = model::markNotification, onNotificationPost = model::openNotificationPost)
            }
        }
    }
    override fun onResume() { super.onResume(); PushManager.register(this) }
}
