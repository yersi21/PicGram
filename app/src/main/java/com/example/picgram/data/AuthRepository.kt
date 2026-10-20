package com.example.picgram.data

import com.example.picgram.*
import kotlinx.coroutines.*

interface AuthRepository {
    suspend fun signUp(email: String, password: String, username: String, displayName: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun requestPasswordRecovery(email: String): Unit
    suspend fun verifyRecoveryLink(link: RecoveryLink): AuthResult
    suspend fun updateRecoveredPassword(session: AuthResult, password: String): String
    suspend fun closeRecoverySession(token: String): Unit
    suspend fun signOut(token: String): Unit
    suspend fun validateToken(token: String): Boolean
}

class SupabaseAuthRepository(private val io: CoroutineDispatcher = Dispatchers.IO) : AuthRepository {
    override suspend fun signUp(email: String, password: String, username: String, displayName: String): AuthResult = withContext(io) { SupabaseAuth.signUp(email, password, username, displayName) }
    override suspend fun signIn(email: String, password: String): AuthResult = withContext(io) { SupabaseAuth.signIn(email, password) }
    override suspend fun requestPasswordRecovery(email: String): Unit = withContext(io) { SupabaseAuth.requestPasswordRecovery(email) }
    override suspend fun verifyRecoveryLink(link: RecoveryLink): AuthResult = withContext(io) { SupabaseAuth.verifyRecoveryLink(link) }
    override suspend fun updateRecoveredPassword(session: AuthResult, password: String): String = withContext(io) { SupabaseAuth.updateRecoveredPassword(session, password) }
    override suspend fun closeRecoverySession(token: String): Unit = withContext(io) { SupabaseAuth.closeRecoverySession(token) }
    override suspend fun signOut(token: String): Unit = withContext(io) { SupabaseAuth.signOut(token) }
    override suspend fun validateToken(token: String): Boolean = withContext(io) { SupabaseAuth.validateToken(token) }
}
