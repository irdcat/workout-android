package com.github.irdcat.workout.application.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(val label: String, val icon: ImageVector) {
    Week("Week", Icons.Outlined.CalendarMonth),
    Browse("Browse", Icons.AutoMirrored.Outlined.List),
    Analysis("Analysis", Icons.Outlined.BarChart),
    Scores("Scores", Icons.Outlined.Calculate),
    Settings("Settings", Icons.Outlined.Tune),
}

/** The tabs the navigation bar shows; settings can hide some. Provided at the app's root. */
val LocalMainTabs = staticCompositionLocalOf { MainTab.entries.toList() }

/** The tabs to show, without the beta ones that are hidden. */
fun visibleMainTabs(hideAnalysisTab: Boolean = false, hideScoresTab: Boolean = false): List<MainTab> =
    MainTab.entries.filterNot { (hideAnalysisTab && it == MainTab.Analysis) || (hideScoresTab && it == MainTab.Scores) }
