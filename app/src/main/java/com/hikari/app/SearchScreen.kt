package com.hikari.app

import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SearchScreen(model: HikariViewModel, play: (Video) -> Unit, onBack: () -> Unit, onChannel: (Video) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences by PreferencesStore.get(context).state.collectAsStateWithLifecycle()
    val library by Library.get(context).state.collectAsStateWithLifecycle()
    val videos = filterDiscovery(state.videos, preferences, library.history)
    var query by rememberSaveable { mutableStateOf(state.query) }
    val results = state.searched
    LaunchedEffect(state.query) { query = state.query }
    val loadingRows = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp / 80).coerceAtLeast(6)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun submit(value: String) {
        if (value.isBlank()) return
        query = value; keyboard?.hide(); focus.clearFocus()
        val videoId = VideoLinks.id(value)
        if (videoId != null) play(Video(videoId, "Loading video…", "", "", "", isShort = VideoLinks.isShort(value)))
        else model.search(value)
    }
    fun back() { keyboard?.hide(); focus.clearFocus(); if (results && model.previousSearch()) query = model.state.value.query else onBack() }
    BackHandler(results) { back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Row(Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(query, { query = it }, Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp).semantics { contentDescription = "Search videos or paste a link" },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                    decorationBox = { field -> if (query.isBlank()) Text("Search or paste a link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); field() })
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }, Modifier.size(44.dp)) { Icon(Icons.Outlined.Close, "Clear search", Modifier.size(18.dp)) }
            }
            IconButton(onClick = { submit(query) }) { Icon(Icons.Outlined.NorthEast, "Search") }
        }
        if (!results) LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item { Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Outlined.Link, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("YouTube links work here, too.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (state.recents.isNotEmpty()) {
                item { SectionHeading("Recent searches", action = "Clear", onAction = model::clearRecents) }
                items(state.recents.take(5), key = { it }) { recent ->
                    Row(Modifier.fillMaxWidth().clickable { submit(recent) }.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.History, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(recent, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
                }
            }

        } else key(state.searchGeneration) {
            HikariList(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item { Text("Results for “${state.query}”", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleSmall) }
                if (state.loading) items(loadingRows) { VideoSkeleton(compact = true) }
                else {
                    if (state.error != null) item { HikariEmpty("Couldn't load videos", "Check your connection and try again.", { HikariButton("Try again", { if (state.videos.isNotEmpty()) model.more() else model.search(state.query) }) }, Icons.Outlined.WifiOff) }
                    if (videos.isEmpty() && state.error == null) item { HikariEmpty("No videos found", "Try another title, topic, or creator.", icon = Icons.Outlined.Search) }
                    items(videos, key = { it.id }) { video -> CompactVideo(video, state.saved.any { it.id == video.id }, { play(video) }, { model.toggleSave(video) }, onChannel = { onChannel(video) }) }
                    if (state.hasMore) item { TextButton(onClick = model::more, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (state.loadingMore) "Loading…" else "Load more videos") } }
                }
            }
        }
    }
}
