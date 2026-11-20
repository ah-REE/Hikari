package com.hikari.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

class ExtractorDownloader : Downloader() {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (name, values) -> values.forEach { builder.addHeader(name, it) } }
        val body = request.dataToSend()?.toRequestBody()
        builder.method(request.httpMethod(), body)
        return client.newCall(builder.build()).execute().use { r -> Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString()) }
    }
}

data class StreamChoice(val label: String, val url: String, val audio: String? = null, val mime: String? = null, val audioTrackId: String? = null, val audioTrackName: String? = null)
data class Caption(val url: String, val language: String, val name: String, val mime: String)
data class AudioTrackChoice(val id: String?, val label: String, val original: Boolean, val url: String = "", val mime: String? = null)
data class ResolvedVideo(val video: Video, val description: String, val choices: List<StreamChoice>, val captions: List<Caption> = emptyList(), val audioTracks: List<AudioTrackChoice> = emptyList(), val downloadChoices: List<StreamChoice> = emptyList(), val relatedVideos: List<Video> = emptyList())

object Extractor {
    private val lock = Mutex()
    @Volatile private var initialized = false
    @Synchronized fun init() { if (!initialized) { NewPipe.init(ExtractorDownloader()); initialized = true } }
    private val infoCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, StreamInfo>>()
    suspend fun streamInfo(id: String): StreamInfo = withContext(Dispatchers.IO) {
        infoCache[id]?.takeIf { System.currentTimeMillis() - it.first < 180000 }?.second ?: run {
            init()
            StreamInfo.getInfo("https://www.youtube.com/watch?v=$id").also { if (infoCache.size > 30) infoCache.clear(); infoCache[id] = System.currentTimeMillis() to it }
        }
    }
    suspend fun resolve(video: Video, preferredAudioTrackId: String? = null): ResolvedVideo = withContext(Dispatchers.IO) {
        lock.withLock {
            init()
            val info = streamInfo(video.id)
            val tracks = info.audioStreams.filter { it.isUrl }
            // Prefer the best AAC stream within each language, rather than the first (often low-bitrate) format.
            val audioTracks = tracks.groupBy { it.audioTrackId }.values.mapNotNull { formats ->
                formats.filter { it.format?.mimeType == "audio/mp4" }.maxByOrNull { it.averageBitrate } ?: formats.maxByOrNull { it.averageBitrate }
            }
            val selectedAudio = audioTracks.firstOrNull { preferredAudioTrackId != null && it.audioTrackId == preferredAudioTrackId }
                ?: audioTracks.firstOrNull { it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL }
                ?: audioTracks.firstOrNull { it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.SECONDARY }
                ?: audioTracks.maxByOrNull { it.averageBitrate }
            fun org.schabi.newpipe.extractor.stream.AudioStream.trackName() = audioTrackName?.takeIf { it.isNotBlank() } ?: audioLocale?.displayName ?: "Original audio"
            val audio = selectedAudio?.content
            val adaptive = if (audio == null) emptyList() else info.videoOnlyStreams.filter { it.isUrl }
                .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.height }.thenBy { it.format?.mimeType != "video/mp4" })
                .map { StreamChoice(it.resolution, it.content, audio, it.format?.mimeType, selectedAudio.audioTrackId, selectedAudio.trackName()) }
            val muxed = info.videoStreams.filter { it.isUrl && !it.isVideoOnly }.sortedByDescending { it.height }.map { StreamChoice(it.resolution, it.content, mime = it.format?.mimeType) }
            val audioOnly = selectedAudio?.let { StreamChoice("Audio only", it.content, mime = it.format?.mimeType, audioTrackId = it.audioTrackId, audioTrackName = it.trackName()) }
            val choices = mutableListOf<StreamChoice>()
            // A manifest or muxed stream contains its own audio and would silently ignore a selected language.
            if (preferredAudioTrackId == null) {
                if (!info.hlsUrl.isNullOrBlank()) choices += StreamChoice("Auto", info.hlsUrl, mime = "application/x-mpegURL")
                choices += muxed
            }
            choices += adaptive
            audioOnly?.let { choices += it }
            check(choices.isNotEmpty()) { "YouTube did not provide a playable stream. Please retry later." }
            val downloads = (adaptive + muxed + listOfNotNull(audioOnly)).distinctBy { it.label }
            ResolvedVideo(
                video.copy(isShort = video.isShort || info.isShortFormContent, title = info.name, channel = info.uploaderName, channelUrl = info.uploaderUrl, avatarUrl = info.uploaderAvatars.firstOrNull()?.url.orEmpty(), duration = if (info.duration > 0) clockTime(info.duration) else video.duration, views = if (info.viewCount >= 0) "%,d views".format(info.viewCount) else video.views),
                info.description.content, choices.distinctBy { it.label },
                info.subtitles.filter { it.isUrl }.map { Caption(it.content, it.languageTag, it.displayLanguageName, it.format?.mimeType ?: "text/vtt") },
                audioTracks.map { AudioTrackChoice(it.audioTrackId, it.trackName(), it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL, it.content, it.format?.mimeType) }.sortedWith(compareByDescending<AudioTrackChoice> { it.original }.thenBy { it.label }),
                downloads, info.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>().mapNotNull { it.asVideo() }
            )
        }
    }
}

/** Keep the requested language attached to the selected video quality for offline playback. */
fun StreamChoice.withDownloadAudio(track: AudioTrackChoice?): StreamChoice = when {
    track == null || track.url.isBlank() -> this
    label == "Audio only" -> copy(url = track.url, mime = track.mime, audioTrackId = track.id, audioTrackName = track.label)
    audio != null -> copy(audio = track.url, audioTrackId = track.id, audioTrackName = track.label)
    else -> this // A muxed-only format cannot replace its bundled audio.
}
