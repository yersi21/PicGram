@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.picgram

import android.view.LayoutInflater
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun VideoPlayer(url: String, modifier: Modifier = Modifier, playbackEnabled: Boolean = true,
    onDoubleTap: (() -> Unit)? = null) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val previewState by rememberVideoPreview(url, playbackEnabled)
    val preview = previewState.preview
    var decodedRatio by remember(url) { mutableStateOf<Float?>(null) }
    var firstFrame by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf(false) }
    var fullscreen by rememberSaveable(url) { mutableStateOf(false) }
    var savedPosition by rememberSaveable(url) { mutableLongStateOf(0L) }
    val player = remember(context, url) { ExoPlayer.Builder(context.applicationContext).build() }
    fun newView(): PlayerView = (LayoutInflater.from(context).inflate(R.layout.view_video_player, null) as PlayerView).apply {
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setKeepContentOnPlayerReset(true)
        setShowNextButton(false)
        setShowPreviousButton(false)
        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
    }
    val inlineView = remember(context, url) { newView() }
    val fullView = remember(context, url) { newView() }
    val currentPlaybackEnabled by rememberUpdatedState(playbackEnabled)
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)

    // Attach to the player, not its playback buttons: child controls keep their own touches.
    DisposableEffect(inlineView, fullView, onDoubleTap != null) {
        val detectors = listOf(inlineView, fullView).map { view ->
            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (currentPlaybackEnabled) view.performClick()
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (currentPlaybackEnabled) currentDoubleTap?.invoke()
                    return true
                }
                override fun onDoubleTapEvent(e: MotionEvent): Boolean = true
            }).apply { setIsLongpressEnabled(false) }
            if (onDoubleTap != null) view.setOnTouchListener { _, event -> detector.onTouchEvent(event); true }
            view to detector
        }
        onDispose {
            detectors.forEach { (view, detector) ->
                val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                detector.onTouchEvent(cancel)
                cancel.recycle()
                view.setOnTouchListener(null)
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                decodedRatio = videoAspectRatio(size.width, size.height, size.pixelWidthHeightRatio)
            }
            override fun onRenderedFirstFrame() { firstFrame = true; error = false }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                savedPosition = newPosition.positionMs
            }
            override fun onPlayerError(failure: PlaybackException) { error = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                inlineView.keepScreenOn = isPlaying
                fullView.keepScreenOn = isPlaying
            }
        }
        player.addListener(listener)
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.playWhenReady = false
        player.setMediaItem(MediaItem.fromUri(url), savedPosition)
        if (currentPlaybackEnabled) player.prepare()
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                savedPosition = player.currentPosition
                player.pause()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose {
            savedPosition = player.currentPosition
            lifecycle?.removeObserver(observer)
            inlineView.player = null
            fullView.player = null
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(playbackEnabled) {
        if (!playbackEnabled) player.pause()
        else if (player.playbackState == Player.STATE_IDLE && !error) player.prepare()
    }
    // Save during playback as well: Activity state can be captured before onDispose on rotation.
    LaunchedEffect(player) {
        while (isActive) {
            delay(500)
            if (player.isPlaying) savedPosition = player.currentPosition
        }
    }
    DisposableEffect(player, fullscreen) {
        val active = if (fullscreen) fullView else inlineView
        val inactive = if (fullscreen) inlineView else fullView
        PlayerView.switchTargetView(player, inactive, active)
        onDispose { active.player = null }
    }

    val ratio = decodedRatio ?: preview?.aspectRatio ?: videoAspectRatio(16, 9)
    val maxHeight = LocalConfiguration.current.screenHeightDp * 0.65f
    val retryPlayback = { error = false; firstFrame = false; player.prepare() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = videoViewportHeight(maxWidth.value, maxHeight, ratio).dp
        Box(Modifier.fillMaxWidth().height(height).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(factory = { inlineView }, modifier = Modifier.matchParentSize(), update = {
                it.useController = playbackEnabled
                it.setFullscreenButtonState(fullscreen)
                it.setFullscreenButtonClickListener { _ -> keyboard?.hide(); fullscreen = true }
            })
            if (!firstFrame && !error) preview?.let {
                Image(it.bitmap.asImageBitmap(), "Vista previa del video", Modifier.matchParentSize(), contentScale = ContentScale.Fit)
            }
            if (error) VideoPlaybackError(enabled = playbackEnabled, onRetry = retryPlayback)
        }
    }
    if (fullscreen) Dialog(onDismissRequest = { fullscreen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            AndroidView(factory = { fullView }, modifier = Modifier.fillMaxSize(), update = {
                it.useController = playbackEnabled
                it.setFullscreenButtonState(true)
                it.setFullscreenButtonClickListener { _ -> fullscreen = false }
            })
            if (error) VideoPlaybackError(Modifier.align(Alignment.Center), playbackEnabled, retryPlayback)
            IconButton(onClick = { fullscreen = false }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salir de pantalla completa", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoPlaybackError(modifier: Modifier = Modifier, enabled: Boolean, onRetry: () -> Unit) {
    Column(modifier.padding(12.dp)
        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No se pudo reproducir el video. Comprueba la conexión o el formato.", color = Color.White)
        TextButton(onClick = onRetry, enabled = enabled) { Text("Reintentar", color = Color.White) }
    }
}
