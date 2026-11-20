package com.hikari.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.channel.ChannelInfo
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow

object SubscriptionFeed {
    suspend fun load(subscriptions: List<Subscription>): Pair<List<Video>, Int> = withContext(Dispatchers.IO) {
        Extractor.init()
        val videos = mutableListOf<Pair<Long, Video>>()
        var failed = 0
        subscriptions.forEach { sub ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                val service = org.schabi.newpipe.extractor.NewPipe.getService(0)
                val channel = ChannelInfo.getInfo(sub.url)
                val tab = channel.tabs.firstOrNull { it.contentFilters.contains("videos") } ?: channel.tabs.firstOrNull() ?: kotlin.error("No uploads tab")
                val uploads = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, tab)
                uploads.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>().filterNot { it.isShortFormContent }.forEach { item ->
                    item.asVideo()?.let { video -> videos += (item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0L) to video.copy(channel = sub.name, channelUrl = sub.url, avatarUrl = channel.avatars.firstOrNull()?.url.orEmpty()) }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed++ }
        }
        videos.sortedByDescending { it.first }.map { it.second }.distinctBy { it.id } to failed
    }
}

@Composable
fun SubscriptionsScreen(model: HikariViewModel, play: (Video) -> Unit, onChannel: (Video) -> Unit, search: () -> Unit) {
    val lib by Library.get(LocalContext.current).state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val preferences by PreferencesStore.get(LocalContext.current).state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(lib.subscriptions) { if (lib.subscriptions.none { it.url == selected }) selected = "" }
    HikariList(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (lib.subscriptions.isEmpty()) item { HikariEmpty("Keep your favorites close.", "Follow a creator from their channel or a video. Their latest uploads will appear here.", { HikariButton("Explore videos", search, icon = Icons.Outlined.Search) }, Icons.Outlined.Subscriptions) }
        else {
            item {
                LazyRow(Modifier.padding(top = 14.dp, bottom = 12.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { CreatorFilter("All", "", selected.isEmpty(), "", { selected = "" }, true) }
                    items(lib.subscriptions, key = { it.url }) { creator -> CreatorFilter(creator.name, app.feed.firstOrNull { it.channelUrl == creator.url }?.avatarUrl.orEmpty(), selected == creator.url, creator.url, { selected = creator.url }) }
                }
            }
            item { SectionHeading(if (selected.isEmpty()) "Latest uploads" else lib.subscriptions.firstOrNull { it.url == selected }?.name ?: "Latest uploads", Modifier.padding(horizontal = 20.dp), action = if (app.feedLoading) "Refreshing…" else "Refresh", onAction = { if (!app.feedLoading) model.refreshFeed(lib.subscriptions) }) }
            if (selected.isNotEmpty()) item { TextButton(onClick = { lib.subscriptions.firstOrNull { it.url == selected }?.let { onChannel(Video("", "", it.name, "", "", it.url)) } }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("View channel"); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) } }
            if (app.feedLoading && app.feed.isEmpty()) items(2) { Box(Modifier.padding(20.dp)) { VideoSkeleton() } }
            if (app.feedFailures > 0 && !app.feedLoading) item { Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("Some channels couldn't be refreshed.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { model.refreshFeed(lib.subscriptions) }) { Text("Retry") } } }
            val videos = filterDiscovery(if (selected.isEmpty()) app.feed else app.feed.filter { it.channelUrl == selected }, preferences, lib.history)
            if (videos.isEmpty() && !app.feedLoading && app.feedFailures == 0) item { HikariEmpty("No uploads yet", "New videos from your creators will appear here.") }
            items(videos, key = { it.id }) { video -> VideoCard(video, app.saved.any { it.id == video.id }, { play(video) }, { model.toggleSave(video) }, onChannel = { onChannel(video) }, edgeToEdge = true) }
        }
    }
}

@Composable
private fun CreatorFilter(name: String, avatar: String, active: Boolean, url: String, onSelect: () -> Unit, all: Boolean = false) {
    Column(Modifier.width(62.dp).clickable(onClick = onSelect).semantics { selected = active }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(58.dp).border(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).padding(4.dp), contentAlignment = Alignment.Center) {
            if (all) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Subscriptions, null, Modifier.size(22.dp)) }
            else CreatorAvatar(name, avatar, 48.dp, url)
        }
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
