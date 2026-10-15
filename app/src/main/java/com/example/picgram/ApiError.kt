package com.example.picgram

class ApiException(val statusCode: Int, message: String) : Exception(message)

class SessionExpiredException : Exception("Tu sesion ha expirado. Inicia sesion de nuevo.")

/** Retry only a rejected JWT, never an ambiguous network failure during a write. */
fun <T> authenticatedRequest(token: String, action: (String) -> T): T {
    val currentToken = SessionManager.validAccessToken(token)
    return try {
        action(currentToken)
    } catch (error: ApiException) {
        if (error.statusCode != 401) throw error
        action(SessionManager.validAccessToken(currentToken, forceRefresh = true))
    }
}
