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
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import org.json.JSONArray
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
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("picgram_messages", "Mensajes directos", NotificationManager.IMPORTANCE_HIGH))
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
    fun show(context: Context, data: Map<String, String>, profilePhoto: Bitmap? = null) {
        val session = SessionManager.currentSession(context) ?: return
        if (data["user_id"] != session.userId) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return
        val id = data["notification_id"] ?: return
        val content = pushNotificationContent(data)
        val isMessage = data["kind"] == "message"
        val peer = data["actor_id"].orEmpty()
        val intent = Intent(context, CommunityActivity::class.java)
            .putExtra("page", (if (isMessage && peer.isNotBlank()) CommunityPage.CHAT else CommunityPage.NOTIFICATIONS).name)
            .putExtra("peer", peer).putExtra("username", content.title)
        val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val profile = profilePhoto ?: if (peer.isNotBlank()) notificationAvatar(content.title) else
            context.packageManager.getApplicationIcon(context.packageName).toBitmap(128, 128)
        val avatar = if (peer.isNotBlank()) brandedAvatar(context, profile) else profile
        val style = if (isMessage) {
            val sender = Person.Builder().setName(content.title)
                .setKey(peer.ifBlank { content.title }).setIcon(IconCompat.createWithBitmap(avatar)).build()
            NotificationCompat.MessagingStyle(Person.Builder().setName(session.username.ifBlank { "Tú" }).build())
                .setGroupConversation(false)
                .addMessage(data["message_preview"]?.trim()?.takeIf { it.isNotBlank() } ?: "Te envió un mensaje",
                    System.currentTimeMillis(), sender)
        } else NotificationCompat.BigTextStyle().bigText(content.body)
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), NotificationCompat.Builder(context, if (isMessage) "picgram_messages" else "picgram_activity")
                .setSmallIcon(R.drawable.ic_notification_picgram)
                .setLargeIcon(avatar).setColor(Color.rgb(217, 35, 92)).setContentTitle(content.title)
                .setSubText(if (isMessage) "Mensaje directo" else "Actividad")
                .setContentText(content.body)
                .setStyle(style)
                .setPriority(if (isMessage) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(if (isMessage) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(pending).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).build())
        } catch (_: SecurityException) { /* User revoked notifications while delivering. */ }
    }

    private fun brandedAvatar(context: Context, profile: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(profile, null, android.graphics.Rect(0, 0, 128, 128), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(106f, 106f, 22f, paint)
        // Actual launcher artwork as a colored app badge beside the sender's photo.
        val logo = context.packageManager.getApplicationIcon(context.packageName).toBitmap(36, 36)
        canvas.drawBitmap(logo, 88f, 88f, paint)
        return result
    }

    internal suspend fun loadProfilePhoto(context: Context, data: Map<String, String>): Bitmap? {
        val url = data["actor_avatar_url"]?.takeIf { it.startsWith("https://") } ?: return null
        return try {
            val result = withTimeoutOrNull(4_000) {
                context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                    .size(128, 128).allowHardware(false).transformations(CircleCropTransformation()).build())
            }
            (result?.drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) { null }
    }

    private fun notificationAvatar(name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(217, 35, 92) }
        canvas.drawCircle(64f, 64f, 64f, paint)
        paint.color = Color.WHITE
        paint.textSize = 52f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val initials = name.split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
        canvas.drawText(initials, 64f, 64f - (paint.ascent() + paint.descent()) / 2, paint)
        return bitmap
    }

    /** Older server payloads only contain an ID. Recover their content using recipient RLS. Call on IO. */
    internal fun resolveContent(context: Context, data: Map<String, String>): Map<String, String> {
        val session = SessionManager.currentSession(context) ?: return data
        if (data["user_id"] != session.userId) return data
        val id = data["notification_id"] ?: return data
        if (!Regex("[0-9a-fA-F-]{36}").matches(id)) return data
        val resolved = data.toMutableMap()
        try {
            fun row(path: String) = JSONArray(SupabaseDatabase.request("GET", path, session.accessToken)).optJSONObject(0)
            val notification = if (data["actor_id"].isNullOrBlank() || data["kind"].isNullOrBlank() ||
                (data["kind"] == "message" && data["message_preview"].isNullOrBlank()))
                row("/rest/v1/notifications?id=eq.$id&user_id=eq.${session.userId}&select=actor_id,kind,message_id&limit=1") else null
            notification?.let {
                resolved["actor_id"] = it.getString("actor_id")
                resolved["kind"] = it.getString("kind")
            }
            val actorId = resolved["actor_id"]?.takeIf { Regex("[0-9a-fA-F-]{36}").matches(it) } ?: return resolved
            // Read the same profile photo used by the app, even with an older push server.
            val actor = row("/rest/v1/users?id=eq.$actorId&select=username,avatar_url&limit=1")
            actor?.let {
                it.optString("username").takeIf { name -> name.isNotBlank() }?.let { name -> resolved["actor_name"] = name }
                resolved["actor_avatar_url"] = if (it.isNull("avatar_url")) "" else it.optString("avatar_url")
            }
            if (resolved["kind"] == "message" && notification != null && !notification.isNull("message_id")) {
                val message = row("/rest/v1/messages?id=eq.${notification.getString("message_id")}&sender_id=eq.$actorId&recipient_id=eq.${session.userId}&select=content&limit=1")
                resolved["message_preview"] = message?.optString("content").orEmpty()
            }
        } catch (error: Exception) {
            android.util.Log.w("PicGram", "No se pudo completar el perfil de la notificación", error)
        }
        // Preserve recovered name/kind/photo when a later request fails.
        return resolved
    }
}
class PicGramMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushManager.registerToken(applicationContext, token) }
    override fun onMessageReceived(message: RemoteMessage) {
        // Keep the service callback alive until delivery completes, including legacy payload resolution.
        runBlocking(Dispatchers.IO) {
            val data = PushManager.resolveContent(applicationContext, message.data)
            val photo = PushManager.loadProfilePhoto(applicationContext, data)
            PushManager.show(applicationContext, data, photo)
        }
    }
}
