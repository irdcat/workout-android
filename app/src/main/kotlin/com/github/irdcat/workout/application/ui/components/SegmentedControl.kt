package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes

/** One of a few [options] side by side, the [selected] one filled; [description] names the whole control. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Surface1, Shapes.xl)
            .border(1.dp, Colors.Border, Shapes.xl)
            .padding(4.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                selected = isSelected,
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = Shapes.lg,
                color = if (isSelected) Colors.Primary else Colors.Surface1,
                contentColor = if (isSelected) Colors.OnPrimary else Colors.TextSecondary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = label(option), style = KineticType.bodyMd.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}
