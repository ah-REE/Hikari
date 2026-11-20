package com.hikari.app

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorLiveTest {
    @Test fun channelSectionsAndPlaylistsLoad() = runBlocking {
        assumeTrue(System.getenv("HIKARI_LIVE_TEST") == "1")
        Extractor.init()
        val channel = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo("https://www.youtube.com/@MrBeast")
        println("Channel tabs: " + channel.tabs.flatMap { it.contentFilters })
        val service = org.schabi.newpipe.extractor.NewPipe.getService(0)
        val videos = channel.tabs.first { "videos" in it.contentFilters }
        val uploads = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, videos)
        assertTrue(uploads.relatedItems.isNotEmpty())
        assertTrue(org.schabi.newpipe.extractor.Page.isValid(uploads.nextPage))
        val more = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getMoreItems(service, videos, uploads.nextPage)
        assertTrue(more.items.any { item -> uploads.relatedItems.none { it.url == item.url } })
        val shorts = channel.tabs.first { "shorts" in it.contentFilters }
        assertTrue(org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, shorts).relatedItems.isNotEmpty())
        val playlistChannel = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo("https://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg")
        println("Blender tabs: " + playlistChannel.tabs.flatMap { it.contentFilters })
        val playlists = playlistChannel.tabs.first { "playlists" in it.contentFilters }
        val collections = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, playlists).relatedItems.filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
        assertTrue(collections.isNotEmpty())
        assertTrue(org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(collections.first().url).relatedItems.isNotEmpty())
    }
    @Test fun multilingualVideoResolvesSelectedDownloadAndPlaybackAudio() = runBlocking {
        assumeTrue(System.getenv("HIKARI_LIVE_TEST") == "1")
        val video = Video("0e3GPea1Tyg", "", "", "", "")
        val resolved = Extractor.resolve(video)
        println("Multilingual video: ${resolved.video.title}; tracks: ${resolved.audioTracks.map { it.label }}")
        assertTrue("No alternate audio tracks returned", resolved.audioTracks.size > 1)
        val alternate = resolved.audioTracks.first { !it.original && it.id != null }
        val download = resolved.downloadChoices.first { it.audio != null }.withDownloadAudio(alternate)
        assertTrue(download.audio == alternate.url)
        val playback = Extractor.resolve(video, alternate.id)
        assertTrue(playback.choices.first().audioTrackId == alternate.id)
        assertTrue(playback.choices.first().audio != null)
    }
    @Test fun subscriptionUploadsLoad() = runBlocking {
        assumeTrue(System.getenv("HIKARI_LIVE_TEST") == "1")
        val feed = SubscriptionFeed.load(listOf(Subscription("https://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg", "Blender")))
        assertTrue("Subscription feed returned no uploads", feed.first.isNotEmpty())
        assertTrue(feed.second == 0)
    }
    @Test fun searchPaginates() = runBlocking {
        assumeTrue(System.getenv("HIKARI_LIVE_TEST") == "1")
        val first = NativeSearch.search("android development", includeShortsShelves = true)
        assertTrue(first.videos.isNotEmpty())
        assertTrue(first.next != null)
        val second = NativeSearch.search("android development", first.next, includeShortsShelves = true)
        assertTrue(second.videos.any { next -> first.videos.none { it.id == next.id } })
    }
    @Test fun watchMetadataLoadsIndependently() = runBlocking {
        assumeTrue(System.getenv("HIKARI_LIVE_TEST") == "1")
        val metadata = WatchMetadataRepository.load(Video("BTMjD7_evjE", "", "", "", ""))
        println("Metadata: ${metadata.video.title}, avatar: ${metadata.video.avatarUrl}, related: ${metadata.related.size}")
        assertTrue(metadata.video.channelUrl.isNotBlank())
        assertTrue(metadata.video.avatarUrl.isNotBlank())
        assertTrue(metadata.related.isNotEmpty())
    }
    @Test fun sponsorSegmentsAndCaptionTracksLoad() = runBlocking {
        assumeTrue(System.getenv("HIKARI_LIVE_TEST") == "1")
        val candidates = NativeSearch.search("Linus Tech Tips review").videos.take(4)
        var sponsors = 0
        for (video in candidates) {
            val segments = SponsorSegments.load(video.id)
            println("SPONSOR_FIXTURE ${video.id}: $segments")
            if (segments.isNotEmpty()) { sponsors = segments.size; break }
        }
        assertTrue("No sponsor fixture found", sponsors > 0)
        val info = Extractor.streamInfo("dQw4w9WgXcQ")
        println("CAPTION_TRACKS ${info.subtitles.map { it.languageTag }}; likes=${info.likeCount}")
        assertTrue("No caption tracks returned", info.subtitles.isNotEmpty())
        val caption = info.subtitles.first()
        val c = java.net.URI(caption.content).toURL().openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
        val body = c.getInputStream().bufferedReader().use { it.readText() }
        println("CAPTION_PAYLOAD bytes=${body.length}, format=${caption.format}")
        assertTrue("Caption endpoint returned no text", body.isNotBlank())
    }
    @Test fun resolvesRealVideo() = runBlocking {
        assumeTrue(System.getenv("HIKARI_LIVE_TEST") == "1")
        val resolved = Extractor.resolve(Video("aqz-KE-bpKQ", "Big Buck Bunny", "Blender", "", ""))
        assertTrue(resolved.choices.isNotEmpty())
        val url = java.net.URI(resolved.choices.first().url).toURL()
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Range", "bytes=0-1023")
        try { assertTrue("Stream HTTP ${connection.responseCode}", connection.responseCode in 200..299) } finally { connection.disconnect() }
    }
}
