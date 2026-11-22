package com.hikari.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

data class Video(val id: String, val title: String, val channel: String, val duration: String, val views: String, val channelUrl: String = "", val avatarUrl: String = "", val isShort: Boolean = false) {
    val thumbnail get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    fun json() = JSONObject().put("id", id).put("title", title).put("channel", channel).put("duration", duration).put("views", views).put("channelUrl", channelUrl).put("avatarUrl", avatarUrl).put("isShort", isShort)
}

object SearchParser {
    fun parse(html: String): List<Video> {
        val marker = Regex("(?:var\\s+ytInitialData\\s*=|window\\[\"ytInitialData\"\\]\\s*=)\\s*").find(html)
            ?: error("YouTube returned an unsupported response. Please try again later.")
        // JSONTokener consumes one complete value, without depending on script formatting.
        val root = org.json.JSONTokener(html.substring(marker.range.last + 1)).nextValue()
        val found = linkedMapOf<String, Video>()
        fun text(o: JSONObject?): String = o?.optString("simpleText")?.takeIf { it.isNotBlank() }
            ?: o?.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") } }.orEmpty()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    value.optJSONObject("videoRenderer")?.let { v ->
                        val id = v.optString("videoId")
                        if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) found[id] = Video(id, text(v.optJSONObject("title")), text(v.optJSONObject("ownerText")), text(v.optJSONObject("lengthText")), text(v.optJSONObject("viewCountText")))
                    }
                    value.keys().forEach { walk(value.opt(it)) }
                }
                is JSONArray -> (0 until value.length()).forEach { walk(value.opt(it)) }
            }
        }
        walk(root)
        return found.values.toList()
    }
}

class VideoRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("hikari-native", Context.MODE_PRIVATE)
    fun saved(): List<Video> = runCatching {
        val a = JSONArray(prefs.getString("saved", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).video() }
    }.getOrDefault(emptyList())
    fun save(videos: List<Video>) { prefs.edit().putString("saved", JSONArray(videos.map { it.json() }).toString()).apply() }
    fun themeMode(): String = prefs.getString("themeMode", null)?.takeIf { it in setOf("system", "light", "dark") }
        ?: if (prefs.contains("dark")) if (prefs.getBoolean("dark", true)) "dark" else "light" else "system"
    fun dark(): Boolean = when (themeMode()) {
        "dark" -> true
        "light" -> false
        else -> context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    fun setThemeMode(value: String) {
        require(value in setOf("system", "light", "dark"))
        val editor = prefs.edit().putString("themeMode", value)
        if (value == "system") editor.remove("dark") else editor.putBoolean("dark", value == "dark")
        editor.apply()
    }
    suspend fun search(query: String): List<Video> = withContext(Dispatchers.IO) {
        val url = URI("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}&hl=en").toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            check(connection.responseCode == 200) { "Search is unavailable (${connection.responseCode}). Please retry." }
            SearchParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
}
