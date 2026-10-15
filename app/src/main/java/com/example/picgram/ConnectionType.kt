package com.example.picgram

/** In a follow A -> B, A follows B and B's follower is A. */
enum class ConnectionType(val title: String, val ownerColumn: String, val userColumn: String) {
    FOLLOWERS("Seguidores", "following_id", "follower_id"),
    FOLLOWING("Seguidos", "follower_id", "following_id");

    companion object {
        fun fromExtra(value: String?): ConnectionType? = entries.firstOrNull { it.name == value }
    }
}
