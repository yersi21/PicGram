package com.example.picgram

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MediaSafetyTest {
    @Test fun copiesLargeFilesWithExactContents() {
        val source = ByteArray(150_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(source.size.toLong(), copyLimited(ByteArrayInputStream(source), output, source.size.toLong()))
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun rejectsAnOversizedFileWithoutWritingPastTheLimit() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            copyLimited(ByteArrayInputStream(ByteArray(150_000)), output, 100_000)
        }
        assertTrue(output.size() <= 100_000)
    }

    @Test fun emptyStreamCopiesZeroBytes() {
        assertEquals(0L, copyLimited(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 10))
    }

    @Test fun removesCacheQueryParametersFromAStoragePath() {
        assertEquals("avatars/u/photo.jpg", storagePathFromUrl("https://example.supabase.co", "picgram-posts",
            "https://example.supabase.co/storage/v1/object/public/picgram-posts/avatars/u/photo.jpg?v=123"))
    }

    @Test fun rejectsOtherOriginsAndBuckets() {
        val base = "https://example.supabase.co"
        assertNull(storagePathFromUrl(base, "picgram-posts", "https://attacker.test/storage/v1/object/public/picgram-posts/a.jpg"))
        assertNull(storagePathFromUrl(base, "picgram-posts", "$base/storage/v1/object/public/other/a.jpg"))
        assertNull(storagePathFromUrl(base, "picgram-posts", "http://example.supabase.co/storage/v1/object/public/picgram-posts/a.jpg"))
    }

    @Test fun rejectsTraversalMalformedAndEmptyPaths() {
        val base = "https://example.supabase.co"
        assertNull(storagePathFromUrl(base, "picgram-posts", "$base/storage/v1/object/public/picgram-posts/%2e%2e/a.jpg"))
        assertNull(storagePathFromUrl(base, "picgram-posts", "$base/storage/v1/object/public/picgram-posts/"))
        assertNull(storagePathFromUrl(base, "picgram-posts", "not a valid URI"))
    }
}
