package com.example.picgram

import java.net.URI
import java.net.URLDecoder

// These types deliberately avoid generated toString() methods that could expose credentials in logs.
sealed class RecoveryLink {
    class Tokens(val accessToken: String, val refreshToken: String) : RecoveryLink()
    class TokenHash(val value: String) : RecoveryLink()
}

class RecoveryExpiredException : Exception("La sesión de recuperación caducó. Solicita otro enlace.")

object RecoveryLinks {
    const val REDIRECT_URL = "com.example.picgram://auth/reset-password"

    fun parse(raw: String?): RecoveryLink {
        require(!raw.isNullOrBlank() && raw.length <= 32_768) { "Abre el enlace de recuperación recibido por correo" }
        val uri = try { URI(raw) } catch (_: Exception) {
            throw IllegalArgumentException("El enlace de recuperación no es válido")
        }
        require(uri.scheme.equals("com.example.picgram", ignoreCase = true) &&
            uri.host.equals("auth", ignoreCase = true) && uri.rawPath == "/reset-password" &&
            uri.userInfo == null && uri.port == -1) { "El enlace de recuperación no es válido" }
        val values = mutableMapOf<String, String>()
        for (section in listOfNotNull(uri.rawQuery, uri.rawFragment)) {
            for (part in section.split('&').filter { it.isNotBlank() }) {
                val pair = part.split('=', limit = 2)
                val key = decode(pair[0])
                require(!values.containsKey(key)) { "El enlace de recuperación no es válido" }
                values[key] = decode(pair.getOrElse(1) { "" })
            }
        }
        require(!values.containsKey("error") && !values.containsKey("error_code")) {
            "El enlace caducó o ya fue utilizado. Solicita uno nuevo."
        }
        require(values["type"] == "recovery") { "Este enlace no es de recuperación de contraseña" }
        val hash = values["token_hash"].orEmpty()
        val token = values["access_token"].orEmpty()
        require(hash.isBlank() || token.isBlank()) { "El enlace de recuperación no es válido" }
        if (hash.isNotBlank()) return RecoveryLink.TokenHash(hash)
        require(token.isNotBlank() && values["token_type"].let { it == null || it == "bearer" }) {
            "El enlace está incompleto. Solicita uno nuevo."
        }
        return RecoveryLink.Tokens(token, values["refresh_token"].orEmpty())
    }

    private fun decode(value: String): String = try { URLDecoder.decode(value, "UTF-8") }
    catch (_: Exception) { throw IllegalArgumentException("El enlace de recuperación no es válido") }
}

/** Retry only an explicit JWT rejection, using this recovery session's refresh token. */
fun executeRecoveryUpdate(session: AuthResult, refresh: (String) -> AuthResult,
                          update: (String) -> Unit): String {
    return try {
        update(session.accessToken)
        session.accessToken
    } catch (error: ApiException) {
        if (error.statusCode != 401 || session.refreshToken.isBlank()) throw error
        val renewed = try { refresh(session.refreshToken) } catch (failure: ApiException) {
            if (failure.statusCode in listOf(400, 401, 403)) throw RecoveryExpiredException()
            throw failure
        }
        if (renewed.userId != session.userId) throw RecoveryExpiredException()
        update(renewed.accessToken)
        renewed.accessToken
    }
}

fun newPasswordError(password: String, confirmation: String): String? = when {
    password.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
    password != confirmation -> "Las contraseñas no coinciden"
    else -> null
}
