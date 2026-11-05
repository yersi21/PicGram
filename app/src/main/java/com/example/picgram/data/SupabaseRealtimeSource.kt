package com.example.picgram.data

import com.example.picgram.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface RealtimeSource { fun changes(token: String, userId: String): Flow<Boolean> }

/** Invalidations only: authoritative data is reloaded through RLS after every event. */
class SupabaseRealtimeSource : RealtimeSource {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    override fun changes(token: String, userId: String): Flow<Boolean> = callbackFlow {
        val topic = "realtime:picgram-$userId"
        var sequence = 1
        fun frame(event: String, payload: JSONObject, target: String = topic) = JSONObject()
            .put("topic", target).put("event", event).put("payload", payload).put("ref", (sequence++).toString()).toString()
        val socket = client.newWebSocket(Request.Builder().url(BuildConfig.SUPABASE_URL.replaceFirst("https://", "wss://") +
            "/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_KEY}&vsn=1.0.0").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val subscriptions = JSONArray()
                listOf("messages", "notifications").forEach { table ->
                    subscriptions.put(JSONObject().put("event", "*").put("schema", "public").put("table", table))
                }
                webSocket.send(frame("phx_join", JSONObject().put("access_token", token).put("config",
                    JSONObject().put("postgres_changes", subscriptions))))
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    when (message.optString("event")) {
                        "phx_reply" -> if (message.optJSONObject("payload")?.optString("status") == "ok") trySend(true)
                            else close(IllegalStateException("Realtime rechazó la suscripción"))
                        "postgres_changes" -> trySend(true)
                        "phx_error", "phx_close" -> close(IllegalStateException("Realtime desconectado"))
                        "system" -> if (message.optJSONObject("payload")?.optString("status") == "error")
                            close(IllegalStateException("Activa Realtime en Supabase"))
                    }
                } catch (failure: Exception) { close(failure) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
        val heartbeat = launch {
            while (true) { delay(25_000); socket.send(frame("heartbeat", JSONObject(), "phoenix")) }
        }
        awaitClose { heartbeat.cancel(); socket.close(1000, "Screen stopped") }
    }
}
