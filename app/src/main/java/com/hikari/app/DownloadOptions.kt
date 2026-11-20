package com.hikari.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DownloadOptions(resolved: ResolvedVideo?, loading: Boolean, error: String?, onRetry: () -> Unit, onDownload: (StreamChoice) -> Unit, onDismiss: () -> Unit) {
    val choices = resolved?.downloadChoices.orEmpty()
    var quality by remember(resolved?.video?.id, choices) { mutableStateOf(choices.firstOrNull { it.label.startsWith("720p") }?.label ?: choices.firstOrNull()?.label) }
    var qualityMenu by remember { mutableStateOf(false) }
    var trackId by remember(resolved?.video?.id, resolved?.audioTracks) { mutableStateOf(resolved?.audioTracks?.firstOrNull { it.original }?.id ?: resolved?.audioTracks?.firstOrNull()?.id) }
    val choice = choices.firstOrNull { it.label == quality }
    val canChooseAudio = choice?.audio != null || choice?.label == "Audio only"
    val audioTracks = resolved?.audioTracks.orEmpty()
    HikariSheet("Download video", onDismiss) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { HikariLoader("Finding video and audio tracks") }
                error != null -> HikariEmpty("Download options unavailable", "Check your connection and try again.", { HikariButton("Try again", onRetry) })
                choices.isEmpty() -> Text(if (resolved?.choices?.firstOrNull()?.label == "Offline") "This video is already available offline." else "No downloadable formats are available for this video.", Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Text(resolved?.video?.title.orEmpty(), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Text("Video quality", Modifier.padding(top = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                    Box {
                        Surface(onClick = { qualityMenu = true }, color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(quality.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                Icon(Icons.Outlined.ExpandMore, "Choose download quality")
                            }
                        }
                        DropdownMenu(qualityMenu, { qualityMenu = false }) {
                            choices.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { quality = item.label; qualityMenu = false }, trailingIcon = { RadioButton(quality == item.label, onClick = null) }) }
                        }
                    }
                    Text("Audio language", Modifier.padding(top = 22.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                    if (canChooseAudio && audioTracks.isNotEmpty()) audioTracks.forEach { track ->
                        SheetOption(track.label, if (track.original) "Original" else null, selected = track.id == trackId) { trackId = track.id }
                    } else Text("Original audio included with this format", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (choice != null && !loading && error == null) HikariButton("Download · ${choice.label}", { onDownload(choice.withDownloadAudio(if (canChooseAudio) audioTracks.firstOrNull { it.id == trackId } else null)) }, Modifier.fillMaxWidth().padding(top = 16.dp), icon = Icons.Outlined.FileDownload)
    }
}
