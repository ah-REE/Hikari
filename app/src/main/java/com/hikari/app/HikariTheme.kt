package com.hikari.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.lerp

/** Interpolate the palette in place: a theme change never recreates a player or screen. */
@Composable
fun animatedHikariColors(dark: Boolean, reduced: Boolean): ColorScheme {
    val fraction by animateFloatAsState(if (dark) 1f else 0f, tween(if (reduced) 0 else 300), label = "Appearance")
    val a = HikariDesign.light
    val b = HikariDesign.dark
    return a.copy(
        primary = lerp(a.primary,b.primary,fraction), onPrimary = lerp(a.onPrimary,b.onPrimary,fraction),
        secondary = lerp(a.secondary,b.secondary,fraction), onSecondary = lerp(a.onSecondary,b.onSecondary,fraction),
        background = lerp(a.background,b.background,fraction), onBackground = lerp(a.onBackground,b.onBackground,fraction),
        surface = lerp(a.surface,b.surface,fraction), onSurface = lerp(a.onSurface,b.onSurface,fraction),
        surfaceVariant = lerp(a.surfaceVariant,b.surfaceVariant,fraction), onSurfaceVariant = lerp(a.onSurfaceVariant,b.onSurfaceVariant,fraction),
        secondaryContainer = lerp(a.secondaryContainer,b.secondaryContainer,fraction), onSecondaryContainer = lerp(a.onSecondaryContainer,b.onSecondaryContainer,fraction),
        outline = lerp(a.outline,b.outline,fraction), outlineVariant = lerp(a.outlineVariant,b.outlineVariant,fraction),
        surfaceContainer = lerp(a.surfaceContainer,b.surfaceContainer,fraction), surfaceContainerHigh = lerp(a.surfaceContainerHigh,b.surfaceContainerHigh,fraction),
        surfaceContainerHighest = lerp(a.surfaceContainerHighest,b.surfaceContainerHighest,fraction))
}
