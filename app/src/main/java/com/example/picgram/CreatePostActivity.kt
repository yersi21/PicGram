package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
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

class CreatePostActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { CreatePostViewModel(it) } }
    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { model.selectMedia(it) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val restoredPost = savedInstanceState?.getString("pending_id")?.let { id -> Post(id = id,
            caption = savedInstanceState.getString("pending_caption").orEmpty(), mediaUrl = savedInstanceState.getString("pending_url").orEmpty(),
            mediaType = savedInstanceState.getString("pending_type") ?: "image", userId = savedInstanceState.getString("pending_owner").orEmpty(),
            timestamp = savedInstanceState.getLong("pending_timestamp")) }
        model.initialize(savedInstanceState?.getString("selected_uri")?.let(Uri::parse), savedInstanceState?.getString("media_type") ?: "image", restoredPost)
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CreatePostScreen(
                        selectedMediaUri = state.selectedMediaUri,
                        isLoading = state.isLoading,
                        loadingMessage = state.loadingMessage,
                        hasPendingPost = state.pendingPost != null,
                        initialCaption = state.pendingPost?.caption ?: state.restoredCaption,
                        previewUrl = state.pendingPost?.mediaUrl?.takeIf { it.isNotBlank() } ?: state.selectedMediaUri?.toString().orEmpty(),
                        previewType = state.pendingPost?.mediaType ?: state.mediaType,
                        onSelectMediaClicked = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onSubmitPost = { caption -> model.startUpload(caption) },
                        onBackClicked = { finish() }
                    )
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_uri", model.uiState.value.selectedMediaUri?.toString())
        outState.putString("media_type", model.uiState.value.mediaType)
        model.uiState.value.pendingPost?.let { post ->
            outState.putString("pending_id", post.id)
            outState.putString("pending_caption", post.caption)
            outState.putString("pending_url", post.mediaUrl)
            outState.putString("pending_type", post.mediaType)
            outState.putString("pending_owner", post.userId)
            outState.putLong("pending_timestamp", post.timestamp)
        }
        super.onSaveInstanceState(outState)
    }
}
