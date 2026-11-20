package com.hikari.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class PlayerPreferences(
    val defaultSpeed: Float = 1f, val seekSeconds: Int = 10, val focusMode: Boolean = false,
    val autoplayNext: Boolean = false, val hideWatched: Boolean = false, val hideShorts: Boolean = false,
    val showDislikes: Boolean = false, val enhancedColors: Boolean = false, val scrollToTop: Boolean = true,
    val timeFitFinder: Boolean = false, val backgroundPlayback: Boolean = false
) {
    fun json() = JSONObject().put("defaultSpeed", defaultSpeed).put("seekDuration", seekSeconds)
        .put("hideRecommendations", focusMode).put("disableAutoplay", !autoplayNext)
        .put("hideWatchedVideos", hideWatched).put("disableShorts", hideShorts)
        .put("enableDislikeCount", showDislikes).put("defaultTos", enhancedColors).put("enableScrollToTop", scrollToTop)
        .put("enableTimeFitFinder", timeFitFinder).put("enableBackgroundPlayback", false)

    companion object {
        fun fromJson(j: JSONObject) = PlayerPreferences(
            j.optDouble("defaultSpeed", 1.0).toFloat().takeIf { it in listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }
                ?: 1f,
            j.optInt("seekDuration", 10).takeIf { it in listOf(5, 10, 15, 30, 60) } ?: 10,
            j.optBoolean("hideRecommendations"),
            !j.optBoolean("disableAutoplay", true),
            j.optBoolean("hideWatchedVideos"),
            j.optBoolean("disableShorts"),
            j.optBoolean("enableDislikeCount"),
            j.optBoolean("defaultTos", j.optBoolean("defaultHdr")),
            j.optBoolean("enableScrollToTop", true),
            j.optBoolean("enableTimeFitFinder", false),
            false)
    }
}

class PreferencesStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hikari-native", 0)
    private fun read() = PlayerPreferences.fromJson(runCatching {
        JSONObject(
            prefs.getString(
                "playerPreferences",
                "{}"
            )!!
        )
    }.getOrDefault(JSONObject()))

    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "playerPreferences") mutable.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(value: PlayerPreferences) {
        prefs.edit().putString("playerPreferences", value.json().toString()).apply(); mutable.value = value
    }

    companion object {
        @Volatile
        private var instance: PreferencesStore? = null
        fun get(context: Context) =
            instance ?: synchronized(this) { instance ?: PreferencesStore(context).also { instance = it } }
    }
}

/** Discovery preferences never remove videos from a user's saved collections. */
fun filterDiscovery(
    videos: List<Video>,
    prefs: PlayerPreferences,
    history: List<HistoryEntry>,
    shortsFeed: Boolean = false
): List<Video> {
    val watched = if (prefs.hideWatched) history.map { it.video.id }.toSet() else emptySet()
    return videos.filter { it.id !in watched }
}
