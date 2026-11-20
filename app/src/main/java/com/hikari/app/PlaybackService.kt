package com.hikari.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import java.net.URI

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var segments = emptyList<SponsorSegment>()
    private var segmentsJob: Job? = null
    private var lastHistoryWrite = 0L
    private val tick = object : Runnable {
        override fun run() {
            session?.player?.let { player ->
                val item = player.currentMediaItem
                if (item != null && player.currentPosition > 0) {
                    if (System.currentTimeMillis() - lastHistoryWrite > 10000) {
                        lastHistoryWrite = System.currentTimeMillis()
                        item.mediaMetadata.extras?.getString("video")?.let { raw -> runCatching { Library.get(this@PlaybackService).record(org.json.JSONObject(raw).video(), player.currentPosition) } }
                    }
                    if (getSharedPreferences("hikari-native", MODE_PRIVATE).getBoolean("sponsorBlock", false) && getSharedPreferences("hikari-native", MODE_PRIVATE).getBoolean("sponsorAutoSkip", true) && player.isPlaying) segments.firstOrNull { it.contains(player.currentPosition / 1000f) }?.let { player.seekTo((it.end * 1000).toLong()) }
                }
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val sponsorPreferences by lazy { getSharedPreferences("hikari-native", MODE_PRIVATE) }
    private val sponsorListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "sponsorBlock") loadSegments(session?.player?.currentMediaItem?.mediaId)
    }
    private fun loadSegments(id: String?) {
        segments = emptyList(); segmentsJob?.cancel()
        if (id == null || !sponsorPreferences.getBoolean("sponsorBlock", false)) return
        segmentsJob = scope.launch {
            try { val loaded = SponsorSegments.load(id); if (session?.player?.currentMediaItem?.mediaId == id) segments = loaded }
            catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }
    private fun playbackAllowed() = !PlaybackPolicy.shouldPause(
        PlaybackPolicy.playerVisible.value, PreferencesStore.get(this).state.value.backgroundPlayback
    )
    override fun onCreate() {
        super.onCreate()
        sponsorPreferences.registerOnSharedPreferenceChangeListener(sponsorListener)
        val factory = DefaultMediaSourceFactory(Downloads.cacheFactory(this))
        val merged = object : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider) = apply { factory.setDrmSessionManagerProvider(provider) }
            override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy) = apply { factory.setLoadErrorHandlingPolicy(policy) }
            override fun getSupportedTypes() = factory.supportedTypes
            override fun createMediaSource(item: MediaItem): MediaSource {
                val videoSource = factory.createMediaSource(item)
                val audio = item.mediaMetadata.extras?.getString("audio") ?: return videoSource
                return MergingMediaSource(videoSource, factory.createMediaSource(MediaItem.fromUri(audio)))
            }
        }
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(merged).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    loadSegments(mediaItem?.mediaId)

                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady && !playbackAllowed()) pause()
                }
            })
        }
        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun play() { if (playbackAllowed()) super.play() }
            override fun setPlayWhenReady(playWhenReady: Boolean) {
                super.setPlayWhenReady(playWhenReady && playbackAllowed())
            }
        }
        session = MediaSession.Builder(this, sessionPlayer).setCallback(object : MediaSession.Callback {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
                if (playerCommand == Player.COMMAND_PLAY_PAUSE && !playbackAllowed()) {
                    session.player.pause()
                    return SessionResult.RESULT_ERROR_PERMISSION_DENIED
                }
                return SessionResult.RESULT_SUCCESS
            }
        }).setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        scope.launch {
            combine(PlaybackPolicy.playerVisible, PreferencesStore.get(this@PlaybackService).state) { visible, preferences ->
                PlaybackPolicy.shouldPause(visible, preferences.backgroundPlayback)
            }.distinctUntilChanged().collect { shouldPause -> if (shouldPause) player.pause() }
        }
        handler.post(tick)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onDestroy() { sponsorPreferences.unregisterOnSharedPreferenceChangeListener(sponsorListener); handler.removeCallbacks(tick); scope.cancel(); session?.run { player.release(); release() }; session = null; super.onDestroy() }
}
