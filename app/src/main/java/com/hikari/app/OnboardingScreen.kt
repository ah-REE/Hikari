package com.hikari.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private const val onboardingPageCount = 5

@Composable
fun HikariOnboarding(
    onComplete: (skipSponsors: Boolean, backgroundPlayback: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences by PreferencesStore.get(context).state.collectAsStateWithLifecycle()
    val nativePreferences = remember(context) {
        context.getSharedPreferences("hikari-native", Context.MODE_PRIVATE)
    }
    var skipSponsors by rememberSaveable {
        mutableStateOf(nativePreferences.getBoolean("sponsorBlock", false))
    }
    var backgroundPlayback by rememberSaveable { mutableStateOf(preferences.backgroundPlayback) }
    val pager = rememberPagerState { onboardingPageCount }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    fun moveTo(page: Int) {
        scope.launch {
            if (reduceMotion) pager.scrollToPage(page)
            else pager.animateScrollToPage(page)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { moveTo(4) }

    fun finish() = onComplete(skipSponsors, backgroundPlayback)
    val page = pager.currentPage
    val action = when (page) {
        3 -> if (needsNotificationPermission) "Allow notifications" else "Next"
        onboardingPageCount - 1 -> "Start watching"
        else -> "Next"
    }

    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                HikariMark(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("hikari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            TextButton(onClick = ::finish) {
                Text("Skip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalPager(
            pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = false
        ) { current ->
            when (current) {
                0 -> WelcomePage()
                1 -> SponsorPage(skipSponsors) { skipSponsors = it }
                2 -> DownloadsPage()
                3 -> NotificationsPage()
                else -> BackgroundPlaybackPage(backgroundPlayback) { backgroundPlayback = it }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            OnboardingProgress(page)
            HikariButton(
                action,
                onClick = {
                    when (page) {
                        3 -> if (needsNotificationPermission) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else moveTo(4)
                        onboardingPageCount - 1 -> finish()
                        else -> moveTo(page + 1)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OnboardingPage(number: Int, label: String, title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "${number.toString().padStart(2, '0')}  —  05",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.displaySmall)
            Text(
                body,
                Modifier.padding(top = 12.dp).widthIn(max = 350.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    )
}

@Composable
private fun WelcomePage() = OnboardingPage(
    1,
    "WELCOME TO HIKARI",
    "The video\nis the point.",
    "Find what you want to watch. Save it for later."
) {
    Box(
        Modifier.fillMaxWidth().weight(1f).padding(top = 24.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(214.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) { HikariMark(Modifier.size(154.dp)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
    Text(
        "Search a topic, a creator, or paste a link.",
        Modifier.fillMaxWidth().padding(top = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SponsorPage(enabled: Boolean, onChange: (Boolean) -> Unit) = OnboardingPage(
    2,
    "SPONSOR SKIPPING",
    "Skip the sponsored parts.",
    "If someone has marked a sponsor, Hikari can skip it for you."
) {
    SponsorTimeline(enabled, Modifier.padding(top = 36.dp))
    Text(
        if (enabled) "It will be skipped." else "You can turn this on whenever you want.",
        Modifier.padding(top = 14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OnboardingSwitchRow(
        number = "01",
        title = "Skip sponsored parts",
        description = "Hikari will skip them when it can.",
        checked = enabled,
        onCheckedChange = onChange,
        modifier = Modifier.padding(top = 26.dp)
    )
    Text(
        "You can change this later in Settings.",
        Modifier.padding(start = 39.dp, top = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SponsorTimeline(enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("04:18", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text("12:48", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(55.dp)) {
            val center = size.height / 2f
            val stroke = 7.dp.toPx()
            drawLine(colors.outlineVariant, Offset(0f, center), Offset(size.width, center), stroke, StrokeCap.Round)
            drawLine(colors.primary, Offset(0f, center), Offset(size.width * .27f, center), stroke, StrokeCap.Round)
            val start = size.width * .43f
            val end = size.width * .69f
            drawLine(colors.primary.copy(alpha = if (enabled) .22f else 1f), Offset(start, center), Offset(end, center), stroke * 2.25f, StrokeCap.Square)
            drawLine(colors.primary, Offset(start, center - 15.dp.toPx()), Offset(start, center + 15.dp.toPx()), 1.dp.toPx())
            drawLine(colors.primary, Offset(end, center - 15.dp.toPx()), Offset(end, center + 15.dp.toPx()), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A sponsor was found", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(if (enabled) "Skipped" else "Leave on", style = MaterialTheme.typography.labelLarge, color = if (enabled) colors.primary else colors.onSurface)
        }
    }
}

@Composable
private fun DownloadsPage() = OnboardingPage(
    3,
    "OFFLINE",
    "Watch it\nlater.",
    "Download videos and enjoy them without the internet."
) {
    Column(Modifier.fillMaxWidth().padding(top = 34.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DOWNLOAD OPTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("READY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        DownloadChoice("Video", "720p")
        DownloadChoice("Audio", "English")
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saving for offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("77%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        HikariProgress(.77f, Modifier.fillMaxWidth().height(5.dp).padding(top = 9.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Find downloads in Library.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadChoice(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).border(
            width = .5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(0.dp)
        ).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✓", Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotificationsPage() = OnboardingPage(
    4,
    "NOTIFICATIONS",
    "Know when it’s ready.",
    "Hikari can let you know when a download is ready to watch."
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 38.dp).border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(16.dp)
        ).padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("HIKARI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Download ready", Modifier.padding(top = 28.dp), style = MaterialTheme.typography.titleLarge)
        Text(
            "This video is ready whenever you are.",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(Modifier.padding(top = 26.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Downloads", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("View", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    Text(
        "You can change this later in your phone's settings.",
        Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BackgroundPlaybackPage(enabled: Boolean, onChange: (Boolean) -> Unit) = OnboardingPage(
    5,
    "BACKGROUND PLAYBACK",
    "Keep\nlistening.",
    "Audio keeps playing after you leave Hikari."
) {
    AudioWaveform(Modifier.fillMaxWidth().height(182.dp).padding(top = 26.dp))
    Text(
        if (enabled) "Audio will keep playing." else "Turn this on when you want it.",
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OnboardingSwitchRow(
        number = "02",
        title = "Background playback",
        description = "Keep listening after you leave the app.",
        checked = enabled,
        onCheckedChange = onChange,
        modifier = Modifier.padding(top = 25.dp)
    )
    Text(
        "You can change this later in Settings.",
        Modifier.padding(start = 39.dp, top = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AudioWaveform(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val bars = remember { listOf(30f, 72f, 42f, 96f, 54f, 116f, 38f, 84f, 50f, 104f, 32f, 76f, 122f, 57f, 90f, 43f, 108f, 62f, 36f, 80f, 48f, 112f, 68f, 28f) }
    Canvas(modifier) {
        val center = size.height * .48f
        val gap = size.width / bars.size
        drawLine(colors.outlineVariant, Offset(0f, size.height * .9f), Offset(size.width, size.height * .9f), 1.dp.toPx())
        bars.forEachIndexed { index, height ->
            val x = gap * index + gap / 2f
            val alpha = if (index % 4 == 0) .46f else if (index % 3 == 0) .72f else 1f
            drawLine(
                colors.primary.copy(alpha = alpha),
                Offset(x, center - height.dp.toPx() / 2f),
                Offset(x, center + height.dp.toPx() / 2f),
                4.dp.toPx(),
                StrokeCap.Round
            )
        }
        drawCircle(colors.background, 6.dp.toPx(), Offset(size.width - 6.dp.toPx(), size.height * .9f))
        drawCircle(colors.primary, 5.dp.toPx(), Offset(size.width - 6.dp.toPx(), size.height * .9f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

@Composable
private fun OnboardingSwitchRow(
    number: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange
        ).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier.size(26.dp).border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
}

@Composable
private fun OnboardingProgress(page: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.End
    ) {
        repeat(onboardingPageCount) { index ->
            Box(
                Modifier.padding(start = if (index == 0) 0.dp else 4.dp).width(16.dp).height(2.dp)
                    .background(if (index <= page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}
