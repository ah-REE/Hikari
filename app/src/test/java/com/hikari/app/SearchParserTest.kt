package com.hikari.app

import org.junit.Assert.*
import org.junit.Test

class SearchParserTest {
    @Test fun extractsNestedVideosAndDeduplicates() {
        val video = """{"videoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"A title"}]},"ownerText":{"runs":[{"text":"Creator"}]},"lengthText":{"simpleText":"12:34"}}}"""
        val results = SearchParser.parse("<script>var ytInitialData = {\"contents\":[$video,$video]};</script>")
        assertEquals(1, results.size)
        assertEquals("A title", results.first().title)
        assertEquals("Creator", results.first().channel)
        assertEquals("12:34", results.first().duration)
    }
    @Test(expected = IllegalStateException::class) fun rejectsConsentOrBlockedPage() {
        SearchParser.parse("<html>Please consent</html>")
    }
    @Test fun handlesEmptyResults() {
        assertTrue(SearchParser.parse("var ytInitialData = {\"contents\":[]};").isEmpty())
    }
}
