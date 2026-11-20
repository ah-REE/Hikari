package com.hikari.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class WatchMetadata(val video: Video, val description: String, val related: List<Video>, val relatedFromSearch: Boolean = false, val likeCount: Long = -1)

object WatchMetadataRepository {
    private val avatars = ConcurrentHashMap<String, String>()
    suspend fun avatar(channelUrl: String): String = withContext(Dispatchers.IO) {
        if (!channelUrl.startsWith("https://www.youtube.com/") && !channelUrl.startsWith("https://youtube.com/")) return@withContext ""
        avatars[channelUrl]?.let { return@withContext it }
        Extractor.init()
        val result = ChannelInfo.getInfo(channelUrl).avatars.maxByOrNull { it.height }?.url.orEmpty()
        if (result.isNotBlank()) avatars[channelUrl] = result
        result
    }
    suspend fun load(video: Video): WatchMetadata = withContext(Dispatchers.IO) {
        val info = try { Extractor.streamInfo(video.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val updated = if (info != null) video.copy(title = info.name.ifBlank { video.title }, channel = info.uploaderName,
            channelUrl = info.uploaderUrl, avatarUrl = info.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty().ifBlank { video.avatarUrl },
            isShort = video.isShort || info.isShortFormContent, duration = if (info.duration > 0) clockTime(info.duration) else video.duration,
            views = if (info.viewCount >= 0) "%,d views".format(info.viewCount) else video.views)
        else {
            val c = URI("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${video.id}&format=json").toURL().openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
            val json = c.getInputStream().bufferedReader().use { JSONObject(it.readText()) }
            video.copy(title = json.optString("title", video.title), channel = json.optString("author_name", video.channel), channelUrl = json.optString("author_url", video.channelUrl))
        }
        val withAvatar = if (updated.avatarUrl.isBlank() && updated.channelUrl.isNotBlank()) updated.copy(avatarUrl = try { avatar(updated.channelUrl) } catch (e: CancellationException) { throw e } catch (_: Exception) { "" }) else updated
        val related = info?.relatedItems?.filterIsInstance<StreamInfoItem>()?.mapNotNull { it.asVideo() }.orEmpty().filterNot { it.id == video.id }
        // Real search results provide a labelled topic fallback when YouTube returns no recommendations.
        val searched = if (related.isEmpty()) try { NativeSearch.search(withAvatar.title).videos.filterNot { it.id == video.id }.take(12) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } else emptyList()
        WatchMetadata(withAvatar, info?.description?.content.orEmpty(), related.ifEmpty { searched }, related.isEmpty() && searched.isNotEmpty(), info?.likeCount ?: -1)
    }
    suspend fun dislikes(id: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URI("https://returnyoutubedislikeapi.com/votes?videoId=$id").toURL().openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }
            c.getInputStream().bufferedReader().use { JSONObject(it.readText()).optLong("dislikes", -1).takeIf { count -> count >= 0 } }
        }.getOrNull()
    }
}
