package com.example.picgram.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.picgram.*
import com.example.picgram.presentation.CommunityPage

/** Shared bottom navigation for every authenticated secondary screen. */
@Composable
fun ComponentActivity.AppNavigationShell(avatarUrl: String = "", unreadMessages: Long = 0, content: @Composable () -> Unit) {
    val session = currentUserSession()
    if (session == null) { content(); return }
    val section = when (this) {
        is SearchActivity -> "search"
        is CreatePostActivity -> "create"
        is UserProfileActivity -> if (intent.getStringExtra("user_id") == session.userId) "profile" else ""
        is CommunityActivity -> if (intent.getStringExtra("page") in listOf(CommunityPage.CHAT.name, CommunityPage.CONVERSATIONS.name)) "messages" else ""
        else -> ""
    }
    fun navigate(target: Class<out ComponentActivity>, configure: Intent.() -> Unit = {}) {
        startActivity(Intent(this, target).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (target == MainActivity::class.java) addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            configure()
        })
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars)) { content() }
        HorizontalDivider()
        FeedNavigation(unreadMessages, session.username, avatarUrl,
            onHome = { navigate(MainActivity::class.java) },
            onMessages = { if (this@AppNavigationShell !is CommunityActivity || intent.getStringExtra("page") != CommunityPage.CONVERSATIONS.name) navigate(CommunityActivity::class.java) { putExtra("page", CommunityPage.CONVERSATIONS.name) } },
            onSearch = { if (section != "search") navigate(SearchActivity::class.java) },
            onProfile = { if (section != "profile") navigate(UserProfileActivity::class.java) {
                putExtra("user_id", session.userId); putExtra("username", session.username)
            } },
            selectedSection = section)
    }
}
