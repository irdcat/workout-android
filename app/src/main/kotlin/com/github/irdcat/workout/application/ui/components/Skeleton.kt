package com.github.irdcat.workout.application.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.Shapes

/** How far the shimmer has swept, 0 to 1; shared so every block in a skeleton moves together. */
private val LocalShimmer = staticCompositionLocalOf<State<Float>?> { null }

/**
 * A placeholder layout shown while a screen's data loads. Its [SkeletonBlock]s shimmer in step;
 * to accessibility services the whole of it is one "Loading" element.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shimmer = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    CompositionLocalProvider(LocalShimmer provides shimmer) {
        Box(modifier = modifier.clearAndSetSemantics { contentDescription = "Loading" }) {
            content()
        }
    }
}

/** A grey shape standing in for text or an element, with a highlight sweeping across it. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = Shapes.md) {
    val shimmer = LocalShimmer.current
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(Colors.Surface2)
                // Read during drawing only, so the sweep redraws without recomposing.
                val progress = shimmer?.value ?: return@drawBehind
                val band = size.width * 0.6f
                val start = -band + progress * (size.width + band * 2)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Colors.Surface3, Color.Transparent),
                        start = Offset(start - band, 0f),
                        end = Offset(start + band, size.height),
                    ),
                )
            },
    )
}
