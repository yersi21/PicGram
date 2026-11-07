package com.example.picgram.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.picgram.ui.screens.FeedScreen
import com.example.picgram.ui.screens.LoginScreen
import com.example.picgram.Post
import com.example.picgram.data.model.Story
import com.example.picgram.data.model.monotonicMillis
import com.example.picgram.ui.FeedNavigation
import com.example.picgram.ui.FeedNotificationAction
import com.example.picgram.ui.screens.StoriesStrip

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
    FeedPreviewContent(true)
}

@Preview(name = "Feed claro", widthDp = 360, heightDp = 800)
@Composable
private fun FeedLightPreview() { FeedPreviewContent(false) }

@Composable
private fun FeedPreviewContent(dark: Boolean) {
    PicGramTheme(darkTheme = dark) {
        FeedScreen(
            isDarkMode = dark, onDarkModeChanged = {},
            storiesHeader = { StoriesStrip(listOf(
                Story("1", "ana", "", "image", monotonicMillis() + 60_000, "ana"),
                Story("2", "luis", "", "image", monotonicMillis() + 60_000, "luis")), "me", "yo", "",
                pendingLikes = emptySet(), replies = emptyMap(), onLike = {}, onReplyChanged = { _, _ -> },
                onReply = {}, viewers = emptyMap(), onStoryOpened = {}, onLoadViewers = {},
                onViewerProfile = { _, _ -> }, onChat = { _, _ -> }, onCreate = {}) },
            communityActions = { FeedNavigation(3L, "yo", "", {}, {}, {}, {}) },
            notificationAction = { FeedNotificationAction(2L, {}) },
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
