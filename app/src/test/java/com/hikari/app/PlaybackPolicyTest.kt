package com.hikari.app

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun backgroundPlaybackSettingDoesNotOverridePlayerChoice() {
        assertTrue(PlaybackPolicy.useEmbed(isShort = false, embedFirst = true))
        assertFalse(PlaybackPolicy.useEmbed(isShort = false, embedFirst = false))
        assertTrue(PlaybackPolicy.useEmbed(isShort = true, embedFirst = false))
    }

    @Test
    fun disablingBackgroundPlaybackPreservesEmbedAndShortsPreferences() {
        assertTrue(PlaybackPolicy.useEmbed(isShort = false, embedFirst = true))
        assertFalse(PlaybackPolicy.useEmbed(isShort = false, embedFirst = false))
        assertTrue(PlaybackPolicy.useEmbed(isShort = true, embedFirst = true))
        assertTrue(PlaybackPolicy.useEmbed(isShort = true, embedFirst = false))
    }

    @Test
    fun visiblePlaybackIncludingPictureInPictureDoesNotRequireBackgroundPermission() {
        listOf(false, true).forEach { backgroundPlayback ->
            assertFalse(PlaybackPolicy.shouldPause(playerVisible = true, backgroundPlayback))
        }
    }

    @Test
    fun hiddenPlaybackPausesUnlessBackgroundPlaybackIsEnabled() {
        assertTrue(PlaybackPolicy.shouldPause(playerVisible = false, backgroundPlayback = false))
        assertFalse(PlaybackPolicy.shouldPause(playerVisible = false, backgroundPlayback = true))
    }

    @Test
    fun backgroundAudioDoesNotAutomaticallyOpenPictureInPicture() {
        assertFalse(PlaybackPolicy.autoEnterPip(hasVideo = true, backgroundPlayback = true))
        assertTrue(PlaybackPolicy.autoEnterPip(hasVideo = true, backgroundPlayback = false))
        listOf(false, true).forEach { backgroundPlayback ->
            assertFalse(PlaybackPolicy.autoEnterPip(hasVideo = false, backgroundPlayback))
        }
    }
}
