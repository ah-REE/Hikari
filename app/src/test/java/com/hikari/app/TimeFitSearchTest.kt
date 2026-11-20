package com.hikari.app

import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.Page

class TimeFitSearchTest {
    @Test
    fun exposesSupportedBudgetsAndBounds() {
        assertEquals(listOf(5, 10, 15, 30), TimeFitSearch.minuteOptions)
        assertEquals(3, TimeFitSearch.RESULT_LIMIT)
        assertEquals(3, TimeFitSearch.PAGE_LIMIT)
        assertFalse(TimeFitResult(emptyList()).incomplete)
    }

    @Test
    fun matchesOnlyPositiveDurationsWithinInclusiveBudget() {
        val videos = listOf(
            video("zero", "0:00"),
            video("smallest", "0:01"),
            video("below", "4:59"),
            video("at", "5:00"),
            video("above", "5:01"),
        )
        assertEquals(listOf("smallest", "below", "at"), TimeFitSearch.matches(videos, 5).map { it.id })
    }

    @Test
    fun eachSupportedBudgetHasAnInclusiveBoundary() {
        for (minutes in TimeFitSearch.minuteOptions) {
            val at = video("at", "$minutes:00")
            val below = video("below", "${minutes - 1}:59")
            assertEquals(
                listOf(at, below),
                TimeFitSearch.matches(listOf(video("above", "$minutes:01"), at, below), minutes),
            )
        }
    }

    @Test
    fun hourFormUsesHoursAndStillAllowsZeroHourDurations() {
        val videos = listOf(
            video("one-hour", "1:00:00"),
            video("above", "0:30:01"),
            video("smallest", "0:00:01"),
            video("below", "0:29:59"),
            video("at", "0:30:00"),
        )
        assertEquals(listOf("smallest", "below", "at"), TimeFitSearch.matches(videos, 30).map { it.id })
    }

    @Test
    fun rejectsLiveUnknownZeroAndMalformedDurations() {
        val invalid = listOf(
            "", " ", "\t", "LIVE", "live", "UPCOMING", "unknown", "--:--", "NaN",
            "0", "5", "300", ":", ":01", "1:", "1::01", "1:00:00:00",
            "1:2", "1:2:03", "1:02:3", "1:002", "01:000", "0:60", "0:99",
            "0:00:60", "0:60:00", "0:99:01", "0:00", "00:00", "00:00:00",
            "-1:00", "+1:00", "1:-1", "1:+1", "1:00.5", "1:00s", "1e0:00",
            " 1:00", "1:00 ", "1 :00", "1: 00", "1:\n00", "1\n:00", "1：00",
            "1:0a", "1:a0", "1:⁰¹", "٠:٦٠", "٠:٦٠:٠٠",
        )
        for (duration in invalid) {
            assertTrue("Must reject '$duration'", TimeFitSearch.matches(listOf(video("bad", duration)), 30).isEmpty())
        }
    }

    @Test
    fun rejectsComponentAndAccumulatedRuntimeOverflow() {
        val overflowing = listOf(
            "9223372036854775808:00",
            "18446744073709551617:00",
            "999999999999999999999999999999999999:00",
            "9223372036854775807:00",
            // Unchecked Long arithmetic would wrap these runtimes to 60 seconds.
            "307445734561825861:16",
            "5124095576030431:01:16",
            "١٨٤٤٦٧٤٤٠٧٣٧٠٩٥٥١٦١٧:٠٠",
        )
        for (duration in overflowing) {
            assertTrue(
                "Must reject overflow '$duration'",
                TimeFitSearch.matches(listOf(video("bad", duration)), 5).isEmpty()
            )
        }
    }

    @Test
    fun acceptsLocalizedDecimalDigits() {
        for (duration in listOf("٥:٠٠", "۰:۰۴:۵۹", "४:५९", "০:০১", "０:０１")) {
            val candidate = video("localized", duration)
            assertEquals("Must accept '$duration'", listOf(candidate), TimeFitSearch.matches(listOf(candidate), 5))
        }
        assertTrue(TimeFitSearch.matches(listOf(video("above", "٥:٠١")), 5).isEmpty())
    }

    @Test
    fun acceptsNativeStyleLocaleFormattingWithoutChangingDefaultLocale() {
        for (language in listOf("en", "ar", "fa", "bn")) {
            val locale = Locale.forLanguageTag(language)
            val minuteForm = video("minutes", "%d:%02d".format(locale, 5, 0))
            val hourForm = video("hours", "%d:%02d:%02d".format(locale, 0, 5, 0))
            assertEquals(listOf(minuteForm, hourForm), TimeFitSearch.matches(listOf(minuteForm, hourForm), 5))
        }
    }

    @Test
    fun preservesRelevanceDeduplicatesByIdAndCapsResults() {
        val first = video("first", "4:59")
        val second = video("second", "0:10")
        val third = video("third", "2:00")
        val result = TimeFitSearch.matches(
            listOf(video("too-long", "5:01"), first, second, first.copy(duration = "0:01"), third, video("fourth")),
            5,
        )
        assertEquals(listOf(first, second, third), result)
        assertSame(first, result.first())
    }

    @Test
    fun invalidDuplicateDoesNotHideLaterKnownRuntime() {
        val known = video("same", "1:00")
        assertEquals(listOf(known), TimeFitSearch.matches(listOf(known.copy(duration = "LIVE"), known), 5))
    }

    @Test
    fun shortsRemainEligibleAndExclusionsDoNotConsumeSlots() {
        val hidden = (1..4).map { video("hidden-$it") }
        val visible = listOf(video("short", "0:15", isShort = true), video("second"), video("third"))
        assertEquals(
            visible,
            TimeFitSearch.matches(hidden + visible + video("fourth"), 5, hidden.map { it.id }.toSet()),
        )
    }

    @Test
    fun matchesRejectsUnsupportedBudgets() {
        for (minutes in listOf(Int.MIN_VALUE, -1, 0, 1, 4, 6, 20, 31, 60, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { TimeFitSearch.matches(emptyList(), minutes) }
        }
    }

    @Test
    fun findRejectsUnsupportedBudgetsBeforeCallingSearch() {
        for (minutes in listOf(-1, 0, 1, 6, 20, 60, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    TimeFitSearch.find("query", minutes, fetchPage = { _, _ ->
                        throw AssertionError("Invalid budget must not fetch")
                    }, fallback = ::unexpectedFallback)
                }
            }
        }
    }

    @Test
    fun findRejectsBlankQueriesBeforeCallingSearch() {
        for (query in listOf("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    TimeFitSearch.find(query, 5, fetchPage = { _, _ ->
                        throw AssertionError("Blank query must not fetch")
                    }, fallback = ::unexpectedFallback)
                }
            }
        }
    }

    @Test
    fun stopsAfterEnoughMatchesOnFirstPage() = runBlocking {
        val expected = listOf(video("first"), video("second"), video("third"))
        var calls = 0
        val result = TimeFitSearch.find("query", 5, fetchPage = { query, next ->
            calls++
            assertEquals("query", query)
            assertNull(next)
            SearchPage(expected + video("fourth"), Page("next"))
        }, fallback = ::unexpectedFallback)
        assertEquals(TimeFitResult(expected), result)
        assertEquals(1, calls)
    }

    @Test
    fun stopsAsSoonAsEnoughDistinctUnexcludedMatchesArriveAcrossPages() = runBlocking {
        val nextPage = Page("next")
        val first = video("first", "4:59", isShort = true)
        val second = video("second", "0:01")
        val third = video("third", "5:00")
        var calls = 0
        val result = TimeFitSearch.find("query", 5, setOf("hidden"), fetchPage = { _, next ->
            calls++
            when (calls) {
                1 -> {
                    assertNull(next)
                    SearchPage(listOf(video("hidden"), first), nextPage)
                }

                2 -> {
                    assertSame(nextPage, next)
                    SearchPage(
                        listOf(
                            first.copy(duration = "0:10"),
                            video("long", "5:01"),
                            second,
                            third,
                            video("extra")
                        ), Page("unused")
                    )
                }

                else -> throw AssertionError("Must stop once three matches are available")
            }
        }, fallback = ::unexpectedFallback)
        assertEquals(TimeFitResult(listOf(first, second, third)), result)
        assertEquals(2, calls)
    }

    @Test
    fun scansPastEmptyAndUnmatchedPagesAndForwardsQueryAndCursor() = runBlocking {
        val query = "  focused learning  "
        val secondPage = Page("second")
        val thirdPage = Page("third")
        val expected = video("match")
        var calls = 0
        val result = TimeFitSearch.find(query, 5, fetchPage = { receivedQuery, next ->
            assertEquals(query, receivedQuery)
            calls++
            when (calls) {
                1 -> {
                    assertNull(next)
                    SearchPage(listOf(video("long", "6:00")), secondPage)
                }

                2 -> {
                    assertSame(secondPage, next)
                    SearchPage(emptyList(), thirdPage)
                }

                3 -> {
                    assertSame(thirdPage, next)
                    SearchPage(listOf(expected), null)
                }

                else -> throw AssertionError("Unexpected extra page")
            }
        }, fallback = ::unexpectedFallback)
        assertEquals(TimeFitResult(listOf(expected)), result)
        assertEquals(3, calls)
    }

    @Test
    fun nullOrInvalidContinuationStopsImmediately() = runBlocking {
        for (continuation in listOf(null, Page(""), Page(emptyList<String>()))) {
            val expected = video("match")
            var calls = 0
            val result = TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
                calls++
                SearchPage(listOf(expected), continuation)
            }, fallback = ::unexpectedFallback)
            assertEquals(TimeFitResult(listOf(expected)), result)
            assertEquals(1, calls)
        }
    }

    @Test
    fun idsOnlyContinuationIsValid() = runBlocking {
        val continuation = Page(listOf("next-id"))
        val expected = video("match")
        var calls = 0
        val result = TimeFitSearch.find("query", 5, fetchPage = { _, next ->
            calls++
            if (calls == 1) {
                SearchPage(emptyList(), continuation)
            } else {
                assertSame(continuation, next)
                SearchPage(listOf(expected), null)
            }
        }, fallback = ::unexpectedFallback)
        assertEquals(TimeFitResult(listOf(expected)), result)
        assertEquals(2, calls)
    }

    @Test
    fun successfulEmptySearchDoesNotUseFallback() = runBlocking {
        var calls = 0
        val result = TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
            calls++
            SearchPage(listOf(video("live", "LIVE"), video("long", "6:00")), null)
        }, fallback = ::unexpectedFallback)
        assertEquals(TimeFitResult(emptyList()), result)
        assertEquals(1, calls)
    }

    @Test
    fun pageCapIsRespectedWithNoHitsOrRepeatedHits() = runBlocking {
        for (pageVideos in listOf(emptyList(), listOf(video("same")))) {
            var calls = 0
            val result = TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
                calls++
                SearchPage(pageVideos, Page("page-$calls"))
            }, fallback = ::unexpectedFallback)
            assertEquals(TimeFitResult(pageVideos), result)
            assertEquals(3, calls)
        }
    }

    @Test
    fun firstPageFailureUsesFallbackWithSameMatchingRulesAndQuery() = runBlocking {
        val query = "focused learning"
        val expected = listOf(video("short", "0:20", isShort = true), video("localized", "٥:٠٠"), video("third"))
        var nativeCalls = 0
        var fallbackCalls = 0
        val result = TimeFitSearch.find(query, 5, setOf("hidden"), fetchPage = { receivedQuery, next ->
            nativeCalls++
            assertEquals(query, receivedQuery)
            assertNull(next)
            throw IOException("Native search unavailable")
        }, fallback = { receivedQuery ->
            fallbackCalls++
            assertEquals(query, receivedQuery)
            listOf(video("hidden"), video("live", "LIVE"), video("long", "5:01"), video("malformed", "1:99")) +
                    listOf(expected[0], expected[0].copy(duration = "0:01"), expected[1], expected[2], video("extra"))
        })
        assertEquals(TimeFitResult(expected), result)
        assertEquals(1, nativeCalls)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun successfulFallbackCanHaveNoEligibleMatches() = runBlocking {
        val result = TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
            throw IOException("Native search unavailable")
        }, fallback = { listOf(video("live", "LIVE"), video("long", "5:01")) })
        assertEquals(TimeFitResult(emptyList()), result)
    }

    @Test
    fun fallbackFailurePropagates() {
        val failure = IOException("Fallback unavailable too")
        val thrown = assertThrows(IOException::class.java) {
            runBlocking {
                TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
                    throw IllegalStateException("Native search unavailable")
                }, fallback = { throw failure })
            }
        }
        assertSame(failure, thrown)
    }

    @Test
    fun laterPageFailureReturnsExistingHitsAsIncompleteWithoutFallback() = runBlocking {
        for (failingPage in 2..3) {
            val expected = video("match")
            var calls = 0
            val result = TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
                calls++
                if (calls == failingPage) throw IOException("Later page unavailable")
                SearchPage(listOf(expected), Page("page-$calls"))
            }, fallback = ::unexpectedFallback)
            assertEquals(TimeFitResult(listOf(expected), incomplete = true), result)
            assertEquals(failingPage, calls)
        }
    }

    @Test
    fun laterPageFailureWithoutEligibleHitsPropagatesInsteadOfReportingNoMatches() {
        for (failingPage in 2..3) {
            val failure = IOException("Later page unavailable")
            var calls = 0
            val thrown = assertThrows(IOException::class.java) {
                runBlocking {
                    TimeFitSearch.find("query", 5, setOf("hidden"), fetchPage = { _, _ ->
                        calls++
                        if (calls == failingPage) throw failure
                        SearchPage(
                            listOf(video("hidden"), video("live", "LIVE"), video("long", "5:01")),
                            Page("page-$calls")
                        )
                    }, fallback = ::unexpectedFallback)
                }
            }
            assertSame(failure, thrown)
            assertEquals(failingPage, calls)
        }
    }

    @Test
    fun firstPageCancellationIsRethrownWithoutFallback() {
        val cancellation = CancellationException("Cancelled first page")
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                TimeFitSearch.find(
                    "query",
                    5,
                    fetchPage = { _, _ -> throw cancellation },
                    fallback = ::unexpectedFallback
                )
            }
        }
        assertSame(cancellation, thrown)
    }

    @Test
    fun laterPageCancellationIsRethrownEvenWithExistingHits() {
        val cancellation = CancellationException("Cancelled later page")
        var calls = 0
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
                    calls++
                    if (calls == 2) throw cancellation
                    SearchPage(listOf(video("match")), Page("next"))
                }, fallback = ::unexpectedFallback)
            }
        }
        assertSame(cancellation, thrown)
        assertEquals(2, calls)
    }

    @Test
    fun fallbackCancellationIsRethrown() {
        val cancellation = CancellationException("Cancelled fallback")
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
                    throw IOException("Native search unavailable")
                }, fallback = { throw cancellation })
            }
        }
        assertSame(cancellation, thrown)
    }

    @Test
    fun cancellingSuspendedSearchDoesNotStartFallback() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var fallbackCalls = 0
        var returned = false
        val job = launch {
            TimeFitSearch.find("query", 5, fetchPage = { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }, fallback = {
                fallbackCalls++
                emptyList()
            })
            returned = true
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(returned)
        assertEquals(0, fallbackCalls)
    }

    private fun video(id: String, duration: String = "1:00", isShort: Boolean = false) =
        Video(id, "Title $id", "Channel", duration, "", isShort = isShort)

    private suspend fun unexpectedFallback(query: String): List<Video> =
        throw AssertionError("Unexpected fallback for '$query'")
}
