package com.hikari.app

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun backgroundPlaybackUsesNativeForEveryPlayerPreferenceAndVideoType() {
        listOf(false, true).forEach { isShort ->
            listOf(false, true).forEach { embedFirst ->
                assertFalse(PlaybackPolicy.useEmbed(isShort, embedFirst, backgroundPlayback = true))
            }
        }
    }

    @Test
    fun disablingBackgroundPlaybackPreservesEmbedAndShortsPreferences() {
        assertTrue(PlaybackPolicy.useEmbed(isShort = false, embedFirst = true, backgroundPlayback = false))
        assertFalse(PlaybackPolicy.useEmbed(isShort = false, embedFirst = false, backgroundPlayback = false))
        assertTrue(PlaybackPolicy.useEmbed(isShort = true, embedFirst = true, backgroundPlayback = false))
        assertTrue(PlaybackPolicy.useEmbed(isShort = true, embedFirst = false, backgroundPlayback = false))
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
