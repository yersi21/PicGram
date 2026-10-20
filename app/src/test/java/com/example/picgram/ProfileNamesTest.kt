package com.example.picgram

import com.example.picgram.data.model.ProfileNames
import org.junit.Assert.*
import org.junit.Test

class ProfileNamesTest {
    @Test fun acceptsAccentsSpacesAndApostrophes() {
        assertEquals("Ana María O'Brien", ProfileNames.validatedName("  Ana María O'Brien  "))
    }

    @Test fun rejectsEmptyShortLongAndMultilineNames() {
        listOf("", " ", "A", "a".repeat(81), "Ana\nMaría", "Ana\tMaría").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { ProfileNames.validatedName(value) }
        }
    }

    @Test fun literalSearchDoesNotRewriteSpecialCharactersOrAccents() {
        assertEquals("López%,_()'", ProfileNames.searchTerm(" López%,_()' "))
        assertEquals("ana", ProfileNames.searchTerm(" @ana "))
        assertEquals("", ProfileNames.searchTerm(" @ "))
    }
}
