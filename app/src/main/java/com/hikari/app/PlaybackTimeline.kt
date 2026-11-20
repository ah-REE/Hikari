package com.hikari.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Scrubbing previews locally; only releasing the thumb seeks the playback engine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackTimeline(
    current: Float,
    duration: Float,
    buffered: Float,
    onSeek: (Float) -> Unit,
    onSeeking: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    segments: List<SponsorSegment> = emptyList(),
    compact: Boolean = false
) {
    var preview by remember { mutableStateOf<Float?>(null) }
    val value = (preview ?: current).coerceIn(0f, duration.coerceAtLeast(1f))
    val accent = MaterialTheme.colorScheme.primary
    DisposableEffect(Unit) { onDispose { onSeeking(false) } }
    Column(modifier) {
        if (!compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clockTime(value.toLong()), style = MaterialTheme.typography.labelMedium, color = Color.White)
            Spacer(Modifier.weight(1f))
            if (preview != null) Text(
                "Release to seek",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = .7f),
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                if (duration > 0) clockTime(duration.toLong()) else "LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = .7f)
            )
        }
        Slider(
            value,
            onValueChange = { preview = it; onSeeking(true) },
            onValueChangeFinished = { preview?.let(onSeek); preview = null; onSeeking(false) },
            valueRange = 0f..duration.coerceAtLeast(1f),
            enabled = duration > 0,
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Playback position" },
            thumb = {
                Box(
                    Modifier.size(24.dp)
                        .background(if (preview != null) accent.copy(alpha = .28f) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(if (preview != null) 16.dp else 12.dp).background(Color.White, CircleShape)) }
            },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    val y = size.height / 2
                    fun line(fraction: Float, color: Color) {
                        if (fraction > 0) drawLine(
                            color,
                            Offset(0f, y),
                            Offset(size.width * fraction.coerceIn(0f, 1f), y),
                            (if (preview != null) 5 else 3).dp.toPx(),
                            StrokeCap.Round
                        )
                    }
                    line(1f, Color.White.copy(alpha = .18f))
                    line(buffered / duration.coerceAtLeast(1f), Color.White.copy(alpha = .36f))
                    line(state.value / duration.coerceAtLeast(1f), accent)
                    if (duration > 0) segments.forEach { segment ->
                        drawLine(
                            Color(0xFF55CE91),
                            Offset(size.width * (segment.start / duration).coerceIn(0f, 1f), y),
                            Offset(size.width * (segment.end / duration).coerceIn(0f, 1f), y),
                            4.dp.toPx(),
                            StrokeCap.Round
                        )
                    }
                }
            })
    }
}
