package com.hikari.app

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

@UnstableApi
data class DownloadEntry(val download: Download, val audio: Download?, val video: Video?, val needsAudio: Boolean, val format: String = "") {
    val complete get() = download.state == Download.STATE_COMPLETED && (!needsAudio || audio?.state == Download.STATE_COMPLETED)
    val failed get() = download.state == Download.STATE_FAILED || audio?.state == Download.STATE_FAILED
    val paused get() = download.stopReason != 0 || (audio?.stopReason ?: 0) != 0
    val bytes get() = download.bytesDownloaded + (audio?.bytesDownloaded ?: 0L)
    val progress get(): Float {
        val tracks = listOfNotNull(download, audio)
        if (tracks.all { it.contentLength > 0 } && (!needsAudio || audio != null)) return (bytes.toDouble() / tracks.sumOf { it.contentLength }).toFloat().coerceIn(0f, 1f)
        return (tracks.sumOf { if (it.state == Download.STATE_COMPLETED) 100.0 else it.percentDownloaded.coerceAtLeast(0f).toDouble() } / if (needsAudio) 200.0 else 100.0).toFloat().coerceIn(0f, 1f)
    }
}

@UnstableApi
fun downloadEntries(downloads: List<Download>): List<DownloadEntry> = downloads.filterNot { it.request.id.endsWith("-audio") }.map { download ->
    val metadata = runCatching { JSONObject(String(download.request.data)) }.getOrNull()
    DownloadEntry(download, downloads.firstOrNull { it.request.id == download.request.id + "-audio" }, runCatching { metadata?.video() }.getOrNull(), metadata?.optString("audio")?.let { it.isNotBlank() && it != "null" } == true, listOf(metadata?.optString("quality"), metadata?.optString("audioTrackName")).filterNot { it.isNullOrBlank() || it == "null" }.joinToString(" · "))
}

@UnstableApi
@Composable
fun rememberDownloads(): State<List<Download>> {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    remember(context) { Downloads.manager(context) }
    return produceState(emptyList(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = withContext(Dispatchers.IO) { Downloads.all(context) }; delay(1000) }
        }
    }
}

@UnstableApi
@Composable
fun DownloadsScreen(play: (Video) -> Unit, search: () -> Unit) {
    val context = LocalContext.current
    val downloads by rememberDownloads()
    val entries = downloadEntries(downloads)
    var remove by remember { mutableStateOf<DownloadEntry?>(null) }
    var options by remember { mutableStateOf<DownloadEntry?>(null) }
    var filter by rememberSaveable { mutableStateOf("All") }
    val visible = entries.filter { when (filter) { "Ready" -> it.complete; "In progress" -> !it.complete; else -> true } }.sortedBy { it.complete }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)) {
        if (entries.isEmpty()) item { HikariEmpty("Take a little joy with you.", "Download from the player and enjoy your favorites anywhere. No connection needed.", { HikariButton("Explore videos", search, icon = Icons.Outlined.Search) }) }
        else {
            item {
                Text("Ready when you are.", style = MaterialTheme.typography.headlineSmall)
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${entries.count { it.complete }}", style = MaterialTheme.typography.headlineLarge); Text(if (entries.count { it.complete } == 1) "video offline" else "videos offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Column(Modifier.weight(1f).padding(start = 24.dp)) { Text(Formatter.formatShortFileSize(context, entries.sumOf { it.bytes }), style = MaterialTheme.typography.headlineSmall); Text("on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Outlined.OfflinePin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Ready", "In progress").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label, style = MaterialTheme.typography.labelMedium) }, shape = RoundedCornerShape(24.dp)) } }
            }
            if (visible.isEmpty()) item { HikariEmpty(if (filter == "Ready") "Your videos are on their way." else "All caught up.", if (filter == "Ready") "Completed downloads will appear here, ready to play." else "Nothing downloading right now. Your offline videos are ready whenever you are.", { TextButton(onClick = { filter = "All" }) { Text("Show all downloads") } }) }
            items(visible, key = { it.download.request.id }) { entry ->
                val video = entry.video
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (video != null) VideoThumbnail(video, Modifier.width(132.dp).aspectRatio(16f / 9), radius = 10.dp, onPlay = if (entry.complete) ({ play(video) }) else null)
                        Column(Modifier.weight(1f).then(if (entry.complete && video != null) Modifier.clickable { play(video) } else Modifier).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(video?.title ?: entry.download.request.id, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (video != null) Text(video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Formatter.formatShortFileSize(context, entry.bytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.width(36.dp)) { IconButton(onClick = { options = entry }) { Icon(Icons.Outlined.MoreVert, "Download options for ${video?.title ?: entry.download.request.id}", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(when { entry.complete -> Icons.Outlined.CheckCircle; entry.failed -> Icons.Outlined.ErrorOutline; entry.paused -> Icons.Outlined.PauseCircleOutline; else -> Icons.Outlined.FileDownload }, null, Modifier.size(15.dp), tint = if (entry.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(when { entry.complete -> entry.format.ifBlank { "Available offline" }; entry.failed -> "Couldn't finish downloading"; entry.paused -> "Paused · ${(entry.progress * 100).toInt()}%"; entry.download.state == Download.STATE_QUEUED -> "Waiting to download"; else -> "Downloading · ${(entry.progress * 100).toInt()}%" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = if (entry.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!entry.complete && !entry.failed) IconButton(onClick = { Downloads.pause(context, entry.download.request.id, !entry.paused) }, modifier = Modifier.size(48.dp)) { Icon(if (entry.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (entry.paused) "Resume download" else "Pause download", Modifier.size(20.dp)) }
                    }
                    if (!entry.complete) HikariProgress(entry.progress, Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
            }
        }
    }
    options?.let { entry -> HikariSheet("Download", { options = null }) {
        Text(entry.video?.title ?: entry.download.request.id, style = MaterialTheme.typography.bodyMedium)
        if (entry.format.isNotBlank()) Text(entry.format, Modifier.padding(top = 5.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry.complete && entry.video != null) SheetOption("Play offline", icon = Icons.Outlined.PlayArrow) { options = null; play(entry.video) }
        if (!entry.complete && !entry.failed) SheetOption(if (entry.paused) "Resume download" else "Pause download", icon = if (entry.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause) { Downloads.pause(context, entry.download.request.id, !entry.paused); options = null }
        if (entry.failed) Text("Remove this copy and download it again from the player.", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SheetOption("Remove download", icon = Icons.Outlined.DeleteOutline) { options = null; remove = entry }
    } }
    remove?.let { entry -> ConfirmRemoval("Remove this download?", "This removes the offline copy. Your saved videos and playlists stay in your library.", "Remove download", { Downloads.remove(context, entry.download.request.id); remove = null }, { remove = null }) }
}
