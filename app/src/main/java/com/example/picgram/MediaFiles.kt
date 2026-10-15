package com.example.picgram

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Copies with bounded memory, including providers that do not report a file size. */
fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count == -1) return total
        total += count
        require(total <= maxBytes) { "El archivo supera el limite de ${maxBytes / (1024 * 1024)} MB" }
        output.write(buffer, 0, count)
    }
}

data class PreparedMedia(val file: File, val contentType: String, val extension: String) {
    val mediaType: String get() = if (contentType.startsWith("video/")) "video" else "image"
}

object MediaFiles {
    /** IO only. The caller owns the returned cache file and must delete it in finally. */
    fun prepare(context: Context, uri: Uri, avatar: Boolean = false): PreparedMedia {
        val mime = context.contentResolver.getType(uri)
            ?: throw IllegalArgumentException("No se pudo identificar el tipo de archivo")
        require(mime.startsWith("image/") || (!avatar && mime.startsWith("video/"))) {
            "Selecciona ${if (avatar) "una imagen" else "una imagen o un video"}"
        }
        val limit = if (avatar) 10L * 1024 * 1024 else 50L * 1024 * 1024
        val file = File.createTempFile("picgram-upload-", ".tmp", context.cacheDir)
        try {
            val length = context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> copyLimited(input, output, limit) }
            } ?: throw IllegalArgumentException("No se pudo leer el archivo")
            require(length > 0) { "El archivo esta vacio" }
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: if (mime.startsWith("video/")) "video" else "image"
            return PreparedMedia(file, mime, extension)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
