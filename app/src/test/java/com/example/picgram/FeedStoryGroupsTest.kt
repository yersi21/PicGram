package com.example.picgram

import com.example.picgram.data.model.Story
import com.example.picgram.ui.activeStoryGroups
import org.junit.Assert.*
import org.junit.Test

class FeedStoryGroupsTest {
    @Test fun oneCirclePerAccountRetainsAllItsStoriesInOrder() {
        val first = Story("1", "ana", "a", "image", 200)
        val second = Story("2", "ana", "b", "video", 300)
        val other = Story("3", "luis", "c", "image", 400)
        val groups = activeStoryGroups(listOf(first, other, second), 100)
        assertEquals(listOf("ana", "luis"), groups.keys.toList())
        assertEquals(listOf(first, second), groups["ana"])
        assertEquals(listOf(other), groups["luis"])
    }

    @Test fun expiredStoriesDisappearAndAccountsWithNoActiveStoriesHaveNoCircle() {
        val expired = Story("1", "ana", "a", "image", 100)
        val active = Story("2", "luis", "b", "video", 101)
        assertEquals(mapOf("luis" to listOf(active)), activeStoryGroups(listOf(expired, active), 100))
        assertTrue(activeStoryGroups(listOf(expired, active), 101).isEmpty())
    }
}
