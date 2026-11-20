package com.hikari.app

import org.junit.Assert.*
import org.junit.Test

class DownloadSelectionTest {
    private val hindi = AudioTrackChoice("hi.1", "Hindi", false, "https://audio/hi", "audio/mp4")
    @Test fun alternateLanguageUsesItsOwnAudioAndKeepsVideoQuality() {
        val selected = StreamChoice("1080p", "https://video/1080", "https://audio/en", "video/mp4").withDownloadAudio(hindi)
        assertEquals("https://video/1080", selected.url)
        assertEquals("https://audio/hi", selected.audio)
        assertEquals("hi.1", selected.audioTrackId)
        assertEquals("Hindi", selected.audioTrackName)
    }
    @Test fun audioOnlyChangesTheDownloadUrlAndMime() {
        val selected = StreamChoice("Audio only", "https://audio/en", mime = "audio/webm").withDownloadAudio(hindi)
        assertEquals("https://audio/hi", selected.url)
        assertEquals("audio/mp4", selected.mime)
        assertNull(selected.audio)
    }
    @Test fun muxedFormatsDoNotPretendToReplaceEmbeddedAudio() {
        val muxed = StreamChoice("360p", "https://video/muxed", mime = "video/mp4")
        assertEquals(muxed, muxed.withDownloadAudio(hindi))
    }
    @Test fun ignoredWrongAndMalformedRangesCannotPassValidation() {
        assertNull(DownloadRange.parse(null))
        assertNull(DownloadRange.parse("bytes 5-4/100"))
        assertNull(DownloadRange.parse("bytes 0-100/100"))
        assertNull(DownloadRange.parse("bytes 0-5/999999999999999999999"))
        val reply = requireNotNull(DownloadRange.parse("bytes 1048576-2097151/5242880"))
        assertTrue(reply.matches(1048576, 1048576))
        assertFalse(reply.matches(0, 1048576))
        assertFalse(reply.matches(1048576, 200))
    }
}
