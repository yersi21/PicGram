package com.example.picgram

import android.content.Context
import android.util.Log

/** Storage and PostgreSQL are separate services; persist cleanup work after a confirmed DB delete. */
object PostOperations {
    @Synchronized
    fun scheduleCleanup(context: Context, userId: String, path: String) {
        if (!path.startsWith("posts/$userId/") && !path.startsWith("avatars/$userId/") &&
            path != "avatars/$userId.jpg") return
        val prefs = context.getSharedPreferences("picgram_cleanup", Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(userId, emptySet()).orEmpty().toMutableSet()
        pending.add(path)
        check(prefs.edit().putStringSet(userId, pending).commit()) { "No se pudo guardar la limpieza pendiente" }
    }

    @Synchronized
    fun flushCleanup(context: Context, session: UserSession) {
        val prefs = context.getSharedPreferences("picgram_cleanup", Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(session.userId, emptySet()).orEmpty().toMutableSet()
        for (path in pending.toList()) {
            try {
                SupabaseStorage.delete(path, session.accessToken)
                pending.remove(path)
                prefs.edit().putStringSet(session.userId, pending).commit()
            } catch (error: Exception) {
                Log.w("PicGram", "Limpieza de Storage pendiente", error)
            }
        }
    }

    fun deletePost(context: Context, session: UserSession, post: Post) {
        require(post.userId == session.userId) { "Solo puedes borrar tus publicaciones" }
        SupabaseDatabase.deletePost(session.accessToken, post.id)
        SupabaseStorage.extractPath(post.mediaUrl)?.let { path ->
            scheduleCleanup(context, session.userId, path)
            flushCleanup(context, session)
        }
    }
}
