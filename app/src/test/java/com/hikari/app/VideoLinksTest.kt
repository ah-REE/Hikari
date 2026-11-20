package com.hikari.app

import org.junit.Assert.*
import org.junit.Test

class VideoLinksTest {
    @Test fun recognizesWatchShortAndSharedLinks() {
        listOf("https://youtube.com/watch?v=abcdefghijk&t=5", "https://youtu.be/abcdefghijk", "Watch this https://www.youtube.com/shorts/abcdefghijk").forEach { assertEquals("abcdefghijk", VideoLinks.id(it)) }
    }
    @Test fun rejectsLookalikeDomainsAndInvalidIds() {
        listOf("https://youtube.com.evil.test/watch?v=abcdefghijk", "https://example.com/abcdefghijk", "https://youtube.com/watch?v=bad").forEach { assertNull(VideoLinks.id(it)) }
    }
    @Test fun shortsPolicyOnlyAppliesToValidShortsLinks() {
        assertTrue(VideoLinks.isShort("Watch this https://www.youtube.com/shorts/abcdefghijk?feature=share"))
        assertTrue(VideoLinks.isShort("https://m.youtube.com/shorts/abcdefghijk"))
        listOf(null, "https://youtube.com/shorts/bad", "https://evil.test/shorts/abcdefghijk", "https://youtube.com/watch?v=abcdefghijk", "https://youtu.be/abcdefghijk").forEach { assertFalse(VideoLinks.isShort(it)) }
    }
}
