package com.hikari.app

import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi

private val navigationSaver = Saver<HikariNavigation, List<String>>(
    save = { it.encode() },
    restore = { HikariNavigation.decode(it) })

@UnstableApi
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HikariApp(model: HikariViewModel = viewModel()) {
    val watch: WatchViewModel = viewModel()
    val app by model.state.collectAsStateWithLifecycle()
    val watchState by watch.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val library = Library.get(context)
    val lib by library.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as MainActivity
    val systemDark = isSystemInDarkTheme()
    val dark = when (app.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val pip by activity.pip.collectAsStateWithLifecycle()
    val incoming by activity.pendingVideo.collectAsStateWithLifecycle()
    var showOnboarding by rememberSaveable { mutableStateOf(OnboardingStore.shouldShow(context)) }
    var navigation by rememberSaveable(stateSaver = navigationSaver) { mutableStateOf(HikariNavigation()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val screenState = rememberSaveableStateHolder()
    val previousChildKeys = remember(screenState) { mutableSetOf<String>() }
    val childKeys = navigation.children.map { it.key }.toSet()
    SideEffect {
        // Active outgoing providers are marked not to save when their animation disposes them.
        (previousChildKeys - childKeys).forEach { screenState.removeState(it) }
        previousChildKeys.clear()
        previousChildKeys.addAll(childKeys)
    }
    val route = navigation.current
    val roots = remember { listOf("home", "following", "library") }
    val pager = rememberPagerState(initialPage = roots.indexOf(navigation.root).coerceAtLeast(0)) { roots.size }
    var requestedPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(navigation.root) {
        val page = roots.indexOf(navigation.root).coerceAtLeast(0)
        if (pager.settledPage != page) {
            requestedPage = page
            try {
                if (app.reduceMotion) pager.scrollToPage(page)
                else pager.animateScrollToPage(page, animationSpec = tween(320, easing = FastOutSlowInEasing))
            } finally {
                requestedPage = null
            }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { if (pager.isScrollInProgress) null else pager.settledPage }.collect { page ->
            if (page != null && requestedPage == null && navigation.children.isEmpty()) navigation =
                navigation.selectRoot(roots[page])
        }
    }
    val play: (Video) -> Unit = { watch.open(it); expanded = true }
    val open: (HikariRoute) -> Unit = { navigation = navigation.open(it) }
    val back: () -> Unit = { navigation = navigation.back() }
    val search: () -> Unit = { model.clearSearch(); open(HikariRoute("search")) }
    val channel: (Video) -> Unit =
        { if (it.channelUrl.isNotBlank()) open(HikariRoute("channel", it.channelUrl, it.channel)) }
    LaunchedEffect(incoming, showOnboarding) {
        if (!showOnboarding) incoming?.let { play(it); activity.pendingVideo.value = null }
    }
    LaunchedEffect(watchState.video?.id) { if (watch.state.value.video == null) expanded = false }
    LaunchedEffect(lib.subscriptions, showOnboarding) {
        if (!showOnboarding) model.refreshFeed(lib.subscriptions)
    }
    var appliedThemeMode by remember { mutableStateOf(app.themeMode) }
    LaunchedEffect(app.themeMode) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val nightMode = when (app.themeMode) {
                "dark" -> android.app.UiModeManager.MODE_NIGHT_YES
                "light" -> android.app.UiModeManager.MODE_NIGHT_NO
                // AUTO maps to an undefined app-local night override, so the device setting applies.
                else -> android.app.UiModeManager.MODE_NIGHT_AUTO
            }
            activity.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(nightMode)
        } else if (appliedThemeMode != app.themeMode) {
            activity.recreate()
        }
        appliedThemeMode = app.themeMode
    }
    DisposableEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(
            AndroidColor.TRANSPARENT,
            AndroidColor.TRANSPARENT
        )
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    BackHandler(!expanded && !pip && navigation.canGoBack, onBack = back)
    MaterialTheme(
        colorScheme = animatedHikariColors(dark, app.reduceMotion), typography = HikariDesign.typography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(22.dp)
        )
    ) {
        CompositionLocalProvider(
            LocalReduceMotion provides app.reduceMotion,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            if (showOnboarding) {
                HikariOnboarding(onComplete = { skipSponsors, backgroundPlayback ->
                    context.getSharedPreferences("hikari-native", 0).edit()
                        .putBoolean("sponsorBlock", skipSponsors).apply()
                    val preferences = PreferencesStore.get(context)
                    if (preferences.state.value.backgroundPlayback != backgroundPlayback) {
                        preferences.update(preferences.state.value.copy(backgroundPlayback = backgroundPlayback))
                    }
                    OnboardingStore.complete(context)
                    showOnboarding = false
                })
            } else {
            val colors = MaterialTheme.colorScheme
            val keyboardVisible = WindowInsets.isImeVisible
            val child = route.takeIf { it.page !in roots }
            val showNavigation = child == null && !keyboardVisible
            val navigationHeight = if (LocalDensity.current.fontScale > 1.3f) 60.dp else 54.dp
            val playerPresent = watchState.video != null
            val barInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
            val expansion = animateFloatAsState(
                if (expanded || pip) 1f else 0f,
                tween(if (app.reduceMotion || pip) 0 else 300, easing = FastOutSlowInEasing),
                label = "Player expansion"
            )
            BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
                val fullHeight = maxHeight
                // Root and detail pages own their insets. A new toolbar never remeasures Library.
                Scaffold(
                    modifier = Modifier.fillMaxSize().statusBarsPadding()
                        .then(if (expanded || pip || child != null) Modifier.clearAndSetSemantics { } else Modifier),
                    contentWindowInsets = WindowInsets(0), containerColor = colors.background,
                    bottomBar = {
                        Column(Modifier.navigationBarsPadding()) {
                            if (playerPresent) Spacer(Modifier.height(60.dp))
                            HikariNavigationBar(
                                navigation.root,
                                pager.currentPage + pager.currentPageOffsetFraction,
                                navigationHeight
                            ) { navigation = navigation.selectRoot(it) }
                        }
                    }) { padding ->
                    HorizontalPager(
                        pager,
                        modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                        userScrollEnabled = child == null && !expanded && !pip,
                        beyondViewportPageCount = 1,
                        key = { roots[it] }) { index ->
                        screenState.SaveableStateProvider(roots[index]) {
                            Column(Modifier.fillMaxSize()) {
                                when (roots[index]) {
                                    "home" -> Row(
                                        Modifier.fillMaxWidth().height(62.dp).padding(start = 20.dp, end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        HikariMark(); Spacer(Modifier.width(8.dp)); Text(
                                        "hikari",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-1).sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                        IconButton(onClick = search) { Icon(Icons.Outlined.Search, "Search videos") }
                                        IconButton(onClick = {
                                            open(
                                                HikariRoute(
                                                    "collection",
                                                    "history",
                                                    "History"
                                                )
                                            )
                                        }) { Icon(Icons.Outlined.History, "Watch history") }
                                    }

                                    "following" -> HikariTopBar("Following") {
                                        IconButton(onClick = search) {
                                            Icon(
                                                Icons.Outlined.Search,
                                                "Search videos"
                                            )
                                        }
                                    }

                                    "library" -> HikariTopBar("Library") {
                                        IconButton(onClick = { open(HikariRoute("settings")) }) {
                                            Icon(
                                                Icons.Outlined.Settings,
                                                "Settings"
                                            )
                                        }
                                    }
                                }
                                when (roots[index]) {
                                    "home" -> HomeScreen(model, play, search, channel)
                                    "following" -> SubscriptionsScreen(model, play, channel, search)
                                    "library" -> LibraryScreen(model, play, open, search)
                                }
                            }
                        }
                    }
                }
                AnimatedContent(
                    targetState = child to navigation.children.size,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it.first?.key ?: "root" },
                    transitionSpec = {
                        val duration = if (app.reduceMotion) 0 else 300
                        val forward = targetState.second > initialState.second
                        ((fadeIn(tween(duration)) + slideInHorizontally(
                            tween(
                                duration,
                                easing = FastOutSlowInEasing
                            )
                        ) { if (app.reduceMotion) 0 else if (forward) it / 9 else -it / 16 }) togetherWith
                                (fadeOut(tween(if (app.reduceMotion) 0 else 180)) + slideOutHorizontally(
                                    tween(
                                        duration,
                                        easing = FastOutSlowInEasing
                                    )
                                ) { if (app.reduceMotion) 0 else if (forward) -it / 16 else it / 9 })).using(null)
                    },
                    label = "Detail transition"
                ) { (shownRoute, _) ->
                    if (shownRoute != null) screenState.SaveableStateProvider(shownRoute.key) {
                        Column(
                            Modifier.fillMaxSize().background(colors.background).statusBarsPadding()
                                .navigationBarsPadding().imePadding()
                                .then(if (expanded || pip) Modifier.clearAndSetSemantics { } else Modifier)) {
                            if (shownRoute.page != "search") HikariTopBar(
                                when (shownRoute.page) {
                                    "downloads" -> "Downloads"; "settings" -> "Settings"; else -> shownRoute.title
                                }, onBack = back
                            )
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Box(Modifier.widthIn(max = 820.dp).fillMaxSize()) {
                                    when (shownRoute.page) {
                                        "search" -> SearchScreen(model, play, back, channel)
                                        "collection" -> CollectionScreen(shownRoute.id, model, play, search)
                                        "playlist" -> PlaylistScreen(shownRoute.id, model, play, search, back)
                                        "downloads" -> DownloadsScreen(play, search)
                                        "settings" -> SettingsScreen(model)
                                        "channel" -> ChannelScreen(
                                            shownRoute.id,
                                            shownRoute.title,
                                            app.saved,
                                            play,
                                            model::toggleSave,
                                            onPlaylist = { url, title ->
                                                open(
                                                    HikariRoute(
                                                        "remote_playlist",
                                                        url,
                                                        title
                                                    )
                                                )
                                            })

                                        "remote_playlist" -> RemotePlaylistScreen(
                                            shownRoute.id,
                                            app.saved,
                                            play,
                                            model::toggleSave
                                        )
                                    }
                                }
                            }
                            if (playerPresent && !keyboardVisible) Spacer(Modifier.height(60.dp))
                        }
                    }
                }
                if (playerPresent) {
                    val visible = !keyboardVisible || expanded || pip
                    val bottomInset = barInset + if (showNavigation) navigationHeight else 0.dp
                    // Only this lightweight host changes its bounds. The watch content and the
                    // Android video surface keep full-size constraints throughout the transition.
                    WatchScreen(
                        watch,
                        model,
                        expanded = expanded || pip,
                        expansionProgress = { expansion.value },
                        fullHeight = fullHeight,
                        onMinimize = { expanded = false },
                        onExpand = { expanded = true },
                        onChannel = { video -> expanded = false; channel(video) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .offset { IntOffset(0, -(bottomInset.toPx() * (1f - expansion.value)).roundToInt()) }
                            .clipToBounds().layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(minHeight = constraints.maxHeight))
                                val height =
                                    if (visible) (60.dp.toPx() + (constraints.maxHeight - 60.dp.toPx()) * expansion.value).roundToInt() else 0
                                layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                            }.fillMaxSize()
                            .then(if (!visible) Modifier.clearAndSetSemantics { } else Modifier))
                }
            }
            }
        }
    }
}

