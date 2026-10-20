package com.example.picgram.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** null follows the device until the user chooses a mode in the feed. */
interface ThemePreferences {
    fun readDarkMode(): Boolean?
    fun writeDarkMode(enabled: Boolean)
}

class ThemeRepository(private val preferences: ThemePreferences = MemoryThemePreferences()) {
    private val state = MutableStateFlow(preferences.readDarkMode())
    val darkMode = state.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        preferences.writeDarkMode(enabled)
        state.value = enabled
    }
}

private class MemoryThemePreferences : ThemePreferences {
    private var darkMode: Boolean? = null
    override fun readDarkMode() = darkMode
    override fun writeDarkMode(enabled: Boolean) { darkMode = enabled }
}

class AndroidThemePreferences(context: Context) : ThemePreferences {
    // Separate from session preferences: signing out does not reset appearance.
    private val preferences = context.applicationContext.getSharedPreferences("picgram_appearance", Context.MODE_PRIVATE)
    override fun readDarkMode(): Boolean? =
        if (preferences.contains("dark_mode")) preferences.getBoolean("dark_mode", false) else null

    override fun writeDarkMode(enabled: Boolean) {
        preferences.edit().putBoolean("dark_mode", enabled).apply()
    }
}
