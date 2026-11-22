package com.hikari.app

import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay

import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    model: WatchViewModel,
    appModel: HikariViewModel,
    expanded: Boolean,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onChannel: (Video) -> Unit,
    modifier: Modifier = Modifier,
    expansionProgress: () -> Float = { if (expanded) 1f else 0f },
    fullHeight: Dp = 800.dp,
    transportEnabled: Boolean = true
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val state by model.state.collectAsStateWithLifecycle()
    val app by appModel.state.collectAsStateWithLifecycle()
    val player by model.controller.collectAsStateWithLifecycle()
    val library = remember { Library.get(context) }
    val lib by library.state.collectAsStateWithLifecycle()
    val pip by activity.pip.collectAsStateWithLifecycle()
    val preferences by PreferencesStore.get(context).state.collectAsStateWithLifecycle()
    val sponsorPrefs = remember { context.getSharedPreferences("hikari-native", 0) }
    val sponsorEnabled = rememberBooleanPreference(sponsorPrefs, "sponsorBlock")
    val sponsorAuto = rememberBooleanPreference(sponsorPrefs, "sponsorAutoSkip", true)
    val video = state.video ?: return
    var surfaceReady by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(video.id, expanded) {
        if (!surfaceReady) {
            // WebView creation can block the UI thread. Finish the entrance first;
            // reopening the mini-player keeps the already attached surface alive.
            if (expanded) snapshotFlow { expansionProgress() }.first { it >= .999f }
            surfaceReady = true
        }
    }
    var sponsorSegments by remember(video.id) { mutableStateOf(emptyList<SponsorSegment>()) }
    var sponsorError by remember(video.id) { mutableStateOf(false) }
    var sponsorRetry by remember(video.id) { mutableIntStateOf(0) }
    var dismissedSponsor by remember(video.id) { mutableStateOf<SponsorSegment?>(null) }
    LaunchedEffect(video.id, sponsorEnabled, sponsorRetry) {
        sponsorSegments = emptyList(); sponsorError = false
        if (sponsorEnabled) try {
            sponsorSegments = SponsorSegments.load(video.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            sponsorError = true
        }
    }
    val detailsScroll = rememberLazyListState()
    LaunchedEffect(video.id) { detailsScroll.scrollToItem(0) }
    var sheet by remember(video.id) { mutableStateOf<String?>(null) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var speed by remember(video.id) { mutableFloatStateOf(preferences.defaultSpeed) }
    var captions by remember(video.id) { mutableStateOf<String?>(null) }
    var embed by remember(video.id) { mutableStateOf<EmbedControls?>(null) }
    var overlay by remember(video.id) { mutableStateOf(true) }
    var youtubeUi by remember(video.id) { mutableStateOf(false) }
    val youtubeControls = state.engine == "embed" && (video.isShort || youtubeUi)
    var seeking by remember(video.id) { mutableStateOf(false) }
    var playing by remember(video.id) { mutableStateOf(false) }
    var buffering by remember(video.id) { mutableStateOf(false) }
    var position by remember(video.id) { mutableFloatStateOf(0f) }
    var videoRatio by remember(video.id) { mutableFloatStateOf(if (video.isShort) 9f / 16f else 16f / 9f) }
    var duration by remember(video.id) { mutableFloatStateOf(0f) }
    var lastHistoryWrite by remember(video.id) { mutableLongStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<Pair<Video, StreamChoice>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDownload?.let { (item, choice) ->
            Downloads.enqueue(context, item, choice); notice = "Download queued"
        }; pendingDownload = null
    }
    var ended by remember(video.id) { mutableStateOf(false) }
    var nextCancelled by remember(video.id) { mutableStateOf(false) }
    var countdown by remember(video.id) { mutableIntStateOf(5) }
    val recommendations = filterDiscovery(state.metadata?.related.orEmpty(), preferences, lib.history)
    val upNext = recommendations.firstOrNull { !it.isShort }
    var dislikes by remember(video.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(video.id, preferences.showDislikes) {
        dislikes = if (preferences.showDislikes) WatchMetadataRepository.dislikes(video.id) else null
    }
    LaunchedEffect(ended, preferences.focusMode, preferences.autoplayNext, pip, expanded, nextCancelled, upNext?.id) {
        if (ended && preferences.autoplayNext && !preferences.focusMode && !pip && expanded && !nextCancelled && upNext != null) {
            countdown = 5
            while (countdown > 0) {
                delay(1000); countdown--
            }
            model.open(upNext)
        }
    }
    val immersive = pip || fullScreen
    val motion = if (LocalReduceMotion.current) 0 else 180
    fun seek(seconds: Float) {
        val target = seconds.coerceIn(
            0f,
            duration.coerceAtLeast(0f)
        ); if (state.engine == "embed") embed?.seek(target) else player?.seekTo((target * 1000).toLong()); position =
            target
    }

    val sponsor = sponsorSegments.firstOrNull { it.contains(position) }
    LaunchedEffect(sponsor, sponsorEnabled, sponsorAuto, playing, state.engine) {
        if (sponsorEnabled && sponsorAuto && playing && state.engine == "embed" && sponsor != null) {
            seek(sponsor.end); notice = "Sponsor skipped"
        }
    }
    fun resumePlayback() {
        if (ended) nextCancelled = true; ended = false
        if (state.engine == "embed") {
            if (duration > 0 && position >= duration - .5f) embed?.seek(0f); embed?.play()
        } else player?.let { if (it.playbackState == androidx.media3.common.Player.STATE_ENDED) it.seekTo(0); it.play() }
    }

    fun pausePlayback() {
        if (state.engine == "embed") embed?.pause() else player?.pause()
    }

    fun togglePlay() {
        if (playing) pausePlayback() else resumePlayback(); overlay = true
    }

    fun native() {
        embed?.pause(); if (embed == null) model.fallbackToNative() else model.fallbackToNative((position * 1000).toLong())
    }

    fun minimize() {
        library.record(video, (position * 1000).toLong()); fullScreen = false; activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; onMinimize()
    }

    fun openSheet(name: String) {
        sheet = name; if (name in listOf("quality", "audio", "captions", "download")) model.loadDetails()
    }

    fun fullscreen() {
        fullScreen = !fullScreen; activity.requestedOrientation =
            if (fullScreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; overlay =
            true
    }
    LaunchedEffect(video.id, state.loading) { if (!state.loading && state.metadata == null) model.loadMetadata() }
    LaunchedEffect(player, state.engine, video.id, transportEnabled) {
        buffering = false
        if (state.engine == "native") while (true) {
            if (!transportEnabled && player?.isPlaying == true) player?.pause(); ended =
                player?.playbackState == androidx.media3.common.Player.STATE_ENDED; player?.videoSize?.let {
                if (it.width > 0 && it.height > 0) videoRatio = it.width * it.pixelWidthHeightRatio / it.height
            }; playing = player?.isPlaying == true; buffering =
                player?.playbackState == androidx.media3.common.Player.STATE_BUFFERING; position =
                (player?.currentPosition ?: 0L) / 1000f; duration =
                ((player?.duration ?: 0L).coerceAtLeast(0)) / 1000f; delay(350)
        }
    }
    LaunchedEffect(
        overlay,
        playing,
        expanded,
        sheet,
        seeking
    ) {
        if (overlay && playing && expanded && sheet == null && !seeking) {
            delay(3500); overlay = false
        }
    }
    LaunchedEffect(expanded, pip, state.engine) {
        if (expanded) overlay = true; if (!expanded || pip || state.engine != "embed") youtubeUi = false
    }
    LaunchedEffect(pip) { if (pip) sheet = null }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3500); notice = null
        }
    }
    DisposableEffect(fullScreen) {
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullScreen) {
            insets.hide(WindowInsetsCompat.Type.systemBars()); insets.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else insets.show(WindowInsetsCompat.Type.systemBars())
        onDispose { insets.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val pipCommand by rememberUpdatedState<(String) -> Unit> { command ->
        when (command) {
            "play" -> resumePlayback()
            "pause" -> pausePlayback()
            "back" -> seek(position - preferences.seekSeconds)
            "forward" -> seek(position + preferences.seekSeconds)
        }
    }
    LaunchedEffect(
        playing,
        embed,
        player,
        state.loading,
        state.engine,
        videoRatio
    ) {
        activity.updatePipPlayback(
            playing,
            !state.loading && (if (state.engine == "embed") embed != null else player != null),
            videoRatio
        )
    }
    LaunchedEffect(
        preferences.seekSeconds,
        preferences.backgroundPlayback,
        transportEnabled
    ) {
        activity.configurePip(transportEnabled)
        if (!transportEnabled || !preferences.backgroundPlayback && !PlaybackPolicy.playerVisible.value) pausePlayback()
    }
    LaunchedEffect(preferences.backgroundPlayback, state.engine, video.id) {
        if (preferences.backgroundPlayback && state.engine == "embed") {
            model.fallbackToNative((position * 1000).toLong())
        }
    }
    val playerHidden by rememberUpdatedState {
        if (state.engine == "embed") {
            if (preferences.backgroundPlayback) model.fallbackToNative((position * 1000).toLong())
            else embed?.pause()
        }
        if (position > 0) library.record(video, (position * 1000).toLong())
    }
    DisposableEffect(activity) {
        activity.onPipCommand = { pipCommand(it) }
        activity.onPlayerHidden = { playerHidden() }
        activity.configurePip(transportEnabled)
        onDispose {
            activity.onPipCommand = null; activity.onPlayerHidden =
            null; activity.configurePip(false); activity.window.attributes =
            activity.window.attributes.apply { screenBrightness = -1f }; activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    BackHandler(expanded && !pip) {
        when {
            sheet != null -> sheet = null; youtubeUi && !video.isShort -> youtubeUi =
            false; fullScreen -> fullscreen(); else -> minimize()
        }
    }
    val showMini by remember(expansionProgress) { derivedStateOf { expansionProgress() < .25f } }
    val showControls by remember(expansionProgress) { derivedStateOf { expansionProgress() > .85f } }
    val statusInset =
        if (immersive) 0.dp else with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset =
        if (immersive) 0.dp else with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    // Stable measurement avoids repeatedly resizing WebView/SurfaceView and the details list.
    // Progress is read in drawing/placement phases, not as a per-frame composition argument.
    BoxWithConstraints(
        modifier.background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
        val videoHeight = if (immersive) maxHeight else (maxWidth / videoRatio.coerceIn(
            .1f,
            10f
        )).coerceAtMost(fullHeight * if (videoRatio < 1f) .62f else .5f)
        if (!immersive) LazyColumn(
            Modifier.widthIn(max = 820.dp).fillMaxSize()
                .graphicsLayer { alpha = expansionProgress() * expansionProgress() }
                .then(if (!expanded) Modifier.clearAndSetSemantics { } else Modifier)
                .align(Alignment.TopCenter).padding(top = videoHeight + statusInset, bottom = bottomInset),
            state = detailsScroll,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp)) {
            state.error?.let {
                item {
                    HikariEmpty(
                        "Playback couldn't start",
                        "Check your connection and try again.",
                        { HikariButton("Try again", { model.open(video) }) },
                        Icons.Outlined.WifiOff
                    )
                }
            }
            item {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (video.views.isNotBlank()) Text(
                        video.views,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { sheet = "description" },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "…more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                state.metadata?.likeCount?.takeIf { it >= 0 }?.let {
                    Text(
                        "%,d likes".format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                dislikes?.let {
                    Text(
                        "%,d estimated dislikes".format(it),
                        Modifier.padding(bottom = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (video.channel.isNotBlank()) Row(
                    Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CreatorAvatar(video.channel, video.avatarUrl, 36.dp, video.channelUrl)
                    Column(
                        Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable(enabled = video.channelUrl.isNotBlank()) { onChannel(video) },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            video.channel,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (video.channelUrl.isNotBlank()) "View channel" else if (state.metadataLoading) "Loading channel…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { library.subscribe(video) },
                        enabled = video.channelUrl.isNotBlank(),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            if (lib.subscriptions.any { it.url == video.channelUrl }) "Following" else "Follow",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WatchAction(
                        if (lib.liked.any { it.id == video.id }) "Liked" else "Like",
                        Icons.Outlined.FavoriteBorder,
                        Modifier.weight(1f),
                        lib.liked.any { it.id == video.id }) { library.like(video) }
                    WatchAction(
                        if (app.saved.any { it.id == video.id }) "Saved" else "Save",
                        Icons.Outlined.BookmarkBorder,
                        Modifier.weight(1f),
                        app.saved.any { it.id == video.id }) { openSheet("save") }
                    WatchAction("Download", Icons.Outlined.FileDownload, Modifier.weight(1f)) { openSheet("download") }
                    WatchAction(
                        "Share",
                        Icons.Outlined.IosShare,
                        Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                        }, "Share video"))
                    }
                    if (state.engine == "embed" && !video.isShort) WatchAction(
                        if (youtubeUi) "Hide YouTube controls" else "Quality & audio",
                        Icons.Outlined.Tune,
                        Modifier.weight(1f),
                        youtubeUi
                    ) { youtubeUi = !youtubeUi; overlay = false }
                }
                if (youtubeUi && !video.isShort) Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tap the video settings for quality and audio.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = ::fullscreen) { Icon(Icons.Outlined.Fullscreen, "Fullscreen") }
                }
                Surface(
                    onClick = { sheet = "comments" },
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Comments",
                                style = MaterialTheme.typography.labelLarge
                            ); Text(
                            "Read the conversation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (sponsorEnabled && sponsorError) Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sponsor segments unavailable",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { sponsorRetry++ }) { Text("Retry") }
                }
                if (state.resolved?.choices?.firstOrNull()?.label == "Offline") TextButton(onClick = ::native) { Text("Stream online instead") }
            }
            if (!preferences.focusMode) {
                item {
                    HorizontalDivider(
                        Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                item {
                    SectionHeading(
                        if (state.metadata?.relatedFromSearch == true) "More on this topic" else "Related videos",
                        Modifier.padding(top = 8.dp)
                    )
                }
                if (ended && preferences.autoplayNext && !nextCancelled && upNext != null) item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Playing next in $countdown…",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(onClick = { nextCancelled = true }) { Text("Cancel") }
                    }
                }
                if (state.metadataLoading) items(3) { VideoSkeleton(compact = true) }
                else if (recommendations.isEmpty()) item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.metadataError) "Related videos couldn't load." else "No related videos available.",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = model::loadMetadata) { Text("Retry") }
                    }
                }
                items(recommendations.take(16), key = { it.id }) { next ->
                    CompactVideo(
                        next,
                        app.saved.any { it.id == next.id },
                        { model.open(next) },
                        { appModel.toggleSave(next) },
                        onChannel = { onChannel(next) })
                }
            }
        }
        if (showMini) {
            Row(
                Modifier.fillMaxWidth().height(60.dp)
                    .graphicsLayer { alpha = (1f - expansionProgress() * 4).coerceIn(0f, 1f) }
                    .padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(76.dp))
                Column(Modifier.weight(1f).clickable(onClick = onExpand).padding(vertical = 10.dp)) {
                    Text(
                        video.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        video.channel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = ::togglePlay) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) "Pause" else "Play"
                    )
                }
                IconButton(onClick = {
                    library.record(
                        video,
                        (position * 1000).toLong()
                    ); model.close()
                }) { Icon(Icons.Outlined.Close, "Close player") }
            }
            HorizontalDivider(Modifier.align(Alignment.TopCenter), color = MaterialTheme.colorScheme.outlineVariant)
            if (duration > 0) HikariProgress(
                position / duration,
                Modifier.fillMaxWidth().padding(top = 58.dp).height(2.dp).align(Alignment.TopCenter)
            )
        }
        val videoModifier = if (immersive) Modifier.fillMaxSize() else Modifier
            .size(width = maxWidth, height = videoHeight)
            .graphicsLayer {
                val progress = expansionProgress()
                val miniScale = minOf(66.dp.toPx() / size.width, 40.dp.toPx() / size.height)
                scaleX = miniScale + (1f - miniScale) * progress
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = (12.dp.toPx() + (66.dp.toPx() - size.width * miniScale) / 2) * (1f - progress)
                translationY =
                    (10.dp.toPx() + (40.dp.toPx() - size.height * miniScale) / 2) * (1f - progress) + statusInset.toPx() * progress
            }
        Box(
            videoModifier.background(Color.Black).onGloballyPositioned { coordinates ->
                val bounds =
                    coordinates.boundsInWindow(); if (!bounds.isEmpty && (expansionProgress() == 0f || expansionProgress() == 1f)) activity.configurePip(
                transportEnabled,
                android.graphics.Rect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )
            }) {
            if (surfaceReady && state.engine == "embed" && !state.loading) Box(
                Modifier.fillMaxSize().align(Alignment.Center)
            ) {
                EmbedPlayback(
                    video,
                    showYoutubeUi = (video.isShort || youtubeUi) && expanded && !pip,
                    onAspectRatio = { if (it.isFinite() && it > 0) videoRatio = it },
                    enhancedColors = preferences.enhancedColors,
                    onEnded = { ended = true },
                    onBuffering = { buffering = it },
                    onRestricted = ::native,
                    onError = model::embedFailed,
                    onMetadata = model::embedMetadata,
                    onControlsReady = { controls ->
                        val firstReady = embed == null; embed = controls; controls.speed(
                        speed
                        ); if (!transportEnabled || !PlaybackPolicy.playerVisible.value && !preferences.backgroundPlayback) controls.pause();
                        val resume = lib.history.firstOrNull { it.video.id == video.id }?.position
                            ?: 0L; if (firstReady && resume > 0) controls.seek(resume / 1000f)
                    },
                    onStatus = { active, current, total ->
                        if (active && !PlaybackPolicy.playerVisible.value && !preferences.backgroundPlayback) embed?.pause()
                        playing = active; position = current; duration = total
                        if (current > 0 && android.os.SystemClock.elapsedRealtime() - lastHistoryWrite > 10000) {
                            library.record(state.video ?: video, (current * 1000).toLong()); lastHistoryWrite =
                                android.os.SystemClock.elapsedRealtime()
                        }
                    })
            }
            else if (state.engine == "native") AndroidView(factory = {
                PlayerView(it).apply {
                    useController = false; setBackgroundColor(android.graphics.Color.BLACK); importantForAccessibility =
                    android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            }, update = { it.player = player }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
            if (!expanded) Box(
                Modifier.fillMaxSize().clickable(onClick = onExpand).semantics { contentDescription = "Expand player" })
            else if (!pip && !youtubeControls && showControls) {
                Box(
                    Modifier.fillMaxSize().semantics {
                        contentDescription = "Video player"; onClick("Show playback controls") {
                        overlay = true; true
                    }
                    }
                        .pointerInput(
                            video.id,
                            state.engine,
                            preferences.seekSeconds
                        ) {
                            detectTapGestures(
                                onTap = { overlay = !overlay },
                                onDoubleTap = { point ->
                                    seek(position + if (point.x < size.width / 2) -preferences.seekSeconds else preferences.seekSeconds); overlay =
                                    true
                                })
                        }
                        .pointerInput(overlay) {
                            if (!overlay) detectVerticalDragGestures { change, amount ->
                                change.consume()
                                if (change.position.x < size.width / 2) {
                                    val attributes = activity.window.attributes
                                    attributes.screenBrightness =
                                        ((if (attributes.screenBrightness < 0) .5f else attributes.screenBrightness) - amount / size.height).coerceIn(
                                            .05f,
                                            1f
                                        )
                                    activity.window.attributes = attributes
                                } else {
                                    val audio =
                                        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                                    val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val current = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                    if (kotlin.math.abs(amount) > 4) audio.setStreamVolume(
                                        android.media.AudioManager.STREAM_MUSIC,
                                        (current + if (amount < 0) 1 else -1).coerceIn(0, max),
                                        0
                                    )
                                }
                            }
                        })
                AnimatedVisibility(
                    visible = overlay || !playing || seeking,
                    enter = fadeIn(tween(motion)),
                    exit = fadeOut(tween(motion))
                ) {
                    PlayerControls(
                        playing,
                        position,
                        duration,
                        state.loading || buffering || (state.engine == "embed" && embed == null),
                        fullScreen,
                        ::togglePlay,
                        ::seek,
                        ::minimize,
                        { openSheet("playback") },
                        ::fullscreen,
                        { seeking = it },
                        if (state.engine == "native") (player?.bufferedPosition ?: 0L) / 1000f else position,
                        preferences.seekSeconds,
                        sponsorSegments
                    )
                }
                if (state.embedError != null) Surface(
                    Modifier.align(Alignment.Center).padding(20.dp),
                    color = Color.Black.copy(alpha = .85f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "This video couldn't start in the YouTube player.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        ); TextButton(onClick = ::native) { Text("Try native playback") }
                    }
                }
            }
            AnimatedVisibility(
                visible = sponsor != null && sponsor != dismissedSponsor && sponsorEnabled && !pip && expanded && videoHeight >= 96.dp,
                modifier = Modifier.align(AbsoluteAlignment.CenterRight),
                enter = fadeIn(tween(motion)) + slideInHorizontally(tween(motion)) { it / 8 },
                exit = fadeOut(tween(motion)) + slideOutHorizontally(tween(motion)) { it / 8 }
            ) {
                SponsorSkipPrompt(
                    onSkip = { sponsor?.let { seek(it.end) } },
                    onDismiss = { sponsor?.let { dismissedSponsor = it } }
                )
            }
        }
        if (youtubeControls && fullScreen && !pip) Row(
            Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = .75f), CircleShape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!video.isShort) IconButton(onClick = { youtubeUi = false; overlay = true }) {
                Icon(
                    Icons.Outlined.Close,
                    "Hide YouTube controls",
                    tint = Color.White
                )
            }
            IconButton(onClick = ::fullscreen) {
                Icon(
                    Icons.Outlined.FullscreenExit,
                    "Exit fullscreen",
                    tint = Color.White
                )
            }
        }
        notice?.let {
            if (expanded && !immersive) Snackbar(
                Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) { Text(it) }
        }
    }
    if (sheet == "comments") CommentsSheet(video) { sheet = null }
    if (sheet == "playlist") PlaylistPicker(video) { sheet = null }
    if (sheet == "download") DownloadOptions(
        state.resolved,
        state.detailsLoading || state.loading,
        state.detailsError ?: state.error,
        model::loadDetails,
        { choice ->
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pendingDownload = video to choice; permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Downloads.enqueue(context, video, choice); notice = "Download queued"
            }
            sheet = null
        },
        { sheet = null })
    if (sheet != null && sheet !in listOf("comments", "playlist", "download")) HikariSheet(
        when (sheet) {
            "playback" -> "Playback"; "quality" -> "Video quality"; "speed" -> "Playback speed"; "audio" -> "Audio track"; "captions" -> "Captions"; "save" -> "Save video"; "download" -> "Save offline"; else -> "About this video"
        }, { sheet = null }) {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
            when (sheet) {
                "playback" -> {
                    if (state.engine == "embed" && !video.isShort) SheetOption(
                        "YouTube controls",
                        "Quality, audio & captions",
                        Icons.Outlined.Tune
                    ) { youtubeUi = true; overlay = false; sheet = null }
                    if (state.engine == "native") SheetOption(
                        "Quality",
                        state.resolved?.choices?.getOrNull(state.choice)?.label ?: "Automatic",
                        Icons.Outlined.Tune
                    ) { openSheet("quality") }
                    SheetOption("Playback speed", "${speed}×", Icons.Outlined.Speed) { sheet = "speed" }
                    SheetOption(
                        "Audio track",
                        "Choose an available language",
                        Icons.Outlined.Headphones
                    ) { openSheet("audio") }
                    SheetOption(
                        "Captions",
                        state.resolved?.captions?.firstOrNull { it.url == captions }?.name ?: "Off",
                        Icons.Outlined.ClosedCaption
                    ) { openSheet("captions") }
                    SheetOption(
                        "Picture in picture",
                        "Keep watching outside Hikari",
                        Icons.Outlined.PictureInPictureAlt
                    ) {
                        sheet = null
                        if (activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) activity.enterPlayerPip() else notice =
                            "Picture in picture is unavailable on this device"
                    }
                    if (state.engine == "embed") SheetOption(
                        "Use native playback",
                        "Switch this video to the native player",
                        Icons.Outlined.PlayCircleOutline
                    ) { native(); sheet = null }
                }

                "speed" -> listOf(
                    .5f,
                    .75f,
                    1f,
                    1.25f,
                    1.5f,
                    1.75f,
                    2f
                ).forEach { value ->
                    SheetOption(
                        if (value == 1f) "Normal" else "${value}×",
                        selected = speed == value
                    ) {
                        speed = value; if (state.engine == "embed") embed?.speed(value) else player?.setPlaybackSpeed(
                        value
                    ); sheet = null
                    }
                }

                "save" -> {
                    SheetOption(
                        if (app.saved.any { it.id == video.id }) "Remove from Watch later" else "Save to Watch later",
                        icon = Icons.Outlined.BookmarkBorder
                    ) { appModel.toggleSave(video); sheet = null }
                    SheetOption("Add to playlist", icon = Icons.AutoMirrored.Outlined.PlaylistAdd) {
                        sheet = "playlist"
                    }
                }

                else -> {
                    if (state.detailsLoading || state.loading) Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { HikariLoader("Loading available options") }
                    if (state.detailsError != null || (state.error != null && state.resolved == null)) {
                        Text(
                            state.detailsError ?: "Options are unavailable.",
                            style = MaterialTheme.typography.bodyMedium
                        ); TextButton(onClick = { if (state.engine == "embed") model.loadDetails() else model.fallbackToNative() }) {
                            Text(
                                "Try again"
                            )
                        }
                    }
                    when (sheet) {
                        "description" -> {
                            Text(
                                video.title,
                                Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.titleLarge
                            ); (state.metadata?.description ?: state.resolved?.description ?: "").let {
                                Text(
                                    HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                                        .ifBlank { "No description provided." },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        "quality" -> if (state.engine == "native") {
                            state.resolved?.choices?.forEachIndexed { index, choice ->
                                SheetOption(
                                    choice.label,
                                    selected = state.choice == index
                                ) { model.select(index, (position * 1000).toLong()); sheet = null }
                            }
                        }

                        "audio" -> {
                            if (state.engine == "embed") Text(
                                "Choosing an audio track uses native playback.",
                                Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                            state.resolved?.audioTracks?.forEach { track ->
                                SheetOption(
                                    track.label,
                                    selected = track.id == state.audioTrackId || (state.audioTrackId == null && track.original)
                                ) {
                                    embed?.pause(); model.selectAudio(track.id, (position * 1000).toLong()); sheet =
                                    null
                                }
                            }
                            if (state.resolved?.audioTracks?.isEmpty() == true) Text(
                                "No alternate audio tracks available.",
                                Modifier.padding(vertical = 16.dp)
                            )
                        }

                        "captions" -> {
                            SheetOption(
                                "Off",
                                selected = captions == null
                            ) {
                                if (state.engine == "native") model.selectCaption(
                                    null,
                                    (position * 1000).toLong()
                                ); captions = null; sheet = null
                            }
                            state.resolved?.captions?.forEach { caption ->
                                SheetOption(
                                    caption.name,
                                    selected = captions == caption.url
                                ) {
                                    embed?.pause(); model.selectCaption(caption, (position * 1000).toLong()); captions =
                                    caption.url; sheet = null
                                }
                            }
                            if (state.resolved?.captions?.isEmpty() == true) Text(
                                "No captions available for this video.",
                                Modifier.padding(vertical = 16.dp)
                            )
                            if (state.engine == "embed") Text(
                                "Selecting captions uses native playback.",
                                Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    playing: Boolean,
    current: Float,
    duration: Float,
    loading: Boolean,
    fullscreen: Boolean,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onMinimize: () -> Unit,
    onSettings: () -> Unit,
    onFullscreen: () -> Unit,
    onSeeking: (Boolean) -> Unit,
    buffered: Float,
    seekSeconds: Int,
    segments: List<SponsorSegment>
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = .5f),
                    Color.Transparent,
                    Color.Black.copy(alpha = .8f)
                )
            )
        )
    ) {
        val compact = maxHeight < 170.dp
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    "Minimize player",
                    tint = Color.White
                )
            }
            if (compact) IconButton(onClick = onToggle) {
                Icon(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (playing) "Pause" else "Play",
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Playback settings", tint = Color.White) }
            IconButton(onClick = onFullscreen) {
                Icon(
                    if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    if (fullscreen) "Exit fullscreen" else "Fullscreen",
                    tint = Color.White
                )
            }
        }
        if (!compact) Row(
            Modifier.align(Alignment.Center).offset(y = if (LocalDensity.current.fontScale > 1.3f) (-16).dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            SeekControl(seekSeconds, false) { onSeek(current - seekSeconds) }
            FilledIconButton(
                onClick = onToggle,
                enabled = !loading,
                modifier = Modifier.size(54.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = .18f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent
                )
            ) {
                if (loading) HikariLoader(modifier = Modifier.size(44.dp)) else Icon(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (playing) "Pause" else "Play",
                    Modifier.size(30.dp)
                )
            }
            SeekControl(seekSeconds, true) { onSeek(current + seekSeconds) }
        }
        PlaybackTimeline(
            current,
            duration,
            buffered,
            onSeek,
            onSeeking,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = if (compact) 0.dp else 8.dp),
            segments,
            compact
        )
    }
}

@Composable
private fun SeekControl(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = "${if (forward) "Forward" else "Rewind"} $seconds seconds"
        }) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Replay,
                null,
                Modifier.fillMaxSize().then(if (forward) Modifier.graphicsLayer { scaleX = -1f } else Modifier),
                tint = Color.White)
            Text(
                seconds.toString(),
                Modifier.offset(y = 2.dp).clearAndSetSemantics { },
                color = Color.White,
                fontSize = 8.sp,
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
private fun WatchAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.height(48.dp).semantics { contentDescription = label; this.selected = selected },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(icon, null, Modifier.size(21.dp))
    }
}

@Composable
fun PlaylistPicker(video: Video?, onDismiss: () -> Unit) {
    val library = Library.get(LocalContext.current)
    val lib by library.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    HikariSheet(if (video == null) "New playlist" else "Add to playlist", onDismiss) {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()).imePadding()) {
            if (video != null) lib.playlists.forEach { playlist ->
                SheetOption(
                    playlist.name,
                    videoCount(playlist.videos.size),
                    Icons.AutoMirrored.Outlined.PlaylistPlay
                ) { library.addToPlaylist(playlist.id, video); onDismiss() }
            }
            OutlinedTextField(
                name,
                { name = it.take(100) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("Playlist name") },
                shape = RoundedCornerShape(10.dp)
            )
            HikariButton("Create playlist", {
                library.createPlaylist(name)
                if (video != null) library.state.value.playlists.lastOrNull()
                    ?.let { library.addToPlaylist(it.id, video) }
                onDismiss()
            }, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = name.isNotBlank())
        }
    }
}
