package com.hikari.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.compose.*

/** Bundled artwork; never waits for a network request. Decorative, so excluded from TalkBack. */
@Composable
fun EmptyIllustration(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.collection_empty))
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val reduced = LocalReduceMotion.current
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever,
        isPlaying = !reduced && lifecycle.isAtLeast(Lifecycle.State.STARTED))
    LottieAnimation(composition, progress = { if (reduced) .5f else progress },
        modifier = modifier.width(256.dp).height(160.dp).clearAndSetSemantics { })
}
