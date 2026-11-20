package com.hikari.app

import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalReduceMotion = staticCompositionLocalOf { false }

object HikariDesign {
    val font = FontFamily(Font(R.font.manrope_regular, FontWeight.Normal), Font(R.font.manrope_medium, FontWeight.Medium), Font(R.font.manrope_semibold, FontWeight.SemiBold), Font(R.font.manrope_bold, FontWeight.Bold), Font(R.font.manrope_extrabold, FontWeight.ExtraBold))
    val dark = darkColorScheme(
        primary = Color(0xFFFF785C), onPrimary = Color(0xFF170E0A), secondary = Color(0xFFF3F3F1), onSecondary = Color(0xFF101112),
        background = Color(0xFF101112), surface = Color(0xFF101112), onSurface = Color(0xFFF3F3F1), onBackground = Color(0xFFF3F3F1),
        surfaceVariant = Color(0xFF1B1D20), onSurfaceVariant = Color(0xFFA3A5A8), secondaryContainer = Color(0xFF2A2422), onSecondaryContainer = Color(0xFFF3F3F1),
        outline = Color(0xFF8C9197), outlineVariant = Color(0xFF2B2D30), surfaceContainer = Color(0xFF1B1D20), surfaceContainerHigh = Color(0xFF222428), surfaceContainerHighest = Color(0xFF222428), surfaceTint = Color.Transparent)
    val light = lightColorScheme(
        primary = Color(0xFFC54A30), onPrimary = Color.White, secondary = Color(0xFF191B1C), onSecondary = Color(0xFFFAF9F6),
        background = Color(0xFFFAF9F6), surface = Color(0xFFFAF9F6), onSurface = Color(0xFF191B1C), onBackground = Color(0xFF191B1C),
        surfaceVariant = Color(0xFFEFEEE9), onSurfaceVariant = Color(0xFF656966), secondaryContainer = Color(0xFFF4EDE7), onSecondaryContainer = Color(0xFF191B1C),
        outline = Color(0xFF646B66), outlineVariant = Color(0xFFDEDED8), surfaceContainer = Color(0xFFEFEEE9), surfaceContainerHigh = Color(0xFFE5E4DE), surfaceContainerHighest = Color(0xFFE5E4DE), surfaceTint = Color.Transparent)
    private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)
    val typography = Typography(
        displaySmall = type(32, 39, FontWeight.Bold, -1.2f), headlineLarge = type(30, 37, FontWeight.Bold, -1f), headlineMedium = type(26, 33, FontWeight.SemiBold, -.8f), headlineSmall = type(23, 30, FontWeight.Bold, -.6f),
        titleLarge = type(21, 28, FontWeight.Bold, -.5f), titleMedium = type(18, 24, FontWeight.Bold, -.4f), titleSmall = type(14, 20, FontWeight.SemiBold, -.15f),
        bodyLarge = type(16, 25), bodyMedium = type(14, 22), bodySmall = type(12, 18), labelLarge = type(13, 19, FontWeight.SemiBold), labelMedium = type(12, 17, FontWeight.Medium), labelSmall = type(11, 16, FontWeight.Medium))
}

@Composable
fun HikariMark(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(27.dp)) {
        val w = size.width
        drawLine(color, Offset(w * .24f, w * .13f), Offset(w * .39f, w * .88f), w * .18f, StrokeCap.Round)
        drawLine(color, Offset(w * .64f, w * .27f), Offset(w * .74f, w * .73f), w * .18f, StrokeCap.Round)
        drawLine(color, Offset(w * .12f, w * .66f), Offset(w * .87f, w * .33f), w * .13f, StrokeCap.Round)
    }
}

@Composable
fun HikariLoader(label: String? = null, modifier: Modifier = Modifier) {
    val reduced = LocalReduceMotion.current
    val angle = if (reduced) 270f else {
        val transition = rememberInfiniteTransition(label = "Loading orbit")
        val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1250, easing = LinearEasing)), label = "Orbit")
        rotation
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.semantics { contentDescription = label ?: "Loading"; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                drawCircle(colors.outlineVariant, style = Stroke(2.dp.toPx()))
                drawArc(colors.primary, angle, 95f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            HikariMark(Modifier.size(23.dp))
        }
        label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}

@Composable
fun HikariTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = if (onBack == null) 20.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text(title, Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, count: Int? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            count?.let { Text(" $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(start = 12.dp)) { Text(action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun HikariButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun HikariEmpty(title: String, body: String, action: @Composable () -> Unit = {}, icon: ImageVector = Icons.Outlined.PlayCircleOutline) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyIllustration()
        Text(title, Modifier.widthIn(max = 320.dp).padding(top = 4.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, Modifier.widthIn(max = 290.dp).padding(top = 10.dp, bottom = 22.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

@Composable
fun EmptyPanel(title: String, body: String, action: @Composable () -> Unit = {}) = HikariEmpty(title, body, action)

@Composable
fun VideoSkeleton(compact: Boolean = false) {
    val opacity = if (LocalReduceMotion.current) 1f else {
        val transition = rememberInfiniteTransition(label = "Content loading")
        val pulse by transition.animateFloat(.5f, 1f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Skeleton pulse")
        pulse
    }
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = opacity)
    if (compact) Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9).background(color, RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(13.dp).background(color, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(.65f).height(13.dp).background(color, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(.45f).height(10.dp).background(color, RoundedCornerShape(4.dp)))
        }
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(color, RoundedCornerShape(12.dp)))
        Box(Modifier.fillMaxWidth(.8f).height(14.dp).background(color, RoundedCornerShape(4.dp)))
        Box(Modifier.fillMaxWidth(.45f).height(11.dp).background(color, RoundedCornerShape(4.dp)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikariSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .72f).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close") }
            }
            content()
        }
    }
}

@Composable
fun HikariProgress(value: Float, modifier: Modifier = Modifier, trackColor: Color = MaterialTheme.colorScheme.outlineVariant) {
    val progress = value.coerceIn(0f, 1f)
    Box(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }.background(trackColor, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun SheetOption(title: String, subtitle: String? = null, icon: ImageVector? = null, selected: Boolean? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon?.let { Icon(it, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelLarge); subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
            if (selected != null) RadioButton(selected, onClick = null)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
