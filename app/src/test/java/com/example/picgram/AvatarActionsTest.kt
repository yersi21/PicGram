package com.example.picgram

import org.junit.Assert.*
import org.junit.Test

class AvatarActionsTest {
    @Test fun acceptsOnlyTheOwnersCurrentAndLegacyAvatarLocations() {
        assertTrue(isOwnedAvatarPath("me", "avatars/me/photo.png"))
        assertTrue(isOwnedAvatarPath("me", "avatars/me/photo.webp"))
        assertTrue(isOwnedAvatarPath("me", "avatars/me.jpg"))
    }

    @Test fun neverTreatsPostsOrAnotherUsersPhotoAsAvatarCleanup() {
        assertFalse(isOwnedAvatarPath("me", "posts/me/my-post.jpg"))
        assertFalse(isOwnedAvatarPath("me", "avatars/other/photo.jpg"))
        assertFalse(isOwnedAvatarPath("me", "avatars/me-other/photo.jpg"))
        assertFalse(isOwnedAvatarPath("me", "avatars/other.jpg"))
    }

    @Test fun rejectsEmptyOwnersFoldersAndTraversal() {
        assertFalse(isOwnedAvatarPath("", "avatars/me/photo.jpg"))
        assertFalse(isOwnedAvatarPath("me", "avatars/me/"))
        assertFalse(isOwnedAvatarPath("me", "avatars/me/../other.jpg"))
        assertFalse(isOwnedAvatarPath("me", "avatars/me/./photo.jpg"))
        assertFalse(isOwnedAvatarPath("me/other", "avatars/me/other/photo.jpg"))
    }
}
