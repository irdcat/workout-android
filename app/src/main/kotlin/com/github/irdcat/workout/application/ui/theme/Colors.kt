package com.github.irdcat.workout.application.ui.theme

import androidx.compose.ui.graphics.Color

/** The app is dark only; these feed [WorkoutTheme]'s color scheme and the screens directly. */
object Colors {

    // ---- Surfaces, back to front -----------------------------------------
    /** Root canvas behind every screen. */
    val Canvas = Color(0xFF0B0F14)
    /** Cards and sheets. */
    val Surface1 = Color(0xFF121820)
    /** Rows, chips and secondary buttons on a card. */
    val Surface2 = Color(0xFF1A222D)
    /** Chips on a [Surface2] container; selected segments; empty progress. */
    val Surface3 = Color(0xFF243040)

    // ---- Accents ---------------------------------------------------------
    /** Actions, the active set and completion. Nothing else. */
    val Primary = Color(0xFFD4FF00)
    val PrimaryPressed = Color(0xFFB6DB00)
    val OnPrimary = Color(0xFF0B0F14)

    /** Cadence, tempo and interval timers. */
    val Secondary = Color(0xFF00F0FF)

    /** Offline, running over the rest time, ending early. */
    val Tertiary = Color(0xFFFFB800)

    /** Destructive actions and errors. */
    val Critical = Color(0xFFFF3B30)

    // ---- Text and strokes ------------------------------------------------
    val TextPrimary = Color(0xFFF3F4F6)
    /** Units, captions and labels. */
    val TextSecondary = Color(0xFF9CA3AF)

    val Border = Color(0xFF283546)
}
