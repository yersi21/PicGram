package com.example.picgram.data

import android.app.Application
import android.content.Context
import android.net.Uri
import com.example.picgram.*
import org.json.JSONObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Manual constructor injection. Android context belongs only to the data layer. */
class AppContainer(
    val auth: AuthRepository,
    val social: SocialRepository,
    val session: SessionRepository,
    val media: MediaRepository,
    val drafts: DraftRepository,
    val emailValidator: EmailValidator = AndroidEmailValidator,
    val theme: ThemeRepository = ThemeRepository()
) {
    constructor(context: Context) : this(SupabaseAuthRepository(), SupabaseSocialRepository(),
        LocalSessionRepository(context.applicationContext), StorageMediaRepository(context.applicationContext),
        LocalDraftRepository(context.applicationContext),
        theme = ThemeRepository(AndroidThemePreferences(context.applicationContext)))
}

fun interface EmailValidator { fun isValid(email: String): Boolean }
object AndroidEmailValidator : EmailValidator {
    override fun isValid(email: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

class PicGramApplication : Application() {
    val container by lazy { AppContainer(this) }
}

interface SessionRepository {
    fun currentSession(): UserSession?
    fun saveSession(session: UserSession)
    fun clearSession()
    suspend fun validAccessToken(token: String): String
}

class LocalSessionRepository(private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO) : SessionRepository {
    override fun currentSession() = SessionManager.currentSession(context)
    override fun saveSession(session: UserSession) = SessionManager.saveSession(context, session)
    override fun clearSession() = SessionManager.clearSession(context)
    override suspend fun validAccessToken(token: String) = withContext(io) { SessionManager.validAccessToken(token) }
}

interface MediaRepository {
    suspend fun uploadPostMedia(uri: Uri, session: UserSession): Pair<String, String>
    suspend fun uploadProfilePhoto(uri: Uri, session: UserSession): String
    fun extractPath(url: String): String?
    suspend fun scheduleCleanup(userId: String, path: String)
    suspend fun flushCleanup(session: UserSession)
    suspend fun deletePost(session: UserSession, post: Post)
    fun mimeType(uri: Uri): String?
}

class StorageMediaRepository(private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO) : MediaRepository {
    override suspend fun uploadPostMedia(uri: Uri, session: UserSession) = withContext(io) {
        val prepared = MediaFiles.prepare(context, uri)
        try {
            val path = "posts/${session.userId}/${java.util.UUID.randomUUID()}.${prepared.extension}"
            SupabaseStorage.upload(path, prepared, session.accessToken) to prepared.mediaType
        } finally { prepared.file.delete() }
    }
    override suspend fun uploadProfilePhoto(uri: Uri, session: UserSession) = withContext(io) {
        val prepared = MediaFiles.prepare(context, uri, avatar = true)
        try { SupabaseStorage.uploadAvatar(session.userId, prepared, session.accessToken) }
        finally { prepared.file.delete() }
    }
    override fun extractPath(url: String) = SupabaseStorage.extractPath(url)
    override suspend fun scheduleCleanup(userId: String, path: String) = withContext(io) {
        PostOperations.scheduleCleanup(context, userId, path)
    }
    override suspend fun flushCleanup(session: UserSession) = withContext(io) { PostOperations.flushCleanup(context, session) }
    override suspend fun deletePost(session: UserSession, post: Post) = withContext(io) { PostOperations.deletePost(context, session, post) }
    override fun mimeType(uri: Uri) = context.contentResolver.getType(uri)
}

interface DraftRepository {
    suspend fun load(userId: String): Post?
    suspend fun save(post: Post)
    suspend fun remove(userId: String)
}

class LocalDraftRepository(context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) : DraftRepository {
    private val preferences = context.getSharedPreferences("picgram_drafts", Context.MODE_PRIVATE)
    override suspend fun load(userId: String): Post? = withContext(io) { preferences.getString(userId, null)?.let { json ->
        runCatching {
            val obj = JSONObject(json)
            Post(id = obj.getString("id"), userId = userId, caption = obj.getString("caption"),
                mediaUrl = obj.getString("media_url"), mediaType = obj.getString("media_type"),
                timestamp = obj.getLong("timestamp"))
        }.getOrNull()
    } }
    override suspend fun save(post: Post) = withContext(io) {
        val serialized = JSONObject().put("id", post.id).put("caption", post.caption)
            .put("media_url", post.mediaUrl).put("media_type", post.mediaType).put("timestamp", post.timestamp)
        check(preferences.edit().putString(post.userId, serialized.toString()).commit()) { "No se pudo guardar el borrador" }
    }
    override suspend fun remove(userId: String) { withContext(io) { preferences.edit().remove(userId).commit() } }
}
