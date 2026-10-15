package com.example.picgram

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class VideoPreview(val bitmap: Bitmap, val aspectRatio: Float)
data class VideoPreviewState(val preview: VideoPreview? = null, val loading: Boolean = false)

private object VideoPreviewCache {
    // Keep decoded posters bounded and avoid starting a decoder for every grid cell at once.
    private val permits = Semaphore(2)
    private val cache = object : LruCache<String, VideoPreview>(12 * 1024) {
        override fun sizeOf(key: String, value: VideoPreview) = (value.bitmap.byteCount / 1024).coerceAtLeast(1)
    }

    suspend fun get(context: Context, url: String): VideoPreview? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        permits.withPermit {
            cache.get(url)?.let { return@withPermit it }
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(url)
                if (uri.scheme in listOf("http", "https")) retriever.setDataSource(url, emptyMap())
                else retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val timestamp = if (duration > 0) minOf(1_000L, duration / 2) * 1_000L else 0L
                val bitmap = retriever.getScaledFrameAtTime(timestamp, MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
                    ?: return@withPermit null
                VideoPreview(bitmap, videoAspectRatio(bitmap.width, bitmap.height)).also { cache.put(url, it) }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                null // Playback can still render its own preview if this codec/provider cannot extract a poster.
            } finally { runCatching { retriever.release() } }
        }
    }
}

@Composable
fun rememberVideoPreview(url: String, enabled: Boolean = true): State<VideoPreviewState> {
    val context = LocalContext.current.applicationContext
    return produceState(VideoPreviewState(loading = enabled), url, enabled) {
        value = VideoPreviewState(loading = enabled)
        if (enabled && url.isNotBlank()) value = VideoPreviewState(VideoPreviewCache.get(context, url))
        else value = VideoPreviewState()
    }
}

@Composable
fun VideoThumbnail(url: String, modifier: Modifier = Modifier) {
    val state by rememberVideoPreview(url)
    val preview = state.preview
    Box(modifier.background(Color(0xFF101724)), contentAlignment = Alignment.Center) {
        preview?.let {
            Image(it.bitmap.asImageBitmap(), "Vista previa del video", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (preview == null && state.loading) CircularProgressIndicator(Modifier.size(48.dp), color = Color.White.copy(alpha = 0.65f))
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.58f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
