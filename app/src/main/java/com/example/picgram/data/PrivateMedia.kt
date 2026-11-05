package com.example.picgram.data

import android.content.Context
import coil.ImageLoader
import com.example.picgram.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/** Every byte request is authorized again: no durable public/signed URLs for private posts. */
class MediaAuthorizationInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val canonical = storagePathFromUrl(BuildConfig.SUPABASE_URL, "picgram-posts", original.url.toString())
        if (canonical == null) return chain.proceed(original)
        val session = SessionManager.currentSession(context) ?: throw java.io.IOException("Sesión requerida")
        val token = SessionManager.validAccessToken(session.accessToken)
        val request = original.newBuilder().url(authenticatedMediaUrl(original.url.toString()))
            .header("Authorization", "Bearer $token").header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Cache-Control", "no-store").build()
        return chain.proceed(request)
    }
}
fun authenticatedMediaUrl(url: String): String =
    if (storagePathFromUrl(BuildConfig.SUPABASE_URL, "picgram-posts", url) != null)
        url.replace("/object/public/picgram-posts/", "/object/authenticated/picgram-posts/") else url

object PrivateMedia {
    fun client(context: Context) = OkHttpClient.Builder().addInterceptor(MediaAuthorizationInterceptor(context.applicationContext)).build()
    fun imageLoader(context: Context) = ImageLoader.Builder(context).okHttpClient(client(context))
        .memoryCache(null).diskCache(null).build()
}
