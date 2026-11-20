package com.hikari.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun SponsorSkipPrompt(onSkip: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val shape = AbsoluteRoundedCornerShape(100.dp, 0.dp, 0.dp, 100.dp)
    // This transport control docks to the physical right edge, including in RTL layouts.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.widthIn(max = 320.dp).heightIn(min = 48.dp)
                .shadow(12.dp, shape, clip = false)
                .clip(shape)
                .background(Color.Black.copy(alpha = .4f))
                .drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    val inset = stroke / 2
                    val border = Path().apply {
                        moveTo(size.width, inset)
                        lineTo(size.height / 2, inset)
                        arcTo(Rect(inset, inset, size.height - inset, size.height - inset), -90f, -180f, false)
                        lineTo(size.width, size.height - inset)
                    }
                    // Leave the docked right edge open, as in the legacy control.
                    drawPath(border, Color.White.copy(alpha = .1f), style = Stroke(stroke))
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f, fill = false).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Skip sponsor segment", onClick = onSkip)
                    .semantics(mergeDescendants = true) { contentDescription = "Skip sponsor segment" }
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = .9f))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Sponsor", Modifier.weight(1f, fill = false).widthIn(max = 120.dp),
                    color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = .2f)))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Skip",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.FastForward, null, Modifier.size(16.dp), tint = Color.White)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    "Dismiss sponsor skip prompt",
                    Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = .65f)
                )
            }
        }
    }
}

@Preview(name = "Inline player", widthDp = 360, heightDp = 204, showBackground = true)
@Preview(name = "Large text", widthDp = 320, heightDp = 180, fontScale = 1.5f, showBackground = true)
@Composable
private fun SponsorSkipPromptPreview() {
    MaterialTheme(colorScheme = HikariDesign.dark, typography = HikariDesign.typography) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF648C89), Color(0xFF203D43))))) {
            SponsorSkipPrompt({}, {}, Modifier.align(AbsoluteAlignment.CenterRight))
        }
    }
}
