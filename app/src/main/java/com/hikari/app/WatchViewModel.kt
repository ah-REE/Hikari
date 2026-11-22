package com.hikari.app

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@UnstableApi
class WatchViewModel(app: Application) : AndroidViewModel(app) {
    data class State(val video: Video? = null, val resolved: ResolvedVideo? = null, val loading: Boolean = false, val error: String? = null, val choice: Int = 0, val engine: String = "embed", val embedError: String? = null, val audioTrackId: String? = null, val detailsLoading: Boolean = false, val detailsError: String? = null, val metadata: WatchMetadata? = null, val metadataLoading: Boolean = false, val metadataError: Boolean = false)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    val controller = MutableStateFlow<MediaController?>(null)
    private val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    private var job: Job? = null
    private var detailsJob: Job? = null
    private var metadataJob: Job? = null
    private var switchPosition: Long? = null
    private var selectedCaption: Caption? = null
    init {
        future.addListener({
            runCatching { future.get() }.onSuccess { player ->
                controller.value = player
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { mutable.update { it.copy(error = "Playback failed. Check your connection and retry. (${error.errorCodeName})") } }
                })
                if (mutable.value.video != null) {
                    player.pause(); player.stop(); player.clearMediaItems()
                    if (mutable.value.engine == "native") mutable.value.resolved?.let { start(it, mutable.value.choice) }
                } else player.currentMediaItem?.let { item ->
                    val raw = item.mediaMetadata.extras?.getString("video")
                    if (raw != null) runCatching { org.json.JSONObject(raw).video() }.onSuccess { mutable.value = State(video = it, engine = "native") }
                }
            }.onFailure { e -> mutable.update { it.copy(error = "Could not connect to the player: ${e.message}") } }
        }, androidx.core.content.ContextCompat.getMainExecutor(app))
    }
    fun open(video: Video) {
        job?.cancel(); detailsJob?.cancel(); metadataJob?.cancel(); switchPosition = null; selectedCaption = null; controller.value?.let { it.pause(); it.stop(); it.clearMediaItems() }
        mutable.value = State(video = video, loading = true)
        job = viewModelScope.launch {
            try {
                val offline = withContext(Dispatchers.IO) { Downloads.offline(getApplication(), video.id) }
                if (offline != null) {
                    resolve(video, offline, "native")
                } else {
                    val backgroundPlayback = PreferencesStore.get(getApplication()).state.value.backgroundPlayback
                    val embedFirst = getApplication<Application>().getSharedPreferences("hikari-native", 0).getBoolean("embedFirst", true)
                    if (!backgroundPlayback && PlaybackPolicy.useEmbed(video.isShort, embedFirst)) {
                        mutable.value = State(video = video, engine = "embed")
                    } else resolveNative(video)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "Unable to load this video") } }
        }
    }
    fun loadMetadata() {
        val video = mutable.value.video ?: return
        if (mutable.value.metadataLoading) return
        metadataJob?.cancel()
        mutable.update { it.copy(metadataLoading = true, metadataError = false) }
        metadataJob = viewModelScope.launch {
            try {
                val metadata = WatchMetadataRepository.load(video)
                mutable.update { if (it.video?.id == video.id) it.copy(video = metadata.video, metadata = metadata, metadataLoading = false) else it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { if (it.video?.id == video.id) it.copy(metadataLoading = false, metadataError = true) else it } }
        }
    }
    fun embedMetadata(title: String, author: String) { mutable.update { state -> state.copy(video = state.video?.let { it.copy(title = title.ifBlank { it.title }, channel = author.ifBlank { it.channel }) }) } }
    fun embedFailed(code: Int) { mutable.update { it.copy(embedError = "YouTube embed error $code") } }
    fun fallbackToNative() { mutable.value.video?.let { resolveNative(it) } }
    fun fallbackToNative(position: Long) { switchPosition = position; fallbackToNative() }
    /** Fetch existing extractor metadata for sheets without replacing embed playback. */
    fun loadDetails() {
        val current = mutable.value
        val video = current.video ?: return
        if (current.resolved != null || current.detailsLoading || current.loading) return
        detailsJob?.cancel()
        mutable.update { it.copy(detailsLoading = true, detailsError = null) }
        detailsJob = viewModelScope.launch {
            try {
                val result = Extractor.resolve(video, current.audioTrackId)
                mutable.update { if (it.video?.id == video.id) it.copy(video = result.video, resolved = result, detailsLoading = false) else it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { if (it.video?.id == video.id) it.copy(detailsLoading = false, detailsError = "Video details are unavailable. Try again.") else it } }
        }
    }
    private fun resolveNative(video: Video, preferEmbedForShort: Boolean = false) {
        job?.cancel(); detailsJob?.cancel()
        mutable.update { it.copy(video = video, engine = "native", loading = true, embedError = null, error = null, detailsLoading = false) }
        job = viewModelScope.launch {
            try {
                val resolved = Extractor.resolve(video, mutable.value.audioTrackId)
                // A /watch link can resolve to a Short, even when native playback is preferred.
                // Explicit restriction fallback remains native so blocked embeds can still play.
                if (preferEmbedForShort && resolved.video.isShort) mutable.value = State(video = resolved.video, resolved = resolved, engine = "embed")
                else resolve(video, null, "native", resolved)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "Unable to load this video") } }
        }
    }
    private fun resolve(video: Video, offline: Pair<Video, StreamChoice>?, engine: String, extracted: ResolvedVideo? = null) {
        val resolved = extracted ?: offline?.let { ResolvedVideo(it.first, "Available offline", listOf(it.second)) } ?: return
        val previous = mutable.value
        mutable.value = previous.copy(video = resolved.video, resolved = resolved, engine = engine, loading = false, error = null, audioTrackId = previous.audioTrackId)
        start(resolved, 0, switchPosition)
        switchPosition = null
    }
    fun selectAudio(trackId: String?) {
        val video = mutable.value.video ?: return
        if (switchPosition == null) switchPosition = controller.value?.currentPosition
        mutable.update { it.copy(audioTrackId = trackId) }
        resolveNative(video)
    }
    fun selectAudio(trackId: String?, position: Long) { switchPosition = position; selectAudio(trackId) }
    fun select(index: Int, seek: Long? = null) {
        if (mutable.value.engine != "native") return
        val result = mutable.value.resolved ?: return
        if (index !in result.choices.indices) return
        val position = seek ?: controller.value?.currentPosition ?: 0L
        mutable.update { it.copy(choice = index, error = null, engine = "native", embedError = null) }
        start(result, index, position)
    }
    fun selectCaption(caption: Caption?, position: Long) {
        selectedCaption = caption
        val result = mutable.value.resolved ?: return
        mutable.update { it.copy(engine = "native", error = null, embedError = null) }
        start(result, mutable.value.choice, position)
    }
    private fun start(result: ResolvedVideo, index: Int, seek: Long? = null) {
        val player = controller.value ?: return
        val choice = result.choices[index]
        val savedPosition = Library.get(getApplication()).state.value.history.firstOrNull { it.video.id == result.video.id }?.position ?: 0L
        // A zero passed by a fresh player/surface is not a deliberate restart. Preserve History.
        val position = (seek?.takeIf { it > 0L } ?: savedPosition).coerceAtLeast(0L)
        val extras = Bundle().apply { putString("audio", choice.audio); putString("video", result.video.json().toString()) }
        val metadata = MediaMetadata.Builder().setTitle(result.video.title).setArtist(result.video.channel).setArtworkUri(android.net.Uri.parse(result.video.thumbnail)).setExtras(extras).build()
        val item = MediaItem.Builder().setMediaId(result.video.id).setUri(choice.url).setMimeType(choice.mime).setMediaMetadata(metadata).setSubtitleConfigurations(listOfNotNull(selectedCaption).map { caption -> MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(caption.url)).setLanguage(caption.language).setLabel(caption.name).setMimeType(caption.mime).build() }).build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, selectedCaption == null)
            .setPreferredTextLanguage(selectedCaption?.language).build()
        player.pause(); player.setMediaItem(item, position)
        player.setPlaybackSpeed(PreferencesStore.get(getApplication()).state.value.defaultSpeed)
        player.prepare()
        if (!PlaybackPolicy.shouldPause(PlaybackPolicy.playerVisible.value, PreferencesStore.get(getApplication()).state.value.backgroundPlayback)) player.play()
    }
    fun close() { job?.cancel(); detailsJob?.cancel(); metadataJob?.cancel(); controller.value?.stop(); controller.value?.clearMediaItems(); mutable.value = State() }
    override fun onCleared() { job?.cancel(); detailsJob?.cancel(); metadataJob?.cancel(); MediaController.releaseFuture(future) }
}
