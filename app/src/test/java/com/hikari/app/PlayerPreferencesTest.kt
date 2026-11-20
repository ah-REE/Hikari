package com.hikari.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerPreferencesTest {
    @Test
    fun timeFitFinderDefaultsOffForFreshAndLegacyPreferences() {
        assertFalse(PlayerPreferences().timeFitFinder)
        assertFalse(PlayerPreferences().json().getBoolean("enableTimeFitFinder"))
        assertFalse(PlayerPreferences.fromJson(JSONObject()).timeFitFinder)
        val legacy =
            PlayerPreferences.fromJson(JSONObject("{\"defaultSpeed\":1.5,\"defaultHdr\":true,\"enableScrollToTop\":false}"))
        assertFalse(legacy.timeFitFinder)
        assertEquals(1.5f, legacy.defaultSpeed)
        assertTrue(legacy.enhancedColors)
        assertFalse(legacy.scrollToTop)
    }

    @Test
    fun timeFitFinderRoundTripsEnabledAndDisabled() {
        listOf(false, true).forEach { enabled ->
            val value = PlayerPreferences(timeFitFinder = enabled)
            val json = JSONObject(value.json().toString())
            assertEquals(enabled, json.getBoolean("enableTimeFitFinder"))
            assertEquals(value, PlayerPreferences.fromJson(json))
        }
    }

    @Test
    fun copyingOtherPreferencesPreservesTimeFitFinder() {
        listOf(false, true).forEach { enabled ->
            val updated = PlayerPreferences(timeFitFinder = enabled).copy(
                defaultSpeed = 1.5f, seekSeconds = 30, focusMode = true, autoplayNext = true,
                hideWatched = true, hideShorts = true, showDislikes = true, enhancedColors = true, scrollToTop = false
            )
            assertEquals(enabled, updated.timeFitFinder)
            assertEquals(updated, PlayerPreferences.fromJson(updated.json()))
        }
    }

    @Test
    fun backgroundPlaybackDefaultsOffForFreshAndLegacyPreferences() {
        assertFalse(PlayerPreferences().backgroundPlayback)
        assertFalse(PlayerPreferences().json().getBoolean("enableBackgroundPlayback"))
        assertFalse(PlayerPreferences.fromJson(JSONObject()).backgroundPlayback)
        val legacy = PlayerPreferences.fromJson(
            JSONObject("{\"defaultSpeed\":1.5,\"defaultHdr\":true,\"enableTimeFitFinder\":true}")
        )
        assertFalse(legacy.backgroundPlayback)
        assertTrue(legacy.timeFitFinder)
        assertEquals(1.5f, legacy.defaultSpeed)
        assertTrue(legacy.enhancedColors)
    }

    @Test
    fun backgroundPlaybackAndTimeFitFinderRoundTripAllCombinations() {
        listOf(false, true).forEach { background ->
            listOf(false, true).forEach { timeFit ->
                val value = PlayerPreferences(timeFitFinder = timeFit, backgroundPlayback = background)
                val json = JSONObject(value.json().toString())
                assertFalse(json.getBoolean("enableBackgroundPlayback"))
                assertEquals(timeFit, json.getBoolean("enableTimeFitFinder"))
                assertFalse(PlayerPreferences.fromJson(json).backgroundPlayback)
                assertEquals(timeFit, PlayerPreferences.fromJson(json).timeFitFinder)
            }
        }
    }

    @Test
    fun copyingOtherPreferencesKeepsBackgroundPlaybackOff() {
        listOf(false, true).forEach { enabled ->
            val updated = PlayerPreferences(timeFitFinder = true, backgroundPlayback = enabled).copy(
                defaultSpeed = 1.5f, seekSeconds = 30, focusMode = true, autoplayNext = true,
                hideWatched = true, hideShorts = true, showDislikes = true, enhancedColors = true, scrollToTop = false
            )
            assertTrue(updated.timeFitFinder)
            assertFalse(PlayerPreferences.fromJson(updated.json()).backgroundPlayback)
        }
    }

    @Test
    fun backupPreferenceMergePreservesAbsentFlagsAndImportsExplicitValues() {
        listOf(false, true).forEach { background ->
            listOf(false, true).forEach { timeFit ->
                val current = PlayerPreferences(timeFitFinder = timeFit, backgroundPlayback = background)
                val legacyBackup = JSONObject("{\"preferences\":{\"defaultSpeed\":1.5}}")
                val merged = current.json()
                val imported = legacyBackup.getJSONObject("preferences")
                imported.keys().forEach { key -> merged.put(key, imported.get(key)) }
                val expected = current.copy(defaultSpeed = 1.5f, backgroundPlayback = false)
                assertEquals(expected, PlayerPreferences.fromJson(merged))

                val replacement = current.copy(timeFitFinder = !timeFit, backgroundPlayback = !background)
                val backup = JSONObject(JSONObject().put("preferences", replacement.json()).toString())
                val explicit = backup.getJSONObject("preferences")
                explicit.keys().forEach { key -> merged.put(key, explicit.get(key)) }
                assertEquals(replacement.copy(backgroundPlayback = false), PlayerPreferences.fromJson(merged))
            }
        }
    }

    @Test
    fun preferencesRoundTripIncludingLegacyNames() {
        val value = PlayerPreferences(1.75f, 30, true, true, true, true, true, true, false)
        assertFalse(value.timeFitFinder)
        assertFalse(value.backgroundPlayback)
        assertEquals(value, PlayerPreferences.fromJson(value.json()))
        assertFalse(value.json().getBoolean("disableAutoplay"))
        assertTrue(PlayerPreferences.fromJson(JSONObject("{\"defaultHdr\":true}")).enhancedColors)
    }

    @Test
    fun unsafeImportedValuesUseValidDefaults() {
        val value = PlayerPreferences.fromJson(JSONObject("{\"defaultSpeed\":900,\"seekDuration\":-10}"))
        assertEquals(1f, value.defaultSpeed)
        assertEquals(10, value.seekSeconds)
        assertFalse(value.autoplayNext)
    }

    @Test
    fun retiredShortsPreferenceDoesNotHideResults() {
        val first = Video("aaaaaaaaaaa", "Watched", "", "", "")
        val short = Video("bbbbbbbbbbb", "Short", "", "", "", isShort = true)
        val fresh = Video("ccccccccccc", "Fresh", "", "", "")
        val source = listOf(first, short, fresh)
        val prefs = PlayerPreferences(hideWatched = true, hideShorts = true)
        val history = listOf(HistoryEntry(first, 1000, 1))
        assertEquals(listOf(short, fresh), filterDiscovery(source, prefs, history))
        assertEquals(listOf(short, fresh), filterDiscovery(source, prefs, history, shortsFeed = true))
        assertEquals(3, source.size)
    }
}
