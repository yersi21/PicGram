package com.example.picgram.presentation

import androidx.lifecycle.ViewModel
import com.example.picgram.*
import com.example.picgram.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Destination { LOGIN, FEED, CLOSE }
data class UiNotice(val id: Long, val message: String? = null, val destination: Destination? = null)

/** Pending UI results are durable state: rotation cannot drop a navigation/error result. */
abstract class ScreenViewModel(protected val repositories: AppContainer) : ViewModel() {
    private var nextNoticeId = 0L
    private val pendingNotices = MutableStateFlow<List<UiNotice>>(emptyList())
    val notices = pendingNotices.asStateFlow()
    protected fun notify(message: String) {
        pendingNotices.update { it + UiNotice(++nextNoticeId, message) }
    }
    protected fun navigate(destination: Destination) {
        pendingNotices.update { it + UiNotice(++nextNoticeId, destination = destination) }
    }
    fun acknowledge(id: Long) { pendingNotices.update { list -> list.filterNot { it.id == id } } }
    protected fun handleSessionError(error: Exception): Boolean {
        if (error !is SessionExpiredException && !(error is ApiException && error.statusCode == 401)) return false
        repositories.session.clearSession()
        notify("Tu sesión ha expirado. Inicia sesión de nuevo.")
        navigate(Destination.LOGIN)
        return true
    }
}
