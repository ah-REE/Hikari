package com.hikari.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HikariState(
    val query: String = "",
    val videos: List<Video> = emptyList(),
    val saved: List<Video> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val dark: Boolean = true,
    val themeMode: String = "system",
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val recents: List<String> = emptyList(),
    val reduceMotion: Boolean = false,
    val feed: List<Video> = emptyList(),
    val feedLoading: Boolean = false,
    val feedFailures: Int = 0,
    val searchGeneration: Long = 0
) {
    internal fun beginSearch(value: String): HikariState {
        val query = value.trim()
        require(query.isNotEmpty())
        return copy(
            query = query, videos = emptyList(), loading = true, loadingMore = false,
            error = null, searched = true, hasMore = false, searchGeneration = searchGeneration + 1
        )
    }

    internal fun restoreSearch(previous: HikariState, hasMore: Boolean) = copy(
        query = previous.query, videos = previous.videos, searched = true, loading = false,
        loadingMore = false, error = previous.error, hasMore = hasMore, searchGeneration = searchGeneration + 1
    )
}

data class TimeFitState(
    val topic: String = "",
    val minutes: Int = 10,
    val videos: List<Video> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val incomplete: Boolean = false,
    val excludingSeen: Boolean = false
)

class HikariViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)
    private val preferencesStore = PreferencesStore.get(application)
    private val prefs = application.getSharedPreferences("hikari-native", 0)
    private val mutable = MutableStateFlow(
        HikariState(
            saved = repository.saved(),
            dark = repository.dark(),
            themeMode = repository.themeMode(),
            reduceMotion = prefs.getBoolean("reduceMotion", false),
            recents = runCatching {
                org.json.JSONArray(prefs.getString("searchRecents", "[]"))
                    .let { a -> (0 until a.length()).map { a.getString(it) } }
            }.getOrDefault(emptyList())
        )
    )
    val state = mutable.asStateFlow()
    private val mutableTimeFit = MutableStateFlow(TimeFitState())
    val timeFit = mutableTimeFit.asStateFlow()
    private var timeFitJob: Job? = null
    private val timeFitHistory = TimeFitHistory()
    private var nextPage: org.schabi.newpipe.extractor.Page? = null
    private var searchJob: Job? = null

    private data class SearchEntry(val state: HikariState, val next: org.schabi.newpipe.extractor.Page?)

    private val searchBackStack = ArrayDeque<SearchEntry>()

    init {
        viewModelScope.launch {
            preferencesStore.state.map { it.timeFitFinder }.distinctUntilChanged().collect { enabled ->
                if (!enabled) {
                    timeFitJob?.cancel()
                    mutableTimeFit.update {
                        it.copy(
                            videos = emptyList(),
                            loading = false,
                            searched = false,
                            error = null,
                            incomplete = false,
                            excludingSeen = false
                        )
                    }
                }
            }
        }
    }

    fun previousSearch(): Boolean {
        if (searchBackStack.isEmpty()) return false
        searchJob?.cancel()
        val entry = searchBackStack.removeLast(); nextPage = entry.next
        mutable.update { it.restoreSearch(entry.state, hasMore = entry.next != null) }
        if (entry.state.loading) search(entry.state.query)
        return true
    }

    private var feedJob: Job? = null
    fun refreshFeed(subscriptions: List<Subscription>) {
        feedJob?.cancel()
        if (subscriptions.isEmpty()) {
            mutable.update { it.copy(feed = emptyList(), feedLoading = false, feedFailures = 0) }; return
        }
        feedJob = viewModelScope.launch {
            mutable.update { it.copy(feedLoading = true) }
            try {
                val result = SubscriptionFeed.load(subscriptions); mutable.update {
                    it.copy(
                        feed = result.first,
                        feedFailures = result.second,
                        feedLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.update { it.copy(feedLoading = false, feedFailures = subscriptions.size) }
            }
        }
    }

    fun clearRecents() {
        prefs.edit().remove("searchRecents").apply(); mutable.update { it.copy(recents = emptyList()) }
    }

    fun reduceMotion(value: Boolean) {
        prefs.edit().putBoolean("reduceMotion", value).apply(); mutable.update { it.copy(reduceMotion = value) }
    }

    fun setTimeFitTopic(value: String) {
        val current = mutableTimeFit.value
        if (value == current.topic) return
        timeFitJob?.cancel()
        mutableTimeFit.value = TimeFitState(topic = value, minutes = current.minutes)
    }

    fun setTimeFitMinutes(value: Int) {
        val current = mutableTimeFit.value
        if (value !in TimeFitSearch.minuteOptions || value == current.minutes) return
        timeFitJob?.cancel()
        mutableTimeFit.value = TimeFitState(topic = current.topic, minutes = value)
    }

    fun findTimeFit() {
        val current = mutableTimeFit.value
        val query = current.topic.trim()
        if (query.isBlank() || !preferencesStore.state.value.timeFitFinder) return
        val shown = timeFitHistory.shownIds(query, current.minutes)
        timeFitJob?.cancel()
        mutableTimeFit.value = current.copy(
            topic = query,
            videos = emptyList(),
            loading = true,
            searched = true,
            error = null,
            incomplete = false,
            excludingSeen = shown.isNotEmpty()
        )
        timeFitJob = viewModelScope.launch {
            try {
                val preferences = preferencesStore.state.value
                val excluded =
                    if (preferences.hideWatched) Library.get(getApplication()).state.value.history.map { it.video.id }
                        .toSet() else emptySet()
                val result = TimeFitSearch.find(
                    query, current.minutes, excluded + shown,
                    fetchPage = { topic, page -> NativeSearch.search(topic, page) },
                    fallback = repository::search
                )
                coroutineContext.ensureActive()
                if (!preferencesStore.state.value.timeFitFinder) return@launch
                timeFitHistory.remember(query, current.minutes, result.videos)
                mutableTimeFit.update {
                    it.copy(
                        videos = result.videos,
                        loading = false,
                        incomplete = result.incomplete
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableTimeFit.update { it.copy(loading = false, error = "Check your connection and try again.") }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        val current = mutable.value
        if (current.searched && current.query != query.trim()) {
            searchBackStack.addLast(SearchEntry(current, nextPage))
            if (searchBackStack.size > 10) searchBackStack.removeFirst()
        }
        val recents = (listOf(query.trim()) + mutable.value.recents).distinct().take(12)
        prefs.edit().putString("searchRecents", org.json.JSONArray(recents).toString()).apply()
        mutable.update { it.copy(recents = recents) }
        searchJob?.cancel(); nextPage = null
        searchJob = viewModelScope.launch {
            mutable.update { it.beginSearch(query) }
            try {
                val result = try {
                    NativeSearch.search(query.trim(), includeShortsShelves = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    SearchPage(repository.search(query.trim()), null)
                }
                nextPage = result.next
                mutable.update { it.copy(videos = result.videos, loading = false, hasMore = result.next != null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = e.message ?: "Could not connect. Please retry.", loading = false) }
            }
        }
    }

    fun clearSearch() {
        searchBackStack.clear(); searchJob?.cancel(); nextPage = null; mutable.update {
            it.copy(
                query = "",
                videos = emptyList(),
                loading = false,
                searched = false,
                error = null,
                hasMore = false,
                loadingMore = false
            )
        }
    }

    fun more() {
        val page = nextPage ?: return
        if (mutable.value.loadingMore || mutable.value.loading) return
        searchJob = viewModelScope.launch {
            mutable.update { it.copy(loadingMore = true, error = null) }
            try {
                val result = NativeSearch.search(mutable.value.query, page, includeShortsShelves = true)
                nextPage = result.next
                mutable.update {
                    it.copy(
                        videos = (it.videos + result.videos).distinctBy { v -> v.id },
                        loadingMore = false,
                        hasMore = result.next != null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loadingMore = false, error = "Couldn't load more videos. ${e.message}") }
            }
        }
    }

    fun toggleSave(video: Video) {
        mutable.update { s ->
            s.copy(saved = if (s.saved.any { it.id == video.id }) s.saved.filterNot { it.id == video.id } else listOf(
                video
            ) + s.saved)
        }
        repository.save(mutable.value.saved)
    }

    fun importSaved(videos: List<Video>) {
        mutable.update { it.copy(saved = (it.saved + videos).distinctBy { v -> v.id }) }; repository.save(mutable.value.saved)
    }

    fun clearLibrary() {
        clearRecents(); mutable.update { it.copy(saved = emptyList()) }; repository.save(emptyList())
    }

    fun theme(mode: String) {
        repository.setThemeMode(mode)
        mutable.update { it.copy(themeMode = mode, dark = repository.dark()) }
    }
}
