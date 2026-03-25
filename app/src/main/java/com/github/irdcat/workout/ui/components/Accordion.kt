package com.github.irdcat.workout.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.colors
import com.composables.composetheme.gray100
import com.composables.composetheme.gray800
import com.composables.composetheme.roundL
import com.composables.composetheme.shapes
import com.github.irdcat.workout.ui.components.icons.ChevronRight

@Composable
fun Accordion(title: @Composable () -> Unit, expanded: Boolean = false, onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    val degrees by animateFloatAsState(if (expanded) -90f else 90f)

    Column {
        Row(modifier = Modifier
            .clip(ComposeTheme.shapes.roundL)
            .clickable {
                onClick.invoke()
            }
            .fillMaxWidth()
            .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                title.invoke()
            }
            Image(
                ChevronRight,
                contentDescription = null,
                modifier = Modifier.rotate(degrees),
                colorFilter = ColorFilter.tint(ComposeTheme.colors.gray800)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                spring(
                    stiffness = Spring.StiffnessLow,
                    visibilityThreshold = IntSize.VisibilityThreshold)
            ),
            exit = shrinkVertically()
        ) {
            Box(Modifier
                .fillMaxWidth()
                .padding(16.dp)) {
                content()
            }
        }
        Separator(1.dp, ComposeTheme.colors.gray100)
    }
}