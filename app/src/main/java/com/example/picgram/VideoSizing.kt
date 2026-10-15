package com.example.picgram

private const val DEFAULT_VIDEO_RATIO = 16f / 9f

fun videoAspectRatio(width: Int, height: Int, pixelRatio: Float = 1f, rotation: Int = 0): Float {
    if (width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0f) return DEFAULT_VIDEO_RATIO
    val ratio = width.toFloat() * pixelRatio / height
    if (!ratio.isFinite() || ratio <= 0f) return DEFAULT_VIDEO_RATIO
    val normalizedRotation = ((rotation % 360) + 360) % 360
    return if (normalizedRotation == 90 || normalizedRotation == 270) 1f / ratio else ratio
}

/** Cap the viewport height, not the video's aspect ratio. FIT adds side bars when needed. */
fun videoViewportHeight(width: Float, maxHeight: Float, ratio: Float): Float {
    if (!width.isFinite() || !maxHeight.isFinite() || width <= 0 || maxHeight <= 0) return 0f
    val safeRatio = ratio.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_VIDEO_RATIO
    return (width / safeRatio).coerceAtMost(maxHeight)
}
