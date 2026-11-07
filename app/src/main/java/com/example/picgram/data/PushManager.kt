package com.example.picgram.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.picgram.*
import com.example.picgram.presentation.CommunityPage
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*

object PushManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val configured get() = listOf(BuildConfig.FIREBASE_APPLICATION_ID, BuildConfig.FIREBASE_API_KEY,
        BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID).all { it.isNotBlank() }
    fun initialize(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("picgram_activity", "Actividad de PicGram", NotificationManager.IMPORTANCE_DEFAULT))
        if (configured && FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY).setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
        }
    }
    fun permissionAsked(context: Context): Boolean {
        val prefs = context.getSharedPreferences("picgram_push", Context.MODE_PRIVATE)
        val asked = prefs.getBoolean("permission_asked", false)
        prefs.edit().putBoolean("permission_asked", true).apply()
        return asked
    }
    fun register(context: Context) {
        if (!configured) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> registerToken(context.applicationContext, token) }
    }
    fun detach(context: Context) {
        val container = (context.applicationContext as PicGramApplication).container
        val session = container.session.currentSession() ?: return
        val token = context.getSharedPreferences("picgram_push", Context.MODE_PRIVATE).getString("token", null) ?: return
        scope.launch { runCatching { container.community.removeDevice(session.accessToken, token) } }
    }
    fun registerToken(context: Context, token: String) {
        context.getSharedPreferences("picgram_push", Context.MODE_PRIVATE).edit().putString("token", token).apply()
        val container = (context.applicationContext as PicGramApplication).container
        val session = container.session.currentSession() ?: return
        scope.launch { try {
            if (container.session.currentSession()?.userId == session.userId) container.community.registerDevice(session.accessToken, token)
        } catch (error: Exception) { android.util.Log.w("PicGram", "Registro push pendiente", error) } }
    }
    fun show(context: Context, data: Map<String, String>) {
        val session = SessionManager.currentSession(context) ?: return
        if (data["user_id"] != session.userId) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return
        val id = data["notification_id"] ?: return
        val intent = Intent(context, CommunityActivity::class.java).putExtra("page", CommunityPage.NOTIFICATIONS.name)
        val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), NotificationCompat.Builder(context, "picgram_activity")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("PicGram")
                .setContentText(when (data["kind"]) {
                    "story_like" -> "A alguien le gusta tu historia"
                    "message" -> "Has recibido un mensaje nuevo"
                    else -> "Tienes nueva actividad en tu cuenta"
                }).setContentIntent(pending).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).build())
        } catch (_: SecurityException) { /* User revoked notifications while delivering. */ }
    }
}
class PicGramMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushManager.registerToken(applicationContext, token) }
    override fun onMessageReceived(message: RemoteMessage) { PushManager.show(applicationContext, message.data) }
}
