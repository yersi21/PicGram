package com.example.picgram

import java.net.HttpURLConnection
import java.net.URL

/**
 * Utilidad para interactuar con Supabase Storage a traves de su API REST.
 * Usa HttpURLConnection nativo de Android: sin dependencias externas ni
 * problemas de compatibilidad de versiones de Kotlin.
 *
 * Documentacion de la API:
 * POST   /storage/v1/object/{bucket}/{path}        → sube un archivo
 * DELETE /storage/v1/object/{bucket}/{path}        → elimina un archivo
 * GET    /storage/v1/object/public/{bucket}/{path} → URL publica (bucket publico)
 */
object SupabaseStorage {

    private const val BUCKET = "picgram-posts"

    /**
     * Sube bytes a Supabase Storage y retorna la URL publica del archivo subido.
     * IMPORTANTE: Debe llamarse desde un hilo de IO (Dispatchers.IO).
     *
     * @param storagePath  Ruta relativa dentro del bucket, ej. "posts/uid/uuid.jpg".
     * @param bytes        Contenido del archivo en bytes.
     * @param contentType  Tipo MIME del archivo, ej. "image/jpeg" o "video/mp4".
     * @return             URL publica del archivo para mostrar en el feed.
     * @throws Exception   Si la subida falla (codigo HTTP fuera de rango 200-299).
     */
    fun upload(storagePath: String, bytes: ByteArray, contentType: String): String {
        val uploadUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET/$storagePath"

        val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("x-upsert", "false")
            connectTimeout = 60_000
            readTimeout = 120_000
            doOutput = true
        }

        try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw Exception("Supabase Storage error ($code): $error")
            }
        } finally {
            connection.disconnect()
        }

        return "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET/$storagePath"
    }

    /**
     * Elimina un archivo del bucket de Supabase Storage.
     * IMPORTANTE: Debe llamarse desde un hilo de IO (Dispatchers.IO).
     * No lanza excepcion si el archivo no existe — es una operacion idem potente.
     *
     * @param storagePath Ruta relativa del archivo dentro del bucket, ej. "posts/uid/uuid.jpg".
     * @param token       JWT del usuario autenticado (necesario para la politica de Storage).
     */
    fun delete(storagePath: String, token: String) {
        val deleteUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET/$storagePath"
        val connection = (URL(deleteUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                android.util.Log.w("SupabaseStorage", "delete($storagePath) returned HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extrae la ruta relativa dentro del bucket desde una URL publica de Supabase Storage.
     * Retorna null si la URL no pertenece a este bucket.
     * Uso: para poder borrar el archivo al borrar un post.
     *
     * @param publicUrl URL publica completa, ej.
     *   "https://xxx.supabase.co/storage/v1/object/public/picgram-posts/posts/uid/file.jpg"
     * @return  "posts/uid/file.jpg" o null si no es una URL valida del bucket.
     */
    fun extractPath(publicUrl: String): String? {
        val marker = "/storage/v1/object/public/$BUCKET/"
        val idx = publicUrl.indexOf(marker)
        if (idx == -1) return null
        val path = publicUrl.substring(idx + marker.length)
        return path.ifBlank { null }
    }
}