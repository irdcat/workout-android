package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.Shapes

val CardBorder = BorderStroke(1.dp, Colors.Border)

/** The outlined surface every screen groups its content in; clickable when given [onClick]. */
@Composable
fun Card(
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: (() -> Unit)? = null,
    border: BorderStroke = CardBorder,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = Shapes.xl,
            color = Colors.Surface1,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = Shapes.xl,
            color = Colors.Surface1,
            border = border,
            content = content,
        )
    }
}
