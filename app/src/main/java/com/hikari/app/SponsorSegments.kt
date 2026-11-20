package com.hikari.app

import android.content.SharedPreferences
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class SponsorSegment(val start: Float, val end: Float) {
    fun contains(seconds: Float) = seconds >= start && seconds < end
}
object SponsorSegments {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<SponsorSegment>>>()
    fun parse(json: JSONArray): List<SponsorSegment> = json.objects().mapNotNull {
        val a = it.optJSONArray("segment") ?: return@mapNotNull null
        val start = a.optDouble(0, Double.NaN).toFloat(); val end = a.optDouble(1, Double.NaN).toFloat()
        if (start.isFinite() && end.isFinite() && start >= 0 && end > start) SponsorSegment(start, end) else null
    }.sortedBy { it.start }
    suspend fun load(id: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        cache[id]?.takeIf { System.currentTimeMillis() - it.first < 600000 }?.let { return@withContext it.second }
        val connection = URI("https://sponsor.ajay.app/api/skipSegments?videoID=$id&categories=%5B%22sponsor%22%5D").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 8000; connection.readTimeout = 8000
        try {
            val result = if (connection.responseCode == 404) emptyList() else {
                check(connection.responseCode == 200) { "SponsorBlock unavailable (${connection.responseCode})" }
                connection.inputStream.bufferedReader().use { parse(JSONArray(it.readText())) }
            }
            if (cache.size > 80) cache.clear()
            cache[id] = System.currentTimeMillis() to result
            result
        } finally { connection.disconnect() }
    }
}
@Composable
fun rememberBooleanPreference(prefs: SharedPreferences, key: String, default: Boolean = false): Boolean {
    var value by remember(prefs, key) { mutableStateOf(prefs.getBoolean(key, default)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == key) value = prefs.getBoolean(key, default) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
