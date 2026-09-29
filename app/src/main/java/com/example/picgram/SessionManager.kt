package com.example.picgram

import android.content.Context
import android.content.SharedPreferences

/**
 * Datos de la sesion activa del usuario autenticado.
 *
 * @param accessToken Token JWT de Supabase — se incluye en todas las llamadas a la API.
 * @param userId      UUID del usuario en Supabase Auth (= id en la tabla public.users).
 * @param email       Correo electronico del usuario.
 * @param username    Nombre de usuario elegido al registrarse.
 */
data class UserSession(
    val accessToken: String,
    val userId: String,
    val email: String,
    val username: String
)

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

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Persiste la sesion activa en SharedPreferences. */
    fun saveSession(context: Context, session: UserSession) {
        prefs(context).edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_USERNAME, session.username)
            .apply()
    }

    /**
     * Retorna la sesion guardada, o null si no hay sesion activa.
     * Nota: no verifica la validez/expiracion del token; para este proyecto
     * se asume que el token tiene larga duracion (configurado en Supabase).
     */
    fun currentSession(context: Context): UserSession? {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null) ?: return null
        val userId = p.getString(KEY_USER_ID, null) ?: return null
        val email = p.getString(KEY_EMAIL, null) ?: return null
        val username = p.getString(KEY_USERNAME, null) ?: ""
        return UserSession(token, userId, email, username)
    }

    /** Elimina todos los datos de sesion (cierre de sesion). */
    fun clearSession(context: Context) {
        prefs(context).edit().clear().apply()
    }
}