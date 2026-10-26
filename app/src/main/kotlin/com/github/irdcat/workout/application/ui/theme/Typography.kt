package com.github.irdcat.workout.application.ui.theme

import android.os.Build
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.github.irdcat.workout.R
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Two families: Space Grotesk for structure and titles, JetBrains Mono for numbers.
 * Both are under the SIL Open Font License; the license texts ship in assets/licenses.
 */
val Grotesk: FontFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    groteskSemiBold(),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val Mono: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Space Grotesk has no static SemiBold, only its variable font. Font variations
 * need Android 8, so older devices use Medium for SemiBold text.
 */
private fun groteskSemiBold(): Font =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Font(
            R.font.space_grotesk_variable,
            FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.SemiBold.weight)),
        )
    } else {
        Font(R.font.space_grotesk_medium, FontWeight.SemiBold)
    }

/** Tabular figures, so changing numbers never shift the layout. */
private const val TABULAR = "tnum"

object KineticType {

    // ---- Grotesk: structure and titles ------------------------------------
    val headlineXl = TextStyle(
        fontFamily = Grotesk, fontSize = 48.sp, fontWeight = FontWeight.Bold,
        lineHeight = 52.sp, letterSpacing = (-0.04).em,
    )
    /** The big uppercase title at the top of a screen, e.g. "SETTINGS". */
    val screenTitle = headlineXl.copy(fontSize = 56.sp, lineHeight = 54.sp)
    val headlineLg = TextStyle(
        fontFamily = Grotesk, fontSize = 32.sp, fontWeight = FontWeight.Bold,
        lineHeight = 36.sp, letterSpacing = (-0.02).em,
    )
    val headlineMd = TextStyle(
        fontFamily = Grotesk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp, letterSpacing = (-0.01).em,
    )
    val bodyLg = TextStyle(
        fontFamily = Grotesk, fontSize = 18.sp, fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    )
    val bodyMd = TextStyle(
        fontFamily = Grotesk, fontSize = 15.sp, fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    )
    /** Titles of list rows and settings, e.g. "Keep screen on". */
    val rowTitle = bodyMd.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    /** Notes, hints and secondary lines on cards. */
    val bodyNote = TextStyle(
        fontFamily = Grotesk, fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 19.sp,
    )
    /** Small buttons and status text, e.g. "Sign out", "Synced 14:32". */
    val bodyNoteStrong = bodyNote.copy(fontWeight = FontWeight.SemiBold)
    val bodySm = TextStyle(
        fontFamily = Grotesk, fontSize = 13.sp, fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )
    val buttonLabel = TextStyle(
        fontFamily = Grotesk, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
    )
    /** The main action at the bottom of a screen. */
    val buttonLabelLg = buttonLabel.copy(fontSize = 18.sp, lineHeight = 22.sp)

    // ---- Mono: numbers and labels -------------------------------------------
    val dataDisplayXl = TextStyle(
        fontFamily = Mono, fontSize = 56.sp, fontWeight = FontWeight.Bold,
        lineHeight = 56.sp, letterSpacing = (-0.05).em, fontFeatureSettings = TABULAR,
    )
    val dataDisplayLg = TextStyle(
        fontFamily = Mono, fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR,
    )
    val dataValue = TextStyle(
        fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        lineHeight = 22.sp, fontFeatureSettings = TABULAR,
    )
    val dataMonoMd = TextStyle(
        fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Medium,
        lineHeight = 20.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR,
    )
    /** Section labels; always uppercase. */
    val labelCaps = TextStyle(
        fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp, letterSpacing = 0.08.em,
    )
}
