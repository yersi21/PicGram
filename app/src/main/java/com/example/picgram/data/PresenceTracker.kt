package com.example.picgram.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.picgram.SessionManager
import com.example.picgram.SupabaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/** One lease per installation. Lost connections expire on the server after 60 seconds. */
class PresenceTracker(private val app: Application) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var started = 0
    private var heartbeat: Job? = null
    private val device = app.getSharedPreferences("picgram_presence", 0).let { prefs ->
        prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }
    }
    override fun onActivityStarted(activity: Activity) {
        started++
        if (started != 1) return
        heartbeat = scope.launch {
            while (isActive) { touch(true); delay(20_000) }
        }
    }
    override fun onActivityStopped(activity: Activity) {
        started--
        if (started != 0) return
        heartbeat?.cancel()
        scope.launch { touch(false) }
    }
    private suspend fun touch(online: Boolean) = lock.withLock {
        val session = SessionManager.currentSession(app) ?: return@withLock
        try {
            SupabaseDatabase.request("POST", "/rest/v1/rpc/picgram_touch_presence", session.accessToken,
                JSONObject().put("device", device).put("online", online))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            android.util.Log.w("PicGram", "Estado de conexión pendiente", error)
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
