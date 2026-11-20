package com.hikari.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryCodecTest {
    @Test fun legacyBackupConvertsSecondsToMillisecondsAndChannelIds() {
        val json = JSONObject("""{"watchHistory":[{"video":{"id":"abcdefghijk","title":"Legacy","channelTitle":"Creator","channelId":"UC123"},"progress":12.5,"watchedAt":123}],"subscriptions":[{"id":"UC123","name":"Creator"}]}""")
        val result = LibraryCodec.decode(json)
        assertEquals(12500L, result.history.first().position)
        assertEquals("https://www.youtube.com/channel/UC123", result.subscriptions.first().url)
        assertEquals("Creator", result.history.first().video.channel)
    }
    @Test fun roundTripPreservesPlaylistsAndHistory() {
        val v = Video("abcdefghijk", "Video", "Channel", "1:00", "42", "https://www.youtube.com/channel/UC123", isShort = true)
        val value = LibraryState(listOf(Playlist("1", "Favorites", listOf(v))), listOf(Subscription(v.channelUrl, v.channel)), listOf(HistoryEntry(v, 5000, 123)), listOf(v))
        assertEquals(value, LibraryCodec.decode(LibraryCodec.encode(value)))
    }
    @Test fun oldBackupsDefaultToRegularVideo() {
        assertFalse(JSONObject("""{"id":"abcdefghijk","title":"Old video"}""").video().isShort)
    }
    @Test(expected = IllegalArgumentException::class) fun malformedVideoRejectsWholeImport() {
        LibraryCodec.decode(JSONObject("""{"likedVideos":[{"id":"../../bad"}]}"""))
    }
}
