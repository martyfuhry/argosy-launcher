package com.nendo.argosy.ui.components.launch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The veil's current opacity, 0 clear to 1 black, for tests that step the clock frame by frame.
 */
val VeilAlpha = SemanticsPropertyKey<Float>("VeilAlpha")

private var SemanticsPropertyReceiver.veilAlpha by VeilAlpha

/**
 * A full-size black layer that fades in over [ComponentDefaults.LaunchOverlay.veilMs] while
 * [veiled] holds and out again when it drops, at an even rate per frame. Reduced motion cuts at
 * once. It takes no input and draws nothing while clear.
 */
@Composable
fun LaunchVeil(veiled: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val target = if (veiled) 1f else 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (reduced) 0 else ComponentDefaults.LaunchOverlay.veilMs,
            easing = LinearEasing
        ),
        label = "launchVeil"
    )
    val alpha = if (reduced) target else animated
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { veilAlpha = alpha }
            .drawBehind { if (alpha > 0f) drawRect(Color.Black, alpha = alpha) }
    )
}
