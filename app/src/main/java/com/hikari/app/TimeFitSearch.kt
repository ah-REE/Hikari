package com.hikari.app

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.schabi.newpipe.extractor.Page

/** [incomplete] marks a later-page failure, not the normal bounded search cutoff. */
data class TimeFitResult(val videos: List<Video>, val incomplete: Boolean = false)

object TimeFitSearch {
    val minuteOptions = listOf(5, 10, 15, 30)
    const val RESULT_LIMIT = 3
    const val PAGE_LIMIT = 3

    fun matches(
        videos: List<Video>,
        minutes: Int,
        excludedIds: Set<String> = emptySet(),
    ): List<Video> {
        require(minutes in minuteOptions) { "Unsupported time budget: $minutes minutes" }
        val budgetSeconds = minutes * 60L
        return videos.asSequence()
            .filter { it.id !in excludedIds }
            .filter { video -> knownDurationSeconds(video.duration)?.let { it <= budgetSeconds } == true }
            .distinctBy { it.id }
            .take(RESULT_LIMIT)
            .toList()
    }

    suspend fun find(
        query: String,
        minutes: Int,
        excludedIds: Set<String> = emptySet(),
        fetchPage: suspend (String, Page?) -> SearchPage,
        fallback: suspend (String) -> List<Video>,
    ): TimeFitResult {
        require(minutes in minuteOptions) { "Unsupported time budget: $minutes minutes" }
        require(query.isNotBlank()) { "Search query must not be blank" }
        val context = currentCoroutineContext()
        var hits = emptyList<Video>()
        var next: Page? = null
        repeat(PAGE_LIMIT) { pageIndex ->
            context.ensureActive()
            val page = try {
                fetchPage(query, next)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                context.ensureActive()
                if (pageIndex == 0) {
                    val videos = fallback(query)
                    context.ensureActive()
                    return TimeFitResult(matches(videos, minutes, excludedIds))
                }
                if (hits.isEmpty()) throw failure
                return TimeFitResult(hits, incomplete = true)
            }
            context.ensureActive()
            hits = matches(hits + page.videos, minutes, excludedIds)
            if (hits.size == RESULT_LIMIT) return TimeFitResult(hits)
            next = page.next?.takeIf { Page.isValid(it) } ?: return TimeFitResult(hits)
        }
        return TimeFitResult(hits)
    }

    // NativeSearch emits M:SS or H:MM:SS, using the default locale's decimal digits.
    private fun knownDurationSeconds(duration: String): Long? {
        val parts = duration.split(':')
        if (parts.size !in 2..3) return null
        var seconds = 0L
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty() || (index > 0 && part.length != 2)) return null
            var value = 0L
            for (character in part) {
                val digit = character.digitToIntOrNull() ?: return null
                if (value > (Long.MAX_VALUE - digit) / 10) return null
                value = value * 10 + digit
            }
            if (index > 0 && value >= 60) return null
            if (seconds > (Long.MAX_VALUE - value) / 60) return null
            seconds = seconds * 60 + value
        }
        return seconds.takeIf { it > 0 }
    }
}

/** Session-only history keeps repeat searches fresh without changing watch history. */
class TimeFitHistory {
    private data class Criteria(val topic: String, val minutes: Int)

    private val shown = linkedMapOf<Criteria, Set<String>>()

    fun shownIds(topic: String, minutes: Int): Set<String> =
        shown[Criteria(topic.trim().lowercase(Locale.ROOT), minutes)].orEmpty().toSet()

    fun remember(topic: String, minutes: Int, videos: List<Video>) {
        if (videos.isEmpty()) return
        val key = Criteria(topic.trim().lowercase(Locale.ROOT), minutes)
        val previous = shown.remove(key).orEmpty()
        shown[key] = previous + videos.map { it.id }
        // Keep only recent search criteria; this is not a persistent viewing profile.
        while (shown.size > 20) shown.remove(shown.keys.first())
    }
}
