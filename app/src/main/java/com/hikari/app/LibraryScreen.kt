package com.hikari.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@UnstableApi
@Composable
fun LibraryScreen(model: HikariViewModel, play: (Video) -> Unit, open: (HikariRoute) -> Unit, search: () -> Unit) {
    val library = Library.get(LocalContext.current)
    val lib by library.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val entries = downloadEntries(rememberDownloads().value)
    var create by remember { mutableStateOf(false) }
    HikariList(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Good videos, kept close.", Modifier.padding(top = 4.dp, bottom = 22.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SavedCover(app.saved) { open(HikariRoute("collection", "saved", "Watch later")) }
                Spacer(Modifier.height(14.dp))
                LibraryLink("Downloads", if (entries.isEmpty()) "Your offline collection" else "${entries.count { it.complete }} ready" + entries.count { !it.complete }.takeIf { it > 0 }?.let { " · $it in progress" }.orEmpty(), Icons.Outlined.FileDownload) { open(HikariRoute("downloads")) }
                LibraryLink("Liked videos", videoCount(lib.liked.size), Icons.Outlined.FavoriteBorder) { open(HikariRoute("collection", "liked", "Liked videos")) }
                LibraryLink("History", if (lib.history.isEmpty()) "Pick up where you left off" else "${videoCount(lib.history.size)} watched", Icons.Outlined.History) { open(HikariRoute("collection", "history", "History")) }
                SectionHeading("Your playlists", Modifier.padding(top = 18.dp), action = "+ New", onAction = { create = true })
            }
        }
        if (lib.playlists.isEmpty()) item {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { create = true }.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) { Text("Start a collection", style = MaterialTheme.typography.titleSmall); Text("A mood, a hobby, a little inspiration.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Outlined.Add, "Create playlist", Modifier.size(20.dp))
            }
        }
        if (lib.playlists.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(lib.playlists, key = { it.id }) { playlist -> PlaylistCover(playlist) { open(HikariRoute("playlist", playlist.id, playlist.name)) } }
            }
        }
        if (lib.history.isNotEmpty()) {
            item { SectionHeading("Recently watched", Modifier.padding(horizontal = 20.dp).padding(top = 22.dp), action = "See all", onAction = { open(HikariRoute("collection", "history", "History")) }) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(lib.history.take(8), key = { it.video.id }) { entry -> ShelfVideo(entry.video) { play(entry.video) } } } }
        }
    }
    if (create) PlaylistPicker(null) { create = false }
}

@Composable
private fun SavedCover(videos: List<Video>, open: () -> Unit) {
    val hasArtwork = videos.isNotEmpty()
    val foreground = if (hasArtwork) Color.White else MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().heightIn(min = 182.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = open)) {
        if (hasArtwork) {
            VideoArtwork(videos.first(), Modifier.matchParentSize(), highResolution = true)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .86f), Color.Black.copy(alpha = .2f)))))
        } else Box(Modifier.align(Alignment.CenterEnd).width(170.dp).height(160.dp)) { EmptyIllustration(Modifier.fillMaxSize()) }
        Column(Modifier.padding(22.dp).widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(16.dp), tint = foreground.copy(alpha = .8f)); Text("SAVED FOR YOU", style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = .8f)) }
            Text("Watch later", style = MaterialTheme.typography.headlineMedium, color = foreground)
            Text(if (hasArtwork) videoCount(videos.size) else "Your next great watch\nstarts here.", style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = .75f))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Text("Open collection", style = MaterialTheme.typography.labelMedium, color = foreground); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = foreground) }
        }
    }
}

@Composable
private fun LibraryLink(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 74.dp).clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start = 39.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
}

