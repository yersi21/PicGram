package com.example.picgram.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.picgram.ui.screens.FeedScreen
import com.example.picgram.ui.screens.LoginScreen
import com.example.picgram.Post

@Preview(name = "Liquid Glass - Acceso claro", widthDp = 412, heightDp = 860)
@Composable
private fun GlassLoginLightPreview() {
    PicGramTheme(darkTheme = false) { LoginScreen(false, { _, _ -> }, {}, {}) }
}

@Preview(name = "Liquid Glass - Acceso oscuro", widthDp = 412, heightDp = 860)
@Composable
private fun GlassLoginDarkPreview() {
    PicGramTheme(darkTheme = true) { LoginScreen(false, { _, _ -> }, {}, {}) }
}

@Preview(name = "Liquid Glass - Feed oscuro", widthDp = 412, heightDp = 860)
@Composable
private fun GlassFeedPreview() {
    PicGramTheme(darkTheme = true) {
        FeedScreen(
            isDarkMode = true, onDarkModeChanged = {},
            posts = listOf(Post(id = "preview", userId = "author", username = "Mariana",
                caption = "Momentos que vale la pena compartir.", likesCount = 24, commentsCount = 3),
                Post(id = "preview-2", userId = "author-2", username = "Diego",
                    caption = "Un nuevo dia, una nueva historia.", likesCount = 12)),
            isLoadingFeed = false, errorMessage = null,
            currentUserId = "me", currentUsername = "Tu perfil", currentAvatarUrl = "",
            onCreatePostClicked = {}, onLogoutClicked = {}, onRefreshClicked = {},
            onDeletePost = {}, onLikePost = { _, _ -> }, onOpenPost = {}, onSearchClicked = {},
            onProfileClicked = {}, onOpenProfile = { _, _ -> }
        )
    }
}
