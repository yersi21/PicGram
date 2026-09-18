package com.example.picgram

import java.net.HttpURLConnection
import java.net.URL

/**
 * Utilidad para interactuar con Supabase Storage a traves de su API REST.
 * Usa HttpURLConnection nativo de Android: sin dependencias externas ni
 * problemas de compatibilidad de versiones de Kotlin.
 *
 * Documentacion de la API:
 * POST /storage/v1/object/{bucket}/{path}  → sube un archivo
 * GET  /storage/v1/object/public/{bucket}/{path} → URL publica (bucket publico)
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
            connectTimeout = 60_000   // 60 s para conectar
            readTimeout = 120_000     // 120 s para leer respuesta (archivos grandes)
            doOutput = true
        }

        try {
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: "HTTP $code"
                throw Exception("Supabase Storage error ($code): $error")
            }
        } finally {
            connection.disconnect()
        }

        // Construir la URL publica del objeto (requiere que el bucket sea publico)
        return "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET/$storagePath"
    }
}