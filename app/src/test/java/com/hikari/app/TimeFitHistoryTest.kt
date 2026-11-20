package com.hikari.app

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.Page

class TimeFitHistoryTest {
    @Test
    fun historyStartsEmptyAndNormalizesTopicWithoutMixingBudgets() {
        val history = TimeFitHistory()
        assertTrue(history.shownIds("Nature", 5).isEmpty())
        history.remember(" Nature ", 5, listOf(video("first")))
        assertEquals(setOf("first"), history.shownIds("nature", 5))
        assertEquals(setOf("first"), history.shownIds("NATURE", 5))
        assertTrue(history.shownIds("nature", 10).isEmpty())
        assertTrue(history.shownIds("science", 5).isEmpty())
    }

    @Test
    fun rememberingMoreResultsAccumulatesIdsWithoutChangingOldSnapshots() {
        val history = TimeFitHistory()
        history.remember("nature", 5, listOf(video("first")))
        val snapshot = history.shownIds("nature", 5)
        history.remember("nature", 5, listOf(video("first"), video("second")))
        history.remember("nature", 5, emptyList())
        assertEquals(setOf("first"), snapshot)
        assertEquals(setOf("first", "second"), history.shownIds("nature", 5))
    }

    @Test
    fun switchingTopicsAndBackRetainsRecentShownIds() {
        val history = TimeFitHistory()
        history.remember("nature", 5, listOf(video("nature-one")))
        history.remember("science", 5, listOf(video("science-one")))
        assertEquals(setOf("nature-one"), history.shownIds("nature", 5))
        assertEquals(setOf("science-one"), history.shownIds("science", 5))
    }

    @Test
    fun historyIsBoundedAndRecentlyUsedCriteriaAreRetained() {
        val history = TimeFitHistory()
        repeat(20) { history.remember("topic $it", 5, listOf(video("video-$it"))) }
        history.remember("topic 0", 5, listOf(video("another")))
        history.remember("topic 20", 5, listOf(video("new")))
        assertTrue(history.shownIds("topic 1", 5).isEmpty())
        assertEquals(setOf("video-0", "another"), history.shownIds("topic 0", 5))
        assertEquals(setOf("new"), history.shownIds("topic 20", 5))
    }

    @Test
    fun repeatedFindsReturnNewVideosAcrossPagesAndDoNotRecycleWhenExhausted() = runBlocking {
        val history = TimeFitHistory()
        val candidates = (1..8).map { video("video-$it") }
        val secondPage = Page("https://www.youtube.com/results?page=2")
        val allShown = mutableSetOf<String>()
        val sizes = mutableListOf<Int>()
        repeat(4) {
            var pages = 0
            val result = TimeFitSearch.find(
                "nature", 5, history.shownIds("nature", 5),
                fetchPage = { _, cursor ->
                    pages++
                    if (cursor == null) SearchPage(candidates.take(4), secondPage)
                    else SearchPage(candidates.drop(4), null)
                },
                fallback = { error("Fallback should not be needed") })
            val ids = result.videos.map { it.id }.toSet()
            assertTrue("Repeat Find must not recycle an earlier result", ids.intersect(allShown).isEmpty())
            assertTrue(pages <= TimeFitSearch.PAGE_LIMIT)
            sizes += result.videos.size
            allShown += ids
            history.remember("nature", 5, result.videos)
        }
        assertEquals(listOf(3, 3, 2, 0), sizes)
        assertEquals(candidates.map { it.id }.toSet(), allShown)
    }

    @Test
    fun failedFindDoesNotConsumeUnseenVideos() = runBlocking {
        val history = TimeFitHistory()
        history.remember("nature", 5, listOf(video("first")))
        assertThrows(IOException::class.java) {
            runBlocking {
                TimeFitSearch.find(
                    "nature", 5, history.shownIds("nature", 5),
                    fetchPage = { _, _ -> throw IOException("Offline") },
                    fallback = { throw IOException("Offline") })
            }
        }
        assertEquals(setOf("first"), history.shownIds("nature", 5))
        val fresh = video("second")
        val result = TimeFitSearch.find(
            "nature", 5, history.shownIds("nature", 5),
            fetchPage = { _, _ -> SearchPage(listOf(video("first"), fresh), null) },
            fallback = { error("Fallback should not be needed") })
        assertEquals(listOf(fresh), result.videos)
    }

    private fun video(id: String) = Video(id, id, "Creator", "3:00", "")
}
