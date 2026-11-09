package com.example.picgram

import com.example.picgram.data.model.UserPresence
import com.example.picgram.ui.presenceLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceLabelTest {
    @Test fun statusUsesServerAgeAndElapsedTime() {
        assertEquals("En línea", presenceLabel(UserPresence(true, 0, 100), 100))
        assertEquals("En línea hace 2 min", presenceLabel(UserPresence(false, 60_000, 100), 60_100))
        assertEquals("En línea hace 2 h", presenceLabel(UserPresence(false, 7_200_000, 100), 100))
        assertEquals("En línea hace 2 días", presenceLabel(UserPresence(false, 172_800_000, 100), 100))
    }
    @Test fun missingStatusDoesNotInventLastConnection() {
        assertEquals("Estado no disponible", presenceLabel(null, 100))
        assertEquals("Sin conexión", presenceLabel(UserPresence(false, null, 100), 100))
    }
}
