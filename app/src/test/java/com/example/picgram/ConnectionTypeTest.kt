package com.example.picgram

import org.junit.Assert.*
import org.junit.Test

class ConnectionTypeTest {
    @Test fun followersLookUpThePeopleWhoFollowTheProfile() {
        assertEquals("following_id", ConnectionType.FOLLOWERS.ownerColumn)
        assertEquals("follower_id", ConnectionType.FOLLOWERS.userColumn)
    }

    @Test fun followingLooksUpThePeopleTheProfileFollows() {
        assertEquals("follower_id", ConnectionType.FOLLOWING.ownerColumn)
        assertEquals("following_id", ConnectionType.FOLLOWING.userColumn)
    }

    @Test fun navigationAcceptsOnlyKnownConnectionTypes() {
        assertEquals(ConnectionType.FOLLOWERS, ConnectionType.fromExtra("FOLLOWERS"))
        assertEquals(ConnectionType.FOLLOWING, ConnectionType.fromExtra("FOLLOWING"))
        assertNull(ConnectionType.fromExtra(null))
        assertNull(ConnectionType.fromExtra("other"))
    }
}
