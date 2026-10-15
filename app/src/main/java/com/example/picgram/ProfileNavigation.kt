package com.example.picgram

import android.app.Activity
import android.content.Intent

fun Activity.openUserProfile(userId: String, username: String) {
    if (userId.isBlank()) return
    startActivity(Intent(this, UserProfileActivity::class.java).apply {
        putExtra("user_id", userId)
        putExtra("username", username.ifBlank { "Usuario" })
    })
}
