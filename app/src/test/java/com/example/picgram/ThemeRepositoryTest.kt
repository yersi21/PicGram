package com.example.picgram

import com.example.picgram.data.ThemePreferences
import com.example.picgram.data.ThemeRepository
import org.junit.Assert.*
import org.junit.Test

class ThemeRepositoryTest {
    private class Preferences(var saved: Boolean? = null) : ThemePreferences {
        override fun readDarkMode() = saved
        override fun writeDarkMode(enabled: Boolean) { saved = enabled }
    }

    @Test fun noPreferenceFollowsTheDevice() {
        assertNull(ThemeRepository(Preferences()).darkMode.value)
    }

    @Test fun bothLightAndDarkChoicesSurviveRepositoryRecreation() {
        val preferences = Preferences()
        val repository = ThemeRepository(preferences)
        repository.setDarkMode(true)
        assertEquals(true, repository.darkMode.value)
        assertEquals(true, ThemeRepository(preferences).darkMode.value)
        repository.setDarkMode(false)
        assertEquals(false, repository.darkMode.value)
        // An explicit light mode is not the same as following the device.
        assertEquals(false, ThemeRepository(preferences).darkMode.value)
    }

    @Test fun aFailedWriteKeepsThePreviousMode() {
        val repository = ThemeRepository(object : ThemePreferences {
            override fun readDarkMode(): Boolean = false
            override fun writeDarkMode(enabled: Boolean) { error("Cannot save") }
        })
        assertThrows(IllegalStateException::class.java) { repository.setDarkMode(true) }
        assertEquals(false, repository.darkMode.value)
    }
}
