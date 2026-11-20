package com.hikari.app

import org.junit.Assert.*
import org.junit.Test

class SearchStateTest {
    private val first = Video("aaaaaaaaaaa", "First video", "Creator", "1:00", "")
    private val second = Video("bbbbbbbbbbb", "Second video", "Creator", "2:00", "")

    @Test
    fun newQueryGetsFreshResultsIdentityAndClearsPreviousResults() {
        val previous = HikariState().beginSearch("first").copy(
            videos = listOf(first), loading = false, loadingMore = true, hasMore = true, error = "Old error"
        )
        val next = previous.beginSearch("  second  ")
        assertEquals("second", next.query)
        assertEquals(previous.searchGeneration + 1, next.searchGeneration)
        assertTrue(next.videos.isEmpty())
        assertTrue(next.loading)
        assertTrue(next.searched)
        assertFalse(next.loadingMore)
        assertFalse(next.hasMore)
        assertNull(next.error)
    }

    @Test
    fun resubmittingTheSameQueryAlsoGetsFreshResultsIdentity() {
        val previous = HikariState().beginSearch("same query").copy(videos = listOf(first), loading = false)
        val next = previous.beginSearch("same query")
        assertEquals(previous.query, next.query)
        assertNotEquals(previous.searchGeneration, next.searchGeneration)
    }

    @Test
    fun paginationAndUnrelatedUpdatesKeepResultsIdentity() {
        val searching = HikariState().beginSearch("videos")
        val loaded = searching.copy(videos = listOf(first), loading = false, hasMore = true)
        val loadingMore = loaded.copy(loadingMore = true)
        val appended = loadingMore.copy(videos = listOf(first, second), loadingMore = false)
        val saved = appended.copy(saved = listOf(first), dark = false, feed = listOf(second))
        listOf(loaded, loadingMore, appended, saved).forEach {
            assertEquals(searching.searchGeneration, it.searchGeneration)
        }
    }

    @Test
    fun returningToAnEarlierQueryDoesNotReuseTheCurrentResultsPosition() {
        val previous = HikariState().beginSearch("first").copy(videos = listOf(first), loading = false)
        val current = previous.beginSearch("second").copy(videos = listOf(second), loading = false)
        val restored = current.restoreSearch(previous, hasMore = true)
        assertEquals(previous.query, restored.query)
        assertEquals(previous.videos, restored.videos)
        assertEquals(current.searchGeneration + 1, restored.searchGeneration)
        assertTrue(restored.hasMore)
        assertFalse(restored.loading)
        assertFalse(restored.loadingMore)
        assertNotEquals(previous.searchGeneration, restored.searchGeneration)
    }

    @Test
    fun searchTransitionsPreserveCurrentLibraryAndAppSettings() {
        val previous = HikariState(query = "old", videos = listOf(first), dark = true)
        val current = HikariState(
            saved = listOf(second), dark = false, recents = listOf("recent"), reduceMotion = true,
            feed = listOf(second), feedFailures = 2
        )
        listOf(current.beginSearch("new"), current.restoreSearch(previous, hasMore = false)).forEach {
            assertEquals(current.saved, it.saved)
            assertEquals(current.dark, it.dark)
            assertEquals(current.recents, it.recents)
            assertEquals(current.reduceMotion, it.reduceMotion)
            assertEquals(current.feed, it.feed)
            assertEquals(current.feedFailures, it.feedFailures)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankQueryCannotStartANewResultsSession() {
        HikariState().beginSearch("   ")
    }
}
