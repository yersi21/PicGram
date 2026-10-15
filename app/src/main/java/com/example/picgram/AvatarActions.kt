package com.example.picgram

/** Avatar cleanup must never remove a post's media or another user's files. */
fun isOwnedAvatarPath(userId: String, path: String): Boolean =
    userId.isNotBlank() && '/' !in userId && '\\' !in userId &&
        path.split('/').none { it == "." || it == ".." } &&
        ((path.startsWith("avatars/$userId/") && path.removePrefix("avatars/$userId/").isNotBlank()) ||
            path == "avatars/$userId.jpg")