@Composable
private fun CollectionArtwork(videos: List<Video>, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (videos.isNotEmpty()) {
            VideoArtwork(videos.first(), Modifier.matchParentSize(), highResolution = true)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)))))
            Icon(icon, null, Modifier.align(Alignment.BottomStart).padding(10.dp).size(22.dp), tint = Color.White)
        } else Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PlaylistCover(playlist: Playlist, onClick: () -> Unit) {
    Column(Modifier.width(146.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Box(Modifier.padding(horizontal = 8.dp).offset(y = (-6).dp).fillMaxWidth().height(30.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)))
            CollectionArtwork(playlist.videos, Icons.AutoMirrored.Outlined.PlaylistPlay, Modifier.fillMaxWidth().aspectRatio(1.25f))
        }
        Text(playlist.name, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(videoCount(playlist.videos.size), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CollectionHeader(title: String, videos: List<Video>, icon: ImageVector, play: (Video) -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        CollectionArtwork(videos, icon, Modifier.size(100.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val minutes = videos.sumOf { durationSeconds(it.duration) } / 60
            Text(videoCount(videos.size) + if (minutes > 0) " · ${minutes} min" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (videos.isNotEmpty()) HikariButton("Play first", { play(videos.first()) }, icon = Icons.Outlined.PlayArrow)
        }
        trailing()
    }
}

@Composable
private fun CollectionVideo(video: Video, saved: Boolean, play: () -> Unit, save: () -> Unit, progress: Long? = null, remove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(124.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(10.dp))) {
            VideoThumbnail(video, Modifier.fillMaxSize(), onPlay = play)
            val total = durationSeconds(video.duration)
            if (progress != null && total > 0) HikariProgress(progress / 1000f / total, Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter), trackColor = Color.White.copy(alpha = .2f))
        }
        Column(Modifier.weight(1f).clickable(onClick = play).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null && progress > 0) Text("Watched to ${clockTime(progress / 1000)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(36.dp)) { VideoMenu(video, saved, save, onRemove = remove) }
    }
}

@Composable
fun CollectionScreen(kind: String, model: HikariViewModel, play: (Video) -> Unit, search: () -> Unit) {
    val library = Library.get(LocalContext.current)
    val lib by library.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val videos = when (kind) { "liked" -> lib.liked; "history" -> lib.history.map { it.video }; else -> app.saved }
    var clear by remember { mutableStateOf(false) }
    var oldestFirst by rememberSaveable(kind) { mutableStateOf(false) }
    HikariList(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        if (videos.isEmpty()) item {
            HikariEmpty(when (kind) { "history" -> "Your story starts here."; "liked" -> "Keep what moves you."; else -> "Something to look forward to." }, when (kind) { "history" -> "The videos you watch will find a home here. Pick up exactly where you left off."; "liked" -> "A little inspiration, a favorite song. Tap Like on a video to keep it close."; else -> "Save videos as you explore. They'll be here when you have a moment." }, { HikariButton("Explore videos", search, icon = Icons.Outlined.Search) })
        } else if (kind == "history") {
            item { Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Pick up the thread.", style = MaterialTheme.typography.headlineSmall); Text(videoCount(videos.size) + " watched", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { clear = true }) { Icon(Icons.Outlined.DeleteOutline, "Clear history") } } }
            val zone = ZoneId.systemDefault()
            lib.history.groupBy { Instant.ofEpochMilli(it.watchedAt).atZone(zone).toLocalDate() }.forEach { (date, entries) ->
                item(key = "date:$date") { Text(when (date) { LocalDate.now() -> "Today"; LocalDate.now().minusDays(1) -> "Yesterday"; else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy")) }, Modifier.padding(top = 22.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(entries, key = { it.video.id }) { entry -> CollectionVideo(entry.video, app.saved.any { it.id == entry.video.id }, { play(entry.video) }, { model.toggleSave(entry.video) }, progress = entry.position) }
            }
        } else {
            item { CollectionHeader(if (kind == "liked") "Your favorites." else "On your own time.", videos, if (kind == "liked") Icons.Outlined.FavoriteBorder else Icons.Outlined.BookmarkBorder, play) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("IN THIS COLLECTION", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { oldestFirst = !oldestFirst }, contentPadding = PaddingValues(horizontal = 0.dp)) { Icon(Icons.AutoMirrored.Outlined.Sort, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (oldestFirst) "Oldest first" else "Newest first", style = MaterialTheme.typography.labelSmall) } } }
            items(if (oldestFirst) videos.reversed() else videos, key = { it.id }) { video -> CollectionVideo(video, app.saved.any { it.id == video.id }, { play(video) }, { model.toggleSave(video) }, remove = if (kind == "liked") ({ library.like(video) }) else null) }
        }
    }
    if (clear) ConfirmRemoval("Clear watch history?", "Your saved videos and playlists will stay in your library.", "Clear history", { library.clearHistory(); clear = false }, { clear = false })
}

@Composable
fun PlaylistScreen(id: String, model: HikariViewModel, play: (Video) -> Unit, search: () -> Unit, onBack: () -> Unit) {
    val library = Library.get(LocalContext.current)
    val lib by library.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val playlist = lib.playlists.firstOrNull { it.id == id }
    var remove by remember { mutableStateOf(false) }
    if (playlist == null) { HikariEmpty("Playlist unavailable", "This collection may have been removed.", { HikariButton("Back to Library", onBack) }); return }
    HikariList(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        if (playlist.videos.isNotEmpty()) {
            item { CollectionHeader(playlist.name, playlist.videos, Icons.AutoMirrored.Outlined.PlaylistPlay, play) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("PRIVATE PLAYLIST", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); IconButton(onClick = { remove = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            items(playlist.videos, key = { it.id }) { video -> CollectionVideo(video, app.saved.any { it.id == video.id }, { play(video) }, { model.toggleSave(video) }, remove = { library.removeFromPlaylist(id, video) }) }
        } else item {
            HikariEmpty("Make it yours.", "A place for videos that belong together. Use Save in the player to add to ${playlist.name}.", { HikariButton("Find the first video", search, icon = Icons.Outlined.Search) })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(onClick = { remove = true }) { Text("Delete playlist", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
    if (remove) ConfirmRemoval("Delete this playlist?", "Only this collection is removed. Videos saved elsewhere stay in your library.", "Delete playlist", { library.removePlaylist(id); remove = false; onBack() }, { remove = false })
}

@Composable
fun ConfirmRemoval(title: String, description: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(description) }, confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
