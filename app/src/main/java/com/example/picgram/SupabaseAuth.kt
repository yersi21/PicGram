package com.example.picgram

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Resultado de una operacion de autenticacion exitosa.
 *
 * @param accessToken JWT de Supabase — usar en todas las llamadas a la API.
 * @param userId      UUID del usuario en Supabase Auth.
 * @param email       Correo del usuario.
 */
typealias AuthResult = com.example.picgram.data.model.AuthResult

/**
 * Cliente de Supabase Auth via REST API.
 * Reemplaza Firebase Authentication sin necesidad de SDK externo.
 * Todos los metodos son bloqueantes — llamar desde Dispatchers.IO.
 *
 * Endpoints usados:
 *   POST /auth/v1/signup           → registro de nueva cuenta
 *   POST /auth/v1/token?grant_type=password → inicio de sesion
 *   POST /auth/v1/logout           → cierre de sesion en el servidor
 */
object SupabaseAuth {

    /**
     * Ejecuta una peticion HTTP POST a un endpoint de Supabase Auth.
     * Lanza [Exception] con mensaje legible si el servidor retorna un error.
     */
    private fun post(endpoint: String, body: JSONObject, token: String? = null): JSONObject {
        return authRequest("POST", endpoint, body, token)
    }

    private fun authRequest(method: String, endpoint: String, body: JSONObject? = null,
                            token: String? = null): JSONObject {
        val url = URL("${BuildConfig.SUPABASE_URL}$endpoint")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            setRequestProperty("Content-Type", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = body != null
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            return if (code in 200..299) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } else {
                val errText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw ApiException(code, parseErrorMessage(errText, code))
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Extrae un mensaje de error legible de la respuesta JSON de Supabase. */
    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val j = JSONObject(body)
            listOf("msg", "message", "error_description", "error")
                .firstNotNullOfOrNull { key -> j.optString(key, "").takeIf { it.isNotBlank() && it != "null" } }
                ?: "Error de autenticacion ($code)"
        } catch (_: Exception) {
            "Error de autenticacion ($code)"
        }
    }

    /**
     * Registra una nueva cuenta en Supabase Auth.
     * IMPORTANTE: desactiva la confirmacion de correo en Supabase →
     * Authentication → Email → Confirm email: OFF para que retorne el token de inmediato.
     *
     * @throws Exception si el correo ya existe o el registro falla.
     */
    fun signUp(email: String, password: String, username: String, displayName: String): AuthResult {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("data", JSONObject().put("username", username).put("display_name", displayName))
        }
        val response = post("/auth/v1/signup", body)

        val accessToken = response.optString("access_token", "")
        val user = response.optJSONObject("user") ?: response.takeIf { it.has("id") }
        val userId = user?.optString("id") ?: ""

        if (accessToken.isEmpty()) {
            // Supabase retorna esto cuando la confirmacion de correo esta activada
            val confirmationSent = user?.isNull("email_confirmed_at") == true
                && (user?.optJSONArray("identities")?.length() ?: 0) > 0
            if (confirmationSent) {
                throw Exception("Revisa tu correo y confirma tu cuenta antes de iniciar sesion")
            }
            throw Exception("No se pudo completar el registro. Intenta de nuevo.")
        }
        if (userId.isEmpty()) throw Exception("No se pudo obtener el ID de usuario")

        return parseSession(response, email)
    }

    /**
     * Inicia sesion con email y contrasena.
     *
     * @throws Exception con mensaje descriptivo si las credenciales son incorrectas.
     */
    fun signIn(email: String, password: String): AuthResult {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val response = post("/auth/v1/token?grant_type=password", body)

        val accessToken = response.optString("access_token", "")
        val user = response.optJSONObject("user")
        val userId = user?.optString("id") ?: ""

        if (accessToken.isEmpty() || userId.isEmpty()) {
            throw Exception("Credenciales incorrectas. Verifica tu correo y contrasena.")
        }

        return parseSession(response, email)
    }

    fun refreshSession(refreshToken: String): AuthResult = parseSession(
        post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken)),
        ""
    )

    fun requestPasswordRecovery(email: String) {
        val redirect = URLEncoder.encode(RecoveryLinks.REDIRECT_URL, "UTF-8")
        post("/auth/v1/recover?redirect_to=$redirect", JSONObject().put("email", email))
    }

    /** Recovery credentials never enter SessionManager or replace the signed-in user's JWT. */
    fun verifyRecoveryLink(link: RecoveryLink): AuthResult = when (link) {
        is RecoveryLink.TokenHash -> parseSession(post("/auth/v1/verify",
            JSONObject().put("token_hash", link.value).put("type", "recovery")), "")
        is RecoveryLink.Tokens -> {
            val user = authRequest("GET", "/auth/v1/user", token = link.accessToken)
            AuthResult(link.accessToken, user.getString("id"), user.getString("email"),
                refreshToken = link.refreshToken,
                username = user.optJSONObject("user_metadata")?.optString("username", "") ?: "",
                displayName = user.optJSONObject("user_metadata")?.optString("display_name", "") ?: "")
        }
    }

    fun updateRecoveredPassword(session: AuthResult, password: String): String =
        executeRecoveryUpdate(session, refresh = ::refreshSession) { token ->
            val user = authRequest("PUT", "/auth/v1/user", JSONObject().put("password", password), token)
            check(user.getString("id") == session.userId) { "No se pudo confirmar el cambio de contraseña" }
        }

    fun closeRecoverySession(token: String) {
        try { post("/auth/v1/logout?scope=local", JSONObject(), token) } catch (_: Exception) { }
    }

    private fun parseSession(response: JSONObject, fallbackEmail: String): AuthResult {
        val user = response.getJSONObject("user")
        val accessToken = response.getString("access_token")
        val refreshToken = response.getString("refresh_token")
        require(accessToken.isNotBlank() && refreshToken.isNotBlank()) { "Sesion incompleta" }
        return AuthResult(
            accessToken = accessToken,
            userId = user.getString("id"),
            email = user.optString("email", fallbackEmail),
            refreshToken = refreshToken,
            expiresAt = response.optLong("expires_at",
                System.currentTimeMillis() / 1000 + response.optLong("expires_in", 3600)),
            username = user.optJSONObject("user_metadata")?.optString("username", "") ?: "",
            displayName = user.optJSONObject("user_metadata")?.optString("display_name", "") ?: ""
        )
    }

    /**
     * Cierra la sesion en el servidor de Supabase (invalida el token).
     * Siempre limpia la sesion local aunque falle la llamada al servidor.
     */
    fun signOut(token: String) {
        try { post("/auth/v1/logout", JSONObject(), token) } catch (_: Exception) { }
    }
    /**
     * Verifica si el JWT sigue siendo valido consultando GET /auth/v1/user.
     * Retorna false si el usuario fue eliminado de Supabase o el token expiro.
     * Retorna true si hay error de red (para no forzar logout por problemas de red).
     * IMPORTANTE: Debe llamarse desde un hilo de IO (Dispatchers.IO).
     */
    fun validateToken(token: String): Boolean {
        return try {
            val url = java.net.URL("${BuildConfig.SUPABASE_URL}/auth/v1/user")
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                val code = connection.responseCode
                when (code) {
                    in 200..299 -> true
                    401, 403 -> false
                    else -> throw ApiException(code, "No se pudo verificar la sesion ($code)")
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: ApiException) {
            throw error
        } catch (_: java.io.IOException) {
            true   // Una desconexion no invalida una sesion.
        }
    }
}
