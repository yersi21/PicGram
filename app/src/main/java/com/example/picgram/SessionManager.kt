package com.example.picgram

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject

/**
 * Datos de la sesion activa del usuario autenticado.
 *
 * @param accessToken Token JWT de Supabase — se incluye en todas las llamadas a la API.
 * @param userId      UUID del usuario en Supabase Auth (= id en la tabla public.users).
 * @param email       Correo electronico del usuario.
 * @param username    Nombre de usuario elegido al registrarse.
 */
typealias UserSession = com.example.picgram.data.model.UserSession

/**
 * Gestor de sesion usando SharedPreferences.
 * Almacena el JWT de Supabase Auth localmente para el auto-login entre reinicios de la app.
 * NO almacena la contrasena del usuario.
 */
object SessionManager {

    private const val PREFS_NAME = "picgram_session"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_USERNAME = "username"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRY = "expires_at"
    @Volatile private var appContext: Context? = null
    private val refreshLock = Any()

    private fun prefs(context: Context): SharedPreferences {
        appContext = context.applicationContext
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Persiste la sesion activa en SharedPreferences. */
    @Synchronized
    fun saveSession(context: Context, session: UserSession) {
        prefs(context).edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRY, session.expiresAt)
            .apply()
    }

    /**
     * Retorna la sesion guardada, o null si no hay sesion activa.
     * Las llamadas de red renuevan la sesion mediante validAccessToken().
     */
    @Synchronized
    fun currentSession(context: Context): UserSession? {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null) ?: return null
        val userId = p.getString(KEY_USER_ID, null) ?: return null
        val email = p.getString(KEY_EMAIL, null) ?: return null
        val username = p.getString(KEY_USERNAME, null) ?: ""
        return UserSession(token, userId, email, username,
            p.getString(KEY_REFRESH, "") ?: "", p.getLong(KEY_EXPIRY, 0L))
    }

    /** Blocking: call from IO. The lock prevents simultaneous refresh-token rotation. */
    fun validAccessToken(requestedToken: String, forceRefresh: Boolean = false): String {
        synchronized(refreshLock) {
        val context = appContext ?: return requestedToken
        val session = currentSession(context) ?: return requestedToken
        val requestedUser = tokenClaims(requestedToken)?.optString("sub")
        if (requestedUser != session.userId) throw SessionExpiredException()
        val expiry = session.expiresAt.takeIf { it > 0 }
            ?: tokenClaims(session.accessToken)?.optLong("exp") ?: 0L
        val alreadyRefreshed = session.accessToken != requestedToken
        if ((!forceRefresh || alreadyRefreshed) && expiry > System.currentTimeMillis() / 1000 + 60) {
            return session.accessToken
        }
        if (session.refreshToken.isBlank()) {
            clearSession(context)
            throw SessionExpiredException()
        }
        val refreshed = try {
            SupabaseAuth.refreshSession(session.refreshToken)
        } catch (error: ApiException) {
            if (error.statusCode == 400 || error.statusCode == 401) {
                clearSession(context)
                throw SessionExpiredException()
            }
            throw error
        }
        if (refreshed.userId != session.userId) throw SessionExpiredException()
        synchronized(this) {
            // A logout or a new login during the network request must not restore an old session.
            val active = currentSession(context)
            if (active?.userId != session.userId || active.refreshToken != session.refreshToken) {
                throw SessionExpiredException()
            }
            saveSession(context, session.copy(accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken, expiresAt = refreshed.expiresAt))
        }
        return refreshed.accessToken
        }
    }

    private fun tokenClaims(token: String): JSONObject? = try {
        JSONObject(String(Base64.decode(token.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
    } catch (_: Exception) { null }

    /** Elimina todos los datos de sesion (cierre de sesion). */
    @Synchronized
    fun clearSession(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
