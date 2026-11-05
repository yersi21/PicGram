package com.example.picgram.ui

import com.example.picgram.data.model.Story

/** Keeps every active story while displaying a single circle per account. */
internal fun activeStoryGroups(stories: List<Story>, now: Long): Map<String, List<Story>> =
    stories.filter { it.isActive(now) }.groupBy { it.userId }
