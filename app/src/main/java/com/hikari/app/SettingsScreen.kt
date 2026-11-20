package com.hikari.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

@Composable
fun SettingsScreen(model: HikariViewModel) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val library = Library.get(context)
    val preferencesStore = PreferencesStore.get(context)
    val options by preferencesStore.state.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("hikari-native", 0) }
    var embedFirst by remember { mutableStateOf(prefs.getBoolean("embedFirst", true)) }
    var sponsors by remember { mutableStateOf(prefs.getBoolean("sponsorBlock", false)) }
    val sponsorAuto = rememberBooleanPreference(prefs, "sponsorAutoSkip", true)
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            notice = withContext(Dispatchers.IO) {
                runCatching {
                    val data = LibraryCodec.encode(library.state.value).put("version", 4)
                        .put("watchLater", JSONArray(state.saved.map { it.json() })).put(
                            "preferences",
                            options.json().put("theme", if (state.dark) "dark" else "light")
                                .put("embedFirst", embedFirst)
                                .put("sponsorBlock", sponsors).put("sponsorAutoSkip", sponsorAuto)
                                .put("reduceMotion", state.reduceMotion)
                        )
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(data.toString(2)) }
                        ?: error("Cannot write backup")
                    "Backup exported"
                }.getOrElse { "Export failed: ${it.message}" }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count =
                                input.read(buffer); if (count < 0) break; require(output.size() + count <= 10 * 1024 * 1024) { "Backup exceeds 10 MB" }; output.write(
                                buffer,
                                0,
                                count
                            )
                        }
                        output.toByteArray()
                    } ?: error("Cannot read backup")
                    require(bytes.size <= 10 * 1024 * 1024) { "Backup exceeds 10 MB" }
                    val json = JSONObject(String(bytes))
                    require(json.has("playlists") || json.has("watchLater") || json.has("watchHistory")) { "Not a Hikari backup" }
                    Triple(
                        LibraryCodec.decode(json),
                        (json.optJSONArray("watchLater") ?: JSONArray()).objects().map { it.video() },
                        json.optJSONObject("preferences")
                    )
                }
                library.merge(parsed.first); model.importSaved(parsed.second)
                parsed.third?.let { imported ->
                    imported.optString("theme").takeIf { it == "dark" || it == "light" }
                        ?.let { model.theme(it == "dark") }
                    // Merge absent fields so older backups cannot reset newer settings.
                    val merged = options.json()
                    imported.keys().forEach { key -> merged.put(key, imported.get(key)) }
                    preferencesStore.update(PlayerPreferences.fromJson(merged))
                    if (imported.has("embedFirst")) {
                        embedFirst = imported.getBoolean("embedFirst"); prefs.edit()
                            .putBoolean("embedFirst", embedFirst).apply()
                    }
                    if (imported.has("sponsorBlock")) {
                        sponsors = imported.getBoolean("sponsorBlock"); prefs.edit()
                            .putBoolean("sponsorBlock", sponsors).apply()
                    }
                    if (imported.has("sponsorAutoSkip")) prefs.edit()
                        .putBoolean("sponsorAutoSkip", imported.getBoolean("sponsorAutoSkip")).apply()
                    if (imported.has("reduceMotion")) model.reduceMotion(imported.getBoolean("reduceMotion"))
                }
                notice = "Backup merged with your library"
            } catch (e: Exception) {
                notice = "Import failed: ${e.message}"
            }
        }
    }
    var optionSheet by remember { mutableStateOf<String?>(null) }
    var clearLibrary by remember { mutableStateOf(false) }
    var playback by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { SettingsLabel("Your experience", first = true) }
        item {
            SettingRow(
                Icons.Outlined.LightMode,
                "Appearance",
                if (state.dark) "Dark · easy on the eyes" else "Light · a softer contrast",
                value = if (state.dark) "Dark" else "Light",
                onClick = { model.theme(!state.dark) })
        }
        item { SettingsLabel("Playback") }
        item {
            SettingRow(
                Icons.Outlined.Movie,
                "Player",
                if (options.backgroundPlayback) "Native player for background playback"
                else if (embedFirst) "YouTube player first" else "Native player first",
                value = if (options.backgroundPlayback || !embedFirst) "Native" else "Automatic",
                onClick = { playback = true })
        }
        item {
            SettingRow(
                Icons.Outlined.Headphones,
                "Background playback",
                "Upcoming feature",
                value = "Upcoming",
                onClick = {})
        }
        item {
            SettingRow(
                Icons.Outlined.Shield,
                "Skip sponsored segments",
                "Community-reported segments in both players",
                checked = sponsors,
                onClick = { sponsors = !sponsors; prefs.edit().putBoolean("sponsorBlock", sponsors).apply() })
        }
        if (sponsors) item {
            SettingRow(
                Icons.Outlined.SkipNext,
                "Skip automatically",
                "Turn off to use the manual Skip sponsor button",
                checked = sponsorAuto,
                onClick = { prefs.edit().putBoolean("sponsorAutoSkip", !sponsorAuto).apply() })
        }
        item {
            SettingRow(
                Icons.Outlined.Bolt,
                "Reduce motion",
                "Keep transitions simple",
                checked = state.reduceMotion,
                onClick = { model.reduceMotion(!state.reduceMotion) })
        }
        item {
            SettingRow(
                Icons.Outlined.Speed,
                "Default playback speed",
                "Used when opening a video",
                value = "${options.defaultSpeed}×",
                onClick = { optionSheet = "speed" })
        }
        item {
            SettingRow(
                Icons.Outlined.Replay,
                "Seek interval",
                "Double tap, player buttons, and picture in picture",
                value = "${options.seekSeconds}s",
                onClick = { optionSheet = "seek" })
        }
        item {
            SettingRow(
                Icons.Outlined.PlayCircleOutline,
                "Autoplay next video",
                "A five-second countdown while watching",
                checked = options.autoplayNext,
                onClick = { preferencesStore.update(options.copy(autoplayNext = !options.autoplayNext)) })
        }
        item {
            SettingRow(
                Icons.Outlined.Contrast,
                "Enhanced colors",
                "Richer contrast in the YouTube player",
                checked = options.enhancedColors,
                onClick = { preferencesStore.update(options.copy(enhancedColors = !options.enhancedColors)) })
        }
        item { SettingsLabel("Discovery") }
        item {
            SettingRow(
                Icons.Outlined.Schedule,
                "Time-fit finder",
                "Optional time-limited video search on Home",
                checked = options.timeFitFinder,
                onClick = { preferencesStore.update(options.copy(timeFitFinder = !options.timeFitFinder)) })
        }
        item {
            SettingRow(
                Icons.Outlined.CenterFocusStrong,
                "Focus mode",
                "Hide related videos and pause autoplay",
                checked = options.focusMode,
                onClick = { preferencesStore.update(options.copy(focusMode = !options.focusMode)) })
        }
        item {
            SettingRow(
                Icons.Outlined.VisibilityOff,
                "Hide watched videos",
                "In search, Following, and related videos",
                checked = options.hideWatched,
                onClick = { preferencesStore.update(options.copy(hideWatched = !options.hideWatched)) })
        }
        item {
            SettingRow(
                Icons.Outlined.ThumbDownOffAlt,
                "Show estimated dislikes",
                "Estimates from Return YouTube Dislike",
                checked = options.showDislikes,
                onClick = { preferencesStore.update(options.copy(showDislikes = !options.showDislikes)) })
        }
        item {
            SettingRow(
                Icons.Outlined.VerticalAlignTop,
                "Back-to-top button",
                "A shortcut when browsing long lists",
                checked = options.scrollToTop,
                onClick = { preferencesStore.update(options.copy(scrollToTop = !options.scrollToTop)) })
        }
        item { SettingsLabel("Your library") }
        item {
            SettingRow(
                Icons.Outlined.Upload,
                "Export a backup",
                "Keep a copy of your library",
                onClick = { export.launch("hikari-backup.json") })
        }
        item {
            SettingRow(
                Icons.Outlined.Download,
                "Import a backup",
                "Bring your collection into Hikari",
                onClick = { import.launch(arrayOf("application/json", "text/plain")) })
        }
        item {
            SettingRow(
                Icons.Outlined.History,
                "Clear watch history",
                "Saved videos stay in your library",
                onClick = { clearHistory = true })
        }
        item {
            SettingRow(
                Icons.Outlined.DeleteOutline,
                "Clear library",
                "Remove saved lists, likes, follows, and history",
                onClick = { clearLibrary = true })
        }
        notice?.let { message ->
            item {
                Text(
                    message,
                    Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item { SettingsLabel("About") }
        item {
            SettingRow(
                Icons.Outlined.Info,
                "Made for intentional watching",
                "Hikari · Android",
                onClick = { about = true })
        }
        item {
            Column(
                Modifier.padding(top = 28.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "Your library stays on your device.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ); Text(
                "No account needed. Your collection, your choice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }
    }
    if (optionSheet != null) HikariSheet(
        if (optionSheet == "speed") "Default playback speed" else "Seek interval",
        { optionSheet = null }) {
        if (optionSheet == "speed") listOf(
            .5f,
            .75f,
            1f,
            1.25f,
            1.5f,
            1.75f,
            2f
        ).forEach { value ->
            SheetOption(
                if (value == 1f) "Normal" else "${value}×",
                selected = options.defaultSpeed == value
            ) { preferencesStore.update(options.copy(defaultSpeed = value)); optionSheet = null }
        }
        else listOf(5, 10, 15, 30, 60).forEach { value ->
            SheetOption(
                "$value seconds",
                selected = options.seekSeconds == value
            ) { preferencesStore.update(options.copy(seekSeconds = value)); optionSheet = null }
        }
    }
    if (clearLibrary) ConfirmRemoval(
        "Clear your library?",
        "This removes Watch later, playlists, likes, follows, and watch and search history from this device. Downloads and settings are kept. Export a backup first to keep a copy.",
        "Clear library",
        { library.clear(); model.clearLibrary(); clearLibrary = false },
        { clearLibrary = false })
    if (playback) HikariSheet("Playback", { playback = false }) {
        SheetOption(
            "Automatic",
            "YouTube player first. Restricted videos use native playback. Background playback takes priority when enabled.",
            selected = embedFirst
        ) { embedFirst = true; prefs.edit().putBoolean("embedFirst", true).apply(); playback = false }
        SheetOption(
            "Native player",
            "Stream quality and audio tracks. Background audio requires Background playback to be enabled.",
            selected = !embedFirst
        ) { embedFirst = false; prefs.edit().putBoolean("embedFirst", false).apply(); playback = false }
        Text(
            "Applies to the next video you open.",
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (clearHistory) ConfirmRemoval(
        "Clear watch history?",
        "Your saved videos, playlists, and downloads will stay in your library.",
        "Clear history",
        { library.clearHistory(); clearHistory = false },
        { clearHistory = false })
    if (about) HikariSheet("About Hikari", { about = false }) {
        val versionName = remember(context) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) { HikariMark(); Text("Hikari $versionName", style = MaterialTheme.typography.headlineMedium) }
            Text(
                "A simple app for watching YouTube with fewer distractions.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Your saved videos and settings stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Licensed under GPL-3.0-or-later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsLabel(text: String, first: Boolean = false) {
    Text(
        text.uppercase(),
        Modifier.padding(top = if (first) 8.dp else 24.dp, bottom = 12.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    value: String? = null,
    checked: Boolean? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().then(
            if (checked != null) Modifier.toggleable(
                checked,
                role = Role.Switch,
                onValueChange = { onClick() }) else Modifier.clickable(role = Role.Button, onClick = onClick)
        ).heightIn(min = 68.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall
            ); Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
        if (checked != null) Switch(
            checked,
            onCheckedChange = null,
            modifier = Modifier.scale(.75f),
            colors = SwitchDefaults.colors(
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        else {
            if (value != null) Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ); Icon(
                if (value != null) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.ChevronRight,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
}
