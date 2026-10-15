package com.example.picgram.ui

import com.example.picgram.presentation.*

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.picgram.LoginActivity
import com.example.picgram.MainActivity
import com.example.picgram.data.AppContainer
import com.example.picgram.data.PicGramApplication

fun ComponentActivity.currentUserSession() = (application as PicGramApplication).container.session.currentSession()

inline fun <reified T : ViewModel> ComponentActivity.screenViewModel(crossinline create: (AppContainer) -> T): T =
    ViewModelProvider(this, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <M : ViewModel> create(modelClass: Class<M>): M =
            create((application as PicGramApplication).container) as M
    })[T::class.java]

@Composable
fun ComponentActivity.ObserveNotices(model: ScreenViewModel) {
    val notices by model.notices.collectAsStateWithLifecycle()
    val notice = notices.firstOrNull()
    LaunchedEffect(notice?.id) {
        notice ?: return@LaunchedEffect
        notice.message?.let { Toast.makeText(this@ObserveNotices, it, Toast.LENGTH_LONG).show() }
        model.acknowledge(notice.id)
        when (notice.destination) {
            Destination.CLOSE -> finish()
            Destination.LOGIN, Destination.FEED -> {
                val target = if (notice.destination == Destination.LOGIN) LoginActivity::class.java else MainActivity::class.java
                startActivity(Intent(this@ObserveNotices, target).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            null -> Unit
        }
    }
}
