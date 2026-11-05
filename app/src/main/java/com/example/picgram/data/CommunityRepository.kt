package com.example.picgram.data

import com.example.picgram.SupabaseDatabase
import com.example.picgram.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface CommunityRepository {
    suspend fun profile(token: String, userId: String, own: Boolean): ProfileDetails
    suspend fun saveProfile(token: String, profile: ProfileDetails)
    suspend fun followStatus(token: String, me: String, target: String): FollowStatus
    suspend fun follow(token: String, target: String, cancel: Boolean): FollowStatus
    suspend fun requests(token: String, me: String): List<FollowRequest>
    suspend fun resolveRequest(token: String, requester: String, accept: Boolean)
    suspend fun stories(token: String): List<Story>
    suspend fun createStory(token: String, id: String, me: String, url: String, type: String)
    suspend fun conversations(token: String): List<Conversation>
    suspend fun messages(token: String, peer: String, before: DirectMessage? = null): List<DirectMessage>
    suspend fun sendMessage(token: String, message: DirectMessage)
    suspend fun markMessages(token: String, peer: String, through: String)
    suspend fun notifications(token: String, me: String): List<AppNotification>
    suspend fun markNotification(token: String, id: String)
    suspend fun registerDevice(token: String, device: String)
    suspend fun removeDevice(token: String, device: String)
}

class SupabaseCommunityRepository : CommunityRepository {
    private fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key, "")
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun rpc(token: String, name: String, body: JSONObject = JSONObject()) =
        SupabaseDatabase.request("POST", "/rest/v1/rpc/$name", token, body)
    private fun userRows(token: String, ids: List<String>): Map<String, JSONObject> = ids.distinct().chunked(100).flatMap {
        SupabaseDatabase.rows("/rest/v1/users?id=in.(${it.joinToString(",")})&select=id,username,avatar_url,display_name&order=id", token)
    }.associateBy { it.getString("id") }

    override suspend fun profile(token: String, userId: String, own: Boolean): ProfileDetails = withContext(Dispatchers.IO) {
        val row = JSONArray(SupabaseDatabase.request("GET",
            "/rest/v1/users?id=eq.$userId&select=id,username,display_name,bio,avatar_url,is_private", token)).getJSONObject(0)
        val personal = if (own) JSONArray(SupabaseDatabase.request("GET", "/rest/v1/personal_details?user_id=eq.$userId&select=phone,birth_date,location", token)).optJSONObject(0) else null
        ProfileDetails(row.getString("id"), row.text("username"), row.text("display_name"), row.text("bio"),
            row.text("avatar_url"), row.getBoolean("is_private"), personal?.text("phone").orEmpty(),
            personal?.text("birth_date").orEmpty(), personal?.text("location").orEmpty())
    }
    override suspend fun saveProfile(token: String, profile: ProfileDetails): Unit = withContext(Dispatchers.IO) {
        val p = profile.validated()
        rpc(token, "picgram_save_profile", JSONObject().put("full_name", p.name).put("biography", p.bio)
            .put("private_account", p.isPrivate).put("phone_number", p.phone)
            .put("birthday", p.birthday.ifBlank { null } ?: JSONObject.NULL).put("city", p.location)); Unit
    }
    override suspend fun followStatus(token: String, me: String, target: String): FollowStatus = withContext(Dispatchers.IO) {
        if (JSONArray(SupabaseDatabase.request("GET", "/rest/v1/follows?follower_id=eq.$me&following_id=eq.$target&select=id", token)).length() > 0) FollowStatus.FOLLOWING
        else if (JSONArray(SupabaseDatabase.request("GET", "/rest/v1/follow_requests?requester_id=eq.$me&target_id=eq.$target&select=requester_id", token)).length() > 0) FollowStatus.REQUESTED
        else FollowStatus.NONE
    }
    override suspend fun follow(token: String, target: String, cancel: Boolean): FollowStatus = withContext(Dispatchers.IO) {
        when (rpc(token, "picgram_follow", JSONObject().put("target_id", target).put("cancel", cancel)).trim('"')) {
            "following" -> FollowStatus.FOLLOWING; "requested" -> FollowStatus.REQUESTED; else -> FollowStatus.NONE
        }
    }
    override suspend fun requests(token: String, me: String): List<FollowRequest> = withContext(Dispatchers.IO) {
        val rows = SupabaseDatabase.rows("/rest/v1/follow_requests?target_id=eq.$me&select=requester_id&order=created_at,requester_id", token)
        val users = userRows(token, rows.map { it.getString("requester_id") })
        rows.map { val id = it.getString("requester_id"); FollowRequest(id, users[id]?.text("username").orEmpty(), users[id]?.text("avatar_url").orEmpty()) }
    }
    override suspend fun resolveRequest(token: String, requester: String, accept: Boolean): Unit = withContext(Dispatchers.IO) {
        rpc(token, "picgram_resolve_request", JSONObject().put("requester", requester).put("accept", accept)); Unit
    }
    override suspend fun stories(token: String): List<Story> = withContext(Dispatchers.IO) {
        val localNow = monotonicMillis()
        val serverNow = rpc(token, "picgram_server_now").toLong()
        val rows = SupabaseDatabase.rows("/rest/v1/stories?select=*&order=created_at,id", token)
        val users = userRows(token, rows.map { it.getString("user_id") })
        rows.map { val uid = it.getString("user_id"); Story(it.getString("id"), uid, it.getString("media_url"),
            it.getString("media_type"), localNow + epochMillis(it.getString("expires_at")) - serverNow, users[uid]?.text("username").orEmpty(), users[uid]?.text("avatar_url").orEmpty()) }
    }
    override suspend fun createStory(token: String, id: String, me: String, url: String, type: String): Unit = withContext(Dispatchers.IO) {
        require(url.isNotBlank() && type in listOf("image", "video"))
        SupabaseDatabase.request("POST", "/rest/v1/stories?on_conflict=id", token,
            JSONObject().put("id", id).put("user_id", me).put("media_url", url).put("media_type", type), "resolution=ignore-duplicates,return=minimal"); Unit
    }
    override suspend fun conversations(token: String): List<Conversation> = withContext(Dispatchers.IO) {
        val rows = JSONArray(rpc(token, "picgram_conversations")).objects()
        val users = userRows(token, rows.map { it.getString("peer_id") })
        rows.map { row ->
            val user = users[row.getString("peer_id")]
            Conversation(row.getString("peer_id"), row.text("last_content"), row.getString("last_at"), row.getInt("unread"),
                username = user?.text("username").orEmpty(), avatarUrl = user?.text("avatar_url").orEmpty(),
                displayName = user?.text("display_name").orEmpty())
        }
    }
    override suspend fun messages(token: String, peer: String, before: DirectMessage?): List<DirectMessage> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("peer", peer)
        before?.let { body.put("before_time", it.createdAt).put("before_id", it.id) }
        JSONArray(rpc(token, "picgram_messages", body)).objects().map {
            DirectMessage(it.getString("id"), it.getString("sender_id"), it.getString("recipient_id"), it.getString("content"), it.getString("created_at"), it.text("read_at").ifBlank { null })
        }.reversed()
    }
    override suspend fun sendMessage(token: String, message: DirectMessage): Unit = withContext(Dispatchers.IO) {
        SupabaseDatabase.request("POST", "/rest/v1/messages?on_conflict=id", token, JSONObject().put("id", message.id)
            .put("sender_id", message.senderId).put("recipient_id", message.recipientId).put("content", validatedMessage(message.content)),
            "resolution=ignore-duplicates,return=minimal"); Unit
    }
    override suspend fun markMessages(token: String, peer: String, through: String): Unit = withContext(Dispatchers.IO) {
        rpc(token, "picgram_mark_messages", JSONObject().put("peer", peer).put("through_time", through)); Unit
    }
    override suspend fun notifications(token: String, me: String): List<AppNotification> = withContext(Dispatchers.IO) {
        val rows = SupabaseDatabase.rows("/rest/v1/notifications?user_id=eq.$me&select=*&order=created_at.desc,id.desc", token)
        val users = userRows(token, rows.map { it.getString("actor_id") })
        val posts = rows.filter { it.text("kind") in listOf("like", "comment") }
            .map { it.text("post_id") }.filter { it.isNotBlank() }.distinct().chunked(100).flatMap { ids ->
                SupabaseDatabase.rows("/rest/v1/posts?id=in.(${ids.joinToString(",")})&select=id,media_url,media_type&order=id", token)
            }.associateBy { it.getString("id") }
        rows.map { AppNotification(it.getString("id"), it.getString("actor_id"), it.getString("kind"), it.getString("created_at"),
            it.text("read_at").isNotBlank(), it.text("post_id"), users[it.getString("actor_id")]?.text("username").orEmpty(),
            avatarUrl = users[it.getString("actor_id")]?.text("avatar_url").orEmpty(),
            postMediaUrl = posts[it.text("post_id")]?.text("media_url").orEmpty(),
            postMediaType = posts[it.text("post_id")]?.text("media_type").orEmpty()) }
    }
    override suspend fun markNotification(token: String, id: String): Unit = withContext(Dispatchers.IO) {
        rpc(token, "picgram_mark_notification", JSONObject().put("notification_id", id)); Unit
    }
    override suspend fun registerDevice(token: String, device: String): Unit = withContext(Dispatchers.IO) {
        rpc(token, "picgram_register_device", JSONObject().put("device_token", device)); Unit
    }
    override suspend fun removeDevice(token: String, device: String): Unit = withContext(Dispatchers.IO) {
        SupabaseDatabase.request("DELETE", "/rest/v1/push_devices?token=eq.${java.net.URLEncoder.encode(device,"UTF-8")}", token); Unit
    }
}
