package com.hikari.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object PlaybackPolicy {
    private val visible = MutableStateFlow(false)
    val playerVisible = visible.asStateFlow()

    fun setPlayerVisible(value: Boolean) {
        visible.value = value
    }

    fun useEmbed(isShort: Boolean, embedFirst: Boolean) = isShort || embedFirst

    fun shouldPause(playerVisible: Boolean, backgroundPlayback: Boolean) =
        !playerVisible && !backgroundPlayback

    fun autoEnterPip(hasVideo: Boolean, backgroundPlayback: Boolean) =
        hasVideo && !backgroundPlayback
}
