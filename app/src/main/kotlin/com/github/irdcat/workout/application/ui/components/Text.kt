package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles

/** Small all-caps caption above a section, card or field. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyles.labelCaps,
        color = Colors.TextSecondary,
    )
}

/** Trailing arrow on a row that opens another screen. */
@Composable
fun Chevron(size: Dp = 24.dp) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = Colors.TextSecondary,
        modifier = Modifier.size(size),
    )
}
