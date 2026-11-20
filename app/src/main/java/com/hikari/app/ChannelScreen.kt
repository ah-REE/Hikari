package com.hikari.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

fun StreamInfoItem.asVideo(): Video? {
    val id = VideoLinks.id(url) ?: return null
    val length = when {
        streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM -> "LIVE"
        duration > 0 -> clockTime(duration)
        else -> ""
    }
    return Video(id, name, uploaderName, length, if (viewCount < 0) "" else "%,d views".format(viewCount), uploaderUrl, uploaderAvatars.firstOrNull()?.url.orEmpty(), isShort = isShortFormContent)
}

@Composable
fun ChannelScreen(url: String, name: String, saved: List<Video>, play: (Video) -> Unit, save: (Video) -> Unit, onPlaylist: (String, String) -> Unit) {
    val library = Library.get(LocalContext.current)
    val lib by library.state.collectAsStateWithLifecycle()
    var info by remember(url) { mutableStateOf<ChannelInfo?>(null) }
    var error by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    var selected by rememberSaveable(url) { mutableStateOf("videos") }
    LaunchedEffect(url, retry) {
        error = false
        try { info = withContext(Dispatchers.IO) { Extractor.init(); ChannelInfo.getInfo(url) } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
    }
    val channel = info
    if (channel == null) {
        if (error) HikariEmpty("Couldn't load this channel", "Check your connection and try again.", { HikariButton("Try again", { retry++ }) }, Icons.Outlined.WifiOff)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { HikariLoader("Opening channel") }
        return
    }
    val tabs = channel.tabs.filter { tab -> tab.contentFilters.any { it in listOf("videos", "shorts", "livestreams", "podcasts", "playlists") } }
    val active = tabs.firstOrNull { selected in it.contentFilters } ?: tabs.firstOrNull()
    val avatar = channel.avatars.firstOrNull()?.url.orEmpty()
    val channelName = channel.name.ifBlank { name }
    Column(Modifier.fillMaxSize()) {
        // Keep the creator identity compact so the first uploads remain above the fold.
        channel.banners.firstOrNull()?.url?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(94.dp), contentScale = ContentScale.Crop) }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CreatorAvatar(channelName, avatar, 54.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(channelName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (channel.subscriberCount >= 0) Text("%,d subscribers".format(channel.subscriberCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { library.subscribe(Video("", "", channelName, "", "", url, avatar)) }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(if (lib.subscriptions.any { it.url == url }) "Following" else "Follow", style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            val labels = mapOf("videos" to "Videos", "shorts" to "Shorts", "livestreams" to "Live", "podcasts" to "Podcasts", "playlists" to "Playlists")
            (tabs.mapNotNull { tab -> tab.contentFilters.firstOrNull { it in labels } } + "about").distinct().forEach { tab ->
                val chosen = if (selected == "about") tab == "about" else active?.contentFilters?.contains(tab) == true
                Column(Modifier.selectable(chosen, role = Role.Tab) { selected = tab }.padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { Text(labels[tab] ?: "About", style = MaterialTheme.typography.labelLarge, color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Box(Modifier.width(24.dp).height(3.dp).background(if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
        if (selected == "about") Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("About $channelName", style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(channel.description.ifBlank { "This creator hasn't added a description." }, style = MaterialTheme.typography.bodyMedium) }
            Text("Channel link", style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (active != null) key(url, active.url) {
            ChannelUploads(active, channelName, url, avatar, saved, play, save, onPlaylist)
        } else HikariEmpty("No public uploads", "This creator hasn't shared any public videos yet.")
    }
}

@Composable
private fun ChannelUploads(handler: ListLinkHandler, name: String, url: String, avatar: String, saved: List<Video>, play: (Video) -> Unit, save: (Video) -> Unit, onPlaylist: (String, String) -> Unit) {
    var content by remember { mutableStateOf(emptyList<InfoItem>()) }
    var page by remember { mutableStateOf<Page?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() { if (loading) return; scope.launch {
        loading = true; error = false
        try {
            val result = withContext(Dispatchers.IO) {
                if (!initialized) ChannelTabInfo.getInfo(NewPipe.getService(0), handler).let { it.relatedItems to it.nextPage }
                else ChannelTabInfo.getMoreItems(NewPipe.getService(0), handler, requireNotNull(page)).let { it.items to it.nextPage }
            }
            content = (content + result.first).distinctBy { it.url }
            page = result.second?.takeIf { Page.isValid(it) }; initialized = true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true } finally { loading = false }
    } }
    LaunchedEffect(handler.url) { load() }
    val shorts = handler.contentFilters.contains("shorts")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        if (shorts) items(content.filterIsInstance<StreamInfoItem>().chunked(2), key = { it.first().url }) { row ->
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item -> item.asVideo()?.copy(channel = name, channelUrl = url, avatarUrl = avatar, isShort = true)?.let { video ->
                    Column(Modifier.weight(1f).clickable { play(video) }) {
                        AsyncImage(item.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnail, null, Modifier.fillMaxWidth().aspectRatio(9f / 14).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                        Text(video.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(video.views, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); VideoMenu(video, saved.any { it.id == video.id }, { save(video) }) }
                    }
                } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        } else items(content, key = { it.url }) { item ->
            when (item) {
                is StreamInfoItem -> item.asVideo()?.copy(channel = name, channelUrl = url, avatarUrl = avatar)?.let { video -> CompactVideo(video, saved.any { it.id == video.id }, { play(video) }, { save(video) }) }
                is PlaylistInfoItem -> Surface(onClick = { onPlaylist(item.url, item.name) }, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.width(120.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            AsyncImage(item.thumbnails.firstOrNull()?.url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null, Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .65f)).padding(5.dp), tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (item.streamCount >= 0) "${item.streamCount} ${if (item.streamCount == 1L) "video" else "videos"}" else "Playlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { HikariLoader() } }
        if (error) item { HikariEmpty("Couldn't load this section", "Try again when you're connected.", { HikariButton("Try again", ::load) }, Icons.Outlined.WifiOff) }
        if (!loading && !error && content.isEmpty()) item { HikariEmpty("Nothing here yet", "New uploads will appear in this section.") }
        if (!loading && !error && page != null) item { TextButton(onClick = ::load, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
    }
}

@Composable
fun RemotePlaylistScreen(url: String, saved: List<Video>, play: (Video) -> Unit, save: (Video) -> Unit) {
    var videos by remember(url) { mutableStateOf(emptyList<Video>()) }
    var page by remember(url) { mutableStateOf<Page?>(null) }
    var initialized by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() { if (loading) return; scope.launch {
        loading = true; error = false
        try {
            val result = withContext(Dispatchers.IO) {
                Extractor.init()
                if (!initialized) PlaylistInfo.getInfo(url).let { it.relatedItems to it.nextPage }
                else PlaylistInfo.getMoreItems(NewPipe.getService(0), url, requireNotNull(page)).let { it.items to it.nextPage }
            }
            videos = (videos + result.first.mapNotNull { it.asVideo() }).distinctBy { it.id }
            page = result.second?.takeIf { Page.isValid(it) }; initialized = true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true } finally { loading = false }
    } }
    LaunchedEffect(url) { load() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        items(videos, key = { it.id }) { video -> CompactVideo(video, saved.any { it.id == video.id }, { play(video) }, { save(video) }) }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { HikariLoader("Loading playlist") } }
        if (error) item { HikariEmpty("Couldn't load this playlist", "Check your connection and try again.", { HikariButton("Try again", ::load) }, Icons.Outlined.WifiOff) }
        if (!loading && !error && videos.isEmpty()) item { HikariEmpty("No public videos", "This playlist has no available videos.") }
        if (!loading && !error && page != null) item { TextButton(onClick = ::load, modifier = Modifier.fillMaxWidth()) { Text("Load more videos") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(video: Video, onDismiss: () -> Unit) {
    var comments by remember(video.id) { mutableStateOf(emptyList<CommentsInfoItem>()) }
    var page by remember(video.id) { mutableStateOf<Page?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var disabled by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch {
        loading = true; error = null
        try {
            val result = withContext(Dispatchers.IO) {
                Extractor.init()
                val url = "https://www.youtube.com/watch?v=${video.id}"
                if (!initialized) {
                    val info = CommentsInfo.getInfo(url)
                    Triple(info.relatedItems, info.nextPage, info.isCommentsDisabled)
                } else { val info = CommentsInfo.getMoreItems(NewPipe.getService(0), url, page); Triple(info.items, info.nextPage, false) }
            }
            comments = (comments + result.first).distinctBy { it.commentId }
            page = result.second?.takeIf { Page.isValid(it) }; disabled = result.third; initialized = true
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Could not load comments" } finally { loading = false }
    } }
    LaunchedEffect(video.id) { load() }
    HikariSheet("Comments", onDismiss) {
        LazyColumn(Modifier.fillMaxHeight(.85f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(comments, key = { it.commentId }) { comment -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CreatorAvatar(comment.uploaderName, comment.uploaderAvatars.firstOrNull()?.url.orEmpty(), size = 30.dp); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(comment.uploaderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(HtmlCompat.fromHtml(comment.commentText.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString(), style = MaterialTheme.typography.bodyMedium); Text("${comment.likeCount.coerceAtLeast(0)} likes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            if (disabled) item { Text("Comments are disabled for this video.") }
            error?.let { item { Text("Couldn't load comments. Check your connection and try again.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HikariLoader("Loading comments") } }
            if (!loading && (page != null || error != null)) item { OutlinedButton(onClick = ::load) { Text(if (error != null) "Retry" else "Load more") } }
            if (!loading && !disabled && comments.isEmpty() && error == null) item { Text("No comments available.") }
        }
    }
}
