package com.example.picgram

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

object SupabaseStorage {
    private const val BUCKET = "picgram-posts"

    fun upload(storagePath: String, media: PreparedMedia, token: String): String =
        uploadFile(storagePath, media.file, media.contentType, token)

    // A unique URL avoids stale cached avatars and keeps the old photo until the DB write succeeds.
    fun uploadAvatar(userId: String, media: PreparedMedia, token: String): String =
        uploadFile("avatars/$userId/${UUID.randomUUID()}.${media.extension}", media.file, media.contentType, token)

    private fun uploadFile(storagePath: String, file: File, contentType: String, token: String): String {
        authenticatedRequest(token) { validToken ->
            val connection = (URL("${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET/$storagePath")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $validToken")
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("x-upsert", "false")
                setFixedLengthStreamingMode(file.length())
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
            }
            try {
                file.inputStream().use { input -> connection.outputStream.use { input.copyTo(it, 64 * 1024) } }
                checkResponse(connection)
            } finally { connection.disconnect() }
        }
        return "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET/$storagePath"
    }

    fun delete(storagePath: String, token: String) {
        require(storagePath.isNotBlank() && !storagePath.contains("..")) { "Ruta de archivo invalida" }
        authenticatedRequest(token) { validToken ->
            val connection = (URL("${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET/$storagePath")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $validToken")
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                // A retry of an already removed file is successful too.
                if (connection.responseCode != 404) checkResponse(connection)
            } finally { connection.disconnect() }
        }
    }

    private fun checkResponse(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw ApiException(code, "Storage error ($code): $detail")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    fun extractPath(publicUrl: String): String? = storagePathFromUrl(BuildConfig.SUPABASE_URL, BUCKET, publicUrl)
}

/** Verify the origin and strip URL query parameters before scheduling a deletion. */
fun storagePathFromUrl(baseUrl: String, bucket: String, publicUrl: String): String? = try {
    val expected = URI(baseUrl)
    val actual = URI(publicUrl)
    val prefix = "${expected.path.trimEnd('/')}/storage/v1/object/public/$bucket/"
    if (actual.scheme != expected.scheme || actual.host != expected.host || actual.port != expected.port ||
        !actual.path.startsWith(prefix)) null
    else actual.path.removePrefix(prefix).takeIf { it.isNotBlank() && !it.contains("..") }
} catch (_: Exception) { null }
