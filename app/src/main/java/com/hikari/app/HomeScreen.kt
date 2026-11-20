package com.hikari.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** A search-first landing page. History lives in Library; creator uploads in Following. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(model: HikariViewModel, play: (Video) -> Unit, search: () -> Unit, onChannel: (Video) -> Unit) {
    val app by model.state.collectAsStateWithLifecycle()
    val finder by model.timeFit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences by PreferencesStore.get(context).state.collectAsStateWithLifecycle()
    val library by Library.get(context).state.collectAsStateWithLifecycle()
    val videos = filterDiscovery(finder.videos, preferences, library.history)
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun find() {
        if (finder.topic.isBlank() || finder.loading) return
        keyboard?.hide()
        focus.clearFocus()
        model.findTimeFit()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        HikariList(
            Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "discovery") {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 24.dp)) {
                    Text(
                        "What’s worth\nwatching today?",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.semantics { heading() })
                    Text(
                        "A topic, a creator, a little curiosity.",
                        Modifier.padding(top = 10.dp, bottom = 22.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    Surface(
                        onClick = search,
                        color = Color.Transparent,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, colors.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Outlined.Search, null, Modifier.size(21.dp), tint = colors.primary)
                            Text(
                                "Search videos or paste a link",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                            Icon(Icons.Outlined.NorthEast, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (preferences.timeFitFinder) {
                item(key = "time_fit_form") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        HorizontalDivider(color = colors.outlineVariant, thickness = .5.dp)
                        Row(
                            Modifier.padding(top = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(22.dp), tint = colors.primary)
                            Text(
                                "How much time do you have?",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                        }
                        Text(
                            "Choose a time and a topic. Get up to three videos.",
                            Modifier.padding(top = 8.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TimeFitSearch.minuteOptions.forEach { minutes ->
                                FilterChip(
                                    selected = finder.minutes == minutes,
                                    onClick = { model.setTimeFitMinutes(minutes) },
                                    label = { Text("$minutes min") },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    leadingIcon = if (finder.minutes == minutes) ({
                                        Icon(
                                            Icons.Outlined.Check,
                                            null,
                                            Modifier.size(16.dp)
                                        )
                                    }) else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.primary.copy(
                                            alpha = .12f
                                        ),
                                        selectedLabelColor = colors.primary,
                                        selectedLeadingIconColor = colors.primary
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = finder.topic,
                            onValueChange = model::setTimeFitTopic,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("Topic or creator") },
                            placeholder = { Text("What are you curious about?") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = MaterialTheme.shapes.medium,
                            trailingIcon = if (finder.topic.isNotEmpty()) ({
                                IconButton(onClick = { model.setTimeFitTopic("") }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        "Clear topic"
                                    )
                                }
                            }) else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { find() })
                        )
                        HikariButton(
                            if (finder.loading) "Finding videos…" else "Find videos", ::find,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp), icon = Icons.Outlined.Search,
                            enabled = finder.topic.isNotBlank() && !finder.loading
                        )
                        Text(
                            "Each video is ${finder.minutes} minutes or less at normal speed. Find again for different videos.",
                            Modifier.padding(top = 10.dp, bottom = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                when {
                    finder.loading -> item(key = "time_fit_loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                            HikariLoader("Finding videos that fit…")
                        }
                    }

                    finder.error != null -> item(key = "time_fit_error") {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Couldn't find videos right now", style = MaterialTheme.typography.titleSmall)
                            Text(
                                finder.error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                            TextButton(onClick = ::find) { Text("Try again") }
                        }
                    }

                    finder.searched && videos.isEmpty() -> item(key = "time_fit_empty") {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (finder.excludingSeen) "No new matches in this small search" else "No matches in this small search",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Try another topic or a longer time. Live streams and videos without a known runtime aren't included.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    finder.searched -> {
                        item(key = "time_fit_heading") {
                            SectionHeading(
                                "A few that fit",
                                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                count = videos.size
                            )
                        }
                        items(videos, key = { "time_fit:${it.id}" }) { video ->
                            CompactVideo(
                                video,
                                app.saved.any { it.id == video.id },
                                { play(video) },
                                { model.toggleSave(video) },
                                modifier = Modifier.padding(horizontal = 20.dp),
                                onChannel = { onChannel(video) })
                        }
                        item(key = "time_fit_end") {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Text(
                                    if (finder.incomplete) "Some results couldn't load. You can still play these, or try again." else "That's your short list. Pick one, or change your topic or time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                                if (finder.incomplete) TextButton(onClick = ::find) { Text("Try again") }
                            }
                        }
                    }
                }
            }
        }
    }
}
