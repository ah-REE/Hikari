package com.hikari.app

import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.media3.common.util.UnstableApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

@UnstableApi
class MainActivity : ComponentActivity() {
    val pip = MutableStateFlow(false)
    val pendingVideo = MutableStateFlow<Video?>(null)
    var hasVideo = false
    var onPipCommand: ((String) -> Unit)? = null
    var onPlayerHidden: (() -> Unit)? = null
    private var pipPlaying = false
    private var pipReady = false
    private var pipRatio = 16f / 9f
    private var pipRect: Rect? = null
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { if (intent.action == "$packageName.PIP") intent.getStringExtra("command")?.let { onPipCommand?.invoke(it) } }
    }
    private fun pipAction(command: String, label: String, icon: Int): RemoteAction {
        val intent = Intent("$packageName.PIP").setPackage(packageName).putExtra("command", command)
        return RemoteAction(Icon.createWithResource(this, icon), label, label, PendingIntent.getBroadcast(this, command.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).apply { isEnabled = pipReady }
    }
    private fun pipParams(): PictureInPictureParams {
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational((pipRatio.coerceIn(1f / 2.39f, 2.39f) * 10000).toInt(), 10000))
            .setActions(if (hasVideo) listOf(
                pipAction("back", "Rewind ${PreferencesStore.get(this).state.value.seekSeconds} seconds", android.R.drawable.ic_media_rew),
                if (pipPlaying) pipAction("pause", "Pause", android.R.drawable.ic_media_pause) else pipAction("play", "Play", android.R.drawable.ic_media_play),
                pipAction("forward", "Forward ${PreferencesStore.get(this).state.value.seekSeconds} seconds", android.R.drawable.ic_media_ff)) else emptyList())
        pipRect?.let { params.setSourceRectHint(it) }
        if (android.os.Build.VERSION.SDK_INT >= 31) params.setAutoEnterEnabled(autoEnterPip())
        return params.build()
    }
    fun updatePipPlayback(playing: Boolean, ready: Boolean, aspectRatio: Float = 16f / 9f) {
        if (pipPlaying == playing && pipReady == ready && pipRatio == aspectRatio) return
        pipPlaying = playing; pipReady = ready; pipRatio = aspectRatio
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) setPictureInPictureParams(pipParams())
    }
    fun enterPlayerPip(): Boolean = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) && enterPictureInPictureMode(pipParams())
    fun configurePip(active: Boolean, rect: android.graphics.Rect? = null) {
        hasVideo = active
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (rect != null) pipRect = rect
        if (!active) pipRect = null
        setPictureInPictureParams(pipParams())
    }
    private fun acceptIntent(value: Intent?) {
        val link = value?.dataString ?: value?.getStringExtra(Intent.EXTRA_TEXT)
        pendingVideo.value = VideoLinks.id(link)?.let { Video(it, "Loading video…", "", "", "", isShort = VideoLinks.isShort(link)) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent) }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) { super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); pip.value = isInPictureInPictureMode }
    private fun autoEnterPip() = PlaybackPolicy.autoEnterPip(hasVideo, PreferencesStore.get(this).state.value.backgroundPlayback)
    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (android.os.Build.VERSION.SDK_INT < 31 && autoEnterPip()) enterPlayerPip() }
    override fun onStart() { super.onStart(); PlaybackPolicy.setPlayerVisible(true) }
    override fun onStop() {
        if (!isChangingConfigurations) {
            PlaybackPolicy.setPlayerVisible(false)
            onPlayerHidden?.invoke()
        }
        super.onStop()
    }
    override fun attachBaseContext(base: Context) {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            val prefs = base.getSharedPreferences("hikari-native", 0)
            val config = Configuration(base.resources.configuration)
            val dark = prefs.getBoolean("dark", config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            super.attachBaseContext(base.createConfigurationContext(config))
        } else super.attachBaseContext(base)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter("$packageName.PIP"), ContextCompat.RECEIVER_NOT_EXPORTED)
        // A configuration recreation must not replay an already-consumed shared link.
        if (savedInstanceState == null) acceptIntent(intent)
        enableEdgeToEdge()
        setContent { HikariApp() }
    }
    override fun onDestroy() { unregisterReceiver(pipReceiver); super.onDestroy() }
}