@Composable
private fun HikariNavigationBar(
    selectedRoot: String,
    pagePosition: Float,
    height: androidx.compose.ui.unit.Dp,
    onSelect: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val reduced = LocalReduceMotion.current
    Column(Modifier.background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .65f), thickness = .5.dp)
            Canvas(Modifier.fillMaxSize()) {
                val center = size.width / 3 * (pagePosition.coerceIn(0f, 2f) + .5f)
                drawLine(
                    colors.primary,
                    Offset(center - 10.dp.toPx(), 1.dp.toPx()),
                    Offset(center + 10.dp.toPx(), 1.dp.toPx()),
                    2.dp.toPx(),
                    StrokeCap.Round
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(height - 2.dp).semantics { isTraversalGroup = true }) {
            listOf(
                Triple("home", "Home", Icons.Outlined.Home),
                Triple("following", "Following", Icons.Outlined.Subscriptions),
                Triple("library", "Library", Icons.Outlined.VideoLibrary)
            ).forEach { (route, label, icon) ->
                val active = route == selectedRoot
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val focused by interaction.collectIsFocusedAsState()
                val scale by animateFloatAsState(
                    if (pressed) .9f else 1f,
                    tween(if (reduced) 0 else 140),
                    label = "Tab press"
                )
                val tint by animateColorAsState(
                    if (active) colors.primary else colors.onSurfaceVariant,
                    tween(if (reduced) 0 else 180),
                    label = "Tab selection"
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab
                    ) { onSelect(route) }.semantics { selected = active },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(26.dp).background(
                            if (focused) colors.secondaryContainer else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        ), contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            null,
                            Modifier.size(21.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                            tint = tint
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) colors.onSurface else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}
