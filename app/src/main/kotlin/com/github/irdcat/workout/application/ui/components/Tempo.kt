package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes

@Immutable
data class Tempo(
    /** e.g. "3010". */
    val digits: String,
    /** e.g. "last group"; null when it applies to every set. */
    val appliesTo: String? = null,
)

@Composable
fun TempoBadge(tempo: Tempo, background: Color = Colors.Surface2) {
    val description = buildString {
        append("Tempo ${tempo.digits.toList().joinToString(" ")}")
        tempo.appliesTo?.let { append(" on the $it") }
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tempo.digits.forEach { digit ->
                Box(
                    modifier = Modifier
                        .widthIn(min = 22.dp)
                        .height(26.dp)
                        .background(background, Shapes.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit.toString(),
                        style = KineticType.dataValue.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = Colors.TextPrimary,
                    )
                }
            }
        }
        tempo.appliesTo?.let {
            Text(text = it, style = KineticType.bodySm, color = Colors.TextSecondary)
        }
    }
}
