package com.hikari.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun HikariList(modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), contentPadding: PaddingValues = PaddingValues(0.dp), verticalArrangement: Arrangement.Vertical = Arrangement.Top, content: LazyListScope.() -> Unit) {
    val prefs by PreferencesStore.get(LocalContext.current).state.collectAsStateWithLifecycle()
    val show by remember { derivedStateOf { state.firstVisibleItemIndex > 4 } }
    val scope = rememberCoroutineScope()
    val reduced = LocalReduceMotion.current
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = contentPadding, verticalArrangement = verticalArrangement, content = content)
        AnimatedVisibility(show && prefs.scrollToTop, Modifier.align(Alignment.BottomEnd).padding(16.dp), enter = fadeIn(androidx.compose.animation.core.tween(if (reduced) 0 else 180)), exit = fadeOut(androidx.compose.animation.core.tween(if (reduced) 0 else 140))) {
            SmallFloatingActionButton(onClick = { scope.launch { if (reduced) state.scrollToItem(0) else state.animateScrollToItem(0) } }, containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface) { Icon(Icons.Outlined.VerticalAlignTop, "Back to top") }
        }
    }
}
