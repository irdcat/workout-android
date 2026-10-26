package com.github.irdcat.workout.application.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii used across the screens. */
object Shapes {
    /** Tempo digits, checkbox marks. */
    val md = RoundedCornerShape(6.dp)
    /** Small badges, set-group chips, segmented buttons. */
    val lg = RoundedCornerShape(8.dp)
    /** Cards, buttons and inputs. */
    val xl = RoundedCornerShape(12.dp)
    /** Status chips, tabs and progress bars. */
    val pill = RoundedCornerShape(percent = 50)
}
