package com.hikari.app

import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun CreatorAvatar(name: String, url: String = "", size: Dp = 36.dp, channelUrl: String = "") {
    var resolved by remember(url, channelUrl) { mutableStateOf(url) }
    var failed by remember(resolved) { mutableStateOf(false) }
    LaunchedEffect(url, channelUrl) {
        if (url.isBlank() && channelUrl.isNotBlank()) try { resolved = WatchMetadataRepository.avatar(channelUrl) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "H", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        if (resolved.isNotBlank() && !failed) AsyncImage(resolved, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
    }
}

@Composable
fun VideoArtwork(video: Video, modifier: Modifier = Modifier, highResolution: Boolean = false) {
    var fallback by remember(video.id) { mutableStateOf(false) }
    AsyncImage(
        model = if (highResolution && !fallback) "https://i.ytimg.com/vi/${video.id}/maxresdefault.jpg" else video.thumbnail,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        onError = { fallback = true }
    )
}

@Composable
fun VideoThumbnail(video: Video, modifier: Modifier = Modifier, radius: Dp = 8.dp, highResolution: Boolean = false, onPlay: (() -> Unit)? = null) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant).then(if (onPlay == null) Modifier else Modifier.clickable(onClick = onPlay).semantics { contentDescription = "Play ${video.title}" })) {
        VideoArtwork(video, Modifier.fillMaxSize(), highResolution)
        if (video.duration.isNotBlank()) Surface(Modifier.align(Alignment.BottomEnd).padding(5.dp), color = Color.Black.copy(alpha = .8f), shape = RoundedCornerShape(4.dp)) {
            Text(video.duration, Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun VideoMenu(video: Video, saved: Boolean, onSave: () -> Unit, onChannel: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf(false) }
    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Options for ${video.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp)) }
    if (menu) HikariSheet("Video options", { menu = false }) {
        Text(video.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        SheetOption(if (saved) "Remove from Watch later" else "Save to Watch later", icon = Icons.Outlined.BookmarkBorder) { onSave(); menu = false }
        SheetOption("Add to playlist", icon = Icons.AutoMirrored.Outlined.PlaylistAdd) { menu = false; playlist = true }
        if (onChannel != null && video.channelUrl.isNotBlank()) SheetOption("View channel", icon = Icons.Outlined.AccountCircle) { menu = false; onChannel() }
        if (onRemove != null) SheetOption("Remove from this collection", icon = Icons.Outlined.RemoveCircleOutline) { onRemove(); menu = false }
    }
    if (playlist) PlaylistPicker(video) { playlist = false }
}

@Composable
fun CompactVideo(video: Video, saved: Boolean, onPlay: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier, onChannel: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, progress: Long? = null) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(120.dp).aspectRatio(16f / 9)) {
                VideoThumbnail(video, Modifier.fillMaxSize(), onPlay = onPlay)
                val total = durationSeconds(video.duration)
                if (progress != null && total > 0) HikariProgress(progress / 1000f / total, Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter), trackColor = Color.Transparent)
            }
            Column(Modifier.weight(1f).clickable(onClick = onPlay).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (video.views.isNotBlank()) Text(video.views, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.width(36.dp)) { VideoMenu(video, saved, onSave, onChannel, onRemove) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), thickness = .5.dp)
    }
}

@Composable
fun VideoCard(video: Video, saved: Boolean, onPlay: () -> Unit, onSave: () -> Unit, onChannel: (() -> Unit)? = null, edgeToEdge: Boolean = false) {
    Column {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .985f else 1f, tween(if (LocalReduceMotion.current) 0 else 120), label = "Video press")
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = interaction, indication = null, onClick = onPlay)) {
            VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9), radius = if (edgeToEdge) 0.dp else 12.dp, highResolution = true)
        }
        Row(Modifier.fillMaxWidth().padding(start = if (edgeToEdge) 20.dp else 0.dp, end = if (edgeToEdge) 8.dp else 0.dp, top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).then(if (onChannel != null) Modifier.clickable(onClick = onChannel) else Modifier), contentAlignment = Alignment.CenterStart) { CreatorAvatar(video.channel, video.avatarUrl, 32.dp) }
            Column(Modifier.weight(1f).clickable(onClick = onPlay).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(video.channel, video.views).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VideoMenu(video, saved, onSave, onChannel)
        }
    }
}

@Composable
fun ResumeVideo(entry: HistoryEntry, onPlay: () -> Unit) {
    val video = entry.video
    val seconds = entry.position / 1000
    val duration = durationSeconds(video.duration)
    Column(Modifier.padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)).background(Color.Black).clickable(onClick = onPlay)) {
            VideoArtwork(video, Modifier.fillMaxSize(), highResolution = true)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .7f)))))
            Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(6.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = Color.White)
                    Text(if (duration > seconds) "${((duration - seconds) / 60).coerceAtLeast(1)} min left" else "Continue watching", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(clockTime(seconds) + if (duration > 0) " / ${video.duration}" else "", Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            FilledIconButton(onClick = onPlay, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFF3F3F1), contentColor = Color.Black)) { Icon(Icons.Outlined.PlayArrow, "Continue ${video.title}") }
            if (duration > 0) HikariProgress(seconds.toFloat() / duration, Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter), trackColor = Color.White.copy(alpha = .25f))
        }
        Text(video.title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 5.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CreatorAvatar(video.channel, video.avatarUrl, 18.dp)
            Text(video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun ShelfVideo(video: Video, onPlay: () -> Unit) {
    Column(Modifier.width(160.dp).clickable(onClick = onPlay)) {
        VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9))
        Text(video.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(video.channel, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
