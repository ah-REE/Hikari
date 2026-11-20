package com.hikari.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Subscription(val url: String, val name: String)
data class Playlist(val id: String, val name: String, val videos: List<Video>)
data class HistoryEntry(val video: Video, val position: Long, val watchedAt: Long)
data class LibraryState(val playlists: List<Playlist> = emptyList(), val subscriptions: List<Subscription> = emptyList(), val history: List<HistoryEntry> = emptyList(), val liked: List<Video> = emptyList())

fun JSONObject.video(): Video {
    val id = getString("id")
    require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid video ID in backup" }
    return Video(id, optString("title"), optString("channel", optString("channelTitle")), optString("durationFormatted", optString("duration")), optString("views", optString("viewCountFormatted")), optString("channelUrl", optString("channelId").takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" }.orEmpty()), optString("avatarUrl"), optBoolean("isShort", false))
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
object LibraryCodec {
    fun encode(state: LibraryState): JSONObject = JSONObject().put("playlists", JSONArray(state.playlists.map { p -> JSONObject().put("id", p.id).put("title", p.name).put("videos", JSONArray(p.videos.map { it.json() })) }))
        .put("subscriptions", JSONArray(state.subscriptions.map { JSONObject().put("url", it.url).put("name", it.name) }))
        .put("watchHistory", JSONArray(state.history.map { JSONObject().put("video", it.video.json()).put("positionMs", it.position).put("watchedAt", it.watchedAt) }))
        .put("likedVideos", JSONArray(state.liked.map { it.json() }))
    fun decode(data: JSONObject): LibraryState {
        fun array(key: String) = data.optJSONArray(key) ?: JSONArray()
        return LibraryState(
            array("playlists").objects().map { Playlist(it.optString("id").ifBlank { UUID.randomUUID().toString() }, it.optString("title", "Imported playlist"), (it.optJSONArray("videos") ?: JSONArray()).objects().map { v -> v.video() }.distinctBy { v -> v.id }) },
            array("subscriptions").let { values -> (0 until values.length()).map { index -> values.optJSONObject(index) ?: JSONObject().put("id", values.getString(index)).put("name", values.getString(index)) } }.map { s ->
                val id = listOf(s.optString("url"), s.optString("id"), s.optString("handle")).firstOrNull { it.startsWith("https://www.youtube.com/") || it.startsWith("UC") || it.startsWith("@") }.orEmpty()
                val url = when { id.startsWith("https://www.youtube.com/") -> id; id.startsWith("UC") -> "https://www.youtube.com/channel/$id"; id.startsWith("@") -> "https://www.youtube.com/$id"; else -> "" }
                Subscription(url, s.optString("name", id))
            }.filter { it.url.isNotBlank() }.distinctBy { it.url },
            array("watchHistory").objects().map { HistoryEntry(it.getJSONObject("video").video(), it.optLong("positionMs", (it.optDouble("progress", 0.0) * 1000).toLong()).coerceAtLeast(0), it.optLong("watchedAt")) }.distinctBy { it.video.id }.take(500),
            array("likedVideos").objects().map { it.video() }.distinctBy { it.id }
        )
    }
}

class Library private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("hikari-library", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching { LibraryCodec.decode(JSONObject(prefs.getString("data", "{}")!!)) }.getOrDefault(LibraryState()))
    val state = mutable.asStateFlow()
    @Synchronized private fun update(change: (LibraryState) -> LibraryState) {
        val next = change(mutable.value)
        prefs.edit().putString("data", LibraryCodec.encode(next).toString()).apply()
        mutable.value = next
    }
    fun createPlaylist(name: String) { require(name.isNotBlank()); update { it.copy(playlists = it.playlists + Playlist(UUID.randomUUID().toString(), name.trim().take(100), emptyList())) } }
    fun removePlaylist(id: String) = update { it.copy(playlists = it.playlists.filterNot { p -> p.id == id }) }
    fun addToPlaylist(id: String, video: Video) = update { s -> s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(videos = (it.videos + video).distinctBy { v -> v.id }) else it }) }
    fun removeFromPlaylist(id: String, video: Video) = update { s -> s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(videos = it.videos.filterNot { v -> v.id == video.id }) else it }) }
    fun subscribe(video: Video) { if (video.channelUrl.isBlank()) return; update { s -> s.copy(subscriptions = if (s.subscriptions.any { it.url == video.channelUrl }) s.subscriptions.filterNot { it.url == video.channelUrl } else s.subscriptions + Subscription(video.channelUrl, video.channel)) } }
    fun like(video: Video) = update { s -> s.copy(liked = if (s.liked.any { it.id == video.id }) s.liked.filterNot { it.id == video.id } else listOf(video) + s.liked) }
    fun record(video: Video, position: Long) = update { s -> s.copy(history = (listOf(HistoryEntry(video, position.coerceAtLeast(0), System.currentTimeMillis())) + s.history.filterNot { it.video.id == video.id }).take(500)) }
    fun clear() = update { LibraryState() }
    fun clearHistory() = update { it.copy(history = emptyList()) }
    fun merge(imported: LibraryState) = update { s -> s.copy(playlists = (s.playlists + imported.playlists).distinctBy { it.id }, subscriptions = (s.subscriptions + imported.subscriptions).distinctBy { it.url }, history = (s.history + imported.history).sortedByDescending { it.watchedAt }.distinctBy { it.video.id }.take(500), liked = (s.liked + imported.liked).distinctBy { it.id }) }
    companion object {
        @Volatile private var instance: Library? = null
        fun get(context: Context): Library = instance ?: synchronized(this) { instance ?: Library(context.applicationContext).also { instance = it } }
    }
}
