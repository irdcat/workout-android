package com.github.irdcat.workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.colors
import com.composables.composetheme.gray300

@Composable
fun Separator(
    height: Dp = 1.dp,
    color: Color = ComposeTheme.colors.gray300) {
    Box(Modifier.fillMaxWidth().height(height).background(color))
}