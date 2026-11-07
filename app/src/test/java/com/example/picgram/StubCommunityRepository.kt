package com.example.picgram

import com.example.picgram.data.CommunityRepository
import com.example.picgram.data.model.*

open class StubCommunityRepository : CommunityRepository {
    override suspend fun profile(token: String, userId: String, own: Boolean): ProfileDetails = error("Unexpected profile")
    override suspend fun saveProfile(token: String, profile: ProfileDetails): Unit = error("Unexpected saveProfile")
    override suspend fun followStatus(token: String, me: String, target: String): FollowStatus = error("Unexpected followStatus")
    override suspend fun follow(token: String, target: String, cancel: Boolean): FollowStatus = error("Unexpected follow")
    override suspend fun requests(token: String, me: String): List<FollowRequest> = error("Unexpected requests")
    override suspend fun resolveRequest(token: String, requester: String, accept: Boolean): Unit = error("Unexpected resolve")
    override suspend fun stories(token: String): List<Story> = error("Unexpected stories")
    override suspend fun setStoryLike(token: String, storyId: String, userId: String, liked: Boolean): Unit = error("Unexpected story like")
    override suspend fun recordStoryView(token: String, storyId: String): Unit = error("Unexpected story view")
    override suspend fun storyViewers(token: String, storyId: String): List<StoryViewer> = error("Unexpected story viewers")
    override suspend fun createStory(token: String, id: String, me: String, url: String, type: String): Unit = error("Unexpected story insert")
    override suspend fun conversations(token: String): List<Conversation> = error("Unexpected conversations")
    override suspend fun messages(token: String, peer: String, before: DirectMessage?): List<DirectMessage> = error("Unexpected messages")
    override suspend fun sendMessage(token: String, message: DirectMessage): Unit = error("Unexpected send")
    override suspend fun markMessages(token: String, peer: String, through: String): Unit = error("Unexpected markMessages")
    override suspend fun notifications(token: String, me: String): List<AppNotification> = error("Unexpected notifications")
    override suspend fun markNotification(token: String, id: String): Unit = error("Unexpected markNotification")
    override suspend fun registerDevice(token: String, device: String): Unit = error("Unexpected registerDevice")
    override suspend fun removeDevice(token: String, device: String): Unit = error("Unexpected removeDevice")
}
