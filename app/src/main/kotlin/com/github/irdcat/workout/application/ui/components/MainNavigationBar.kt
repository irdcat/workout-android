package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType

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

@Composable
fun MainNavigationBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = Colors.Border)
        NavigationBar(containerColor = Colors.Surface1) {
            LocalMainTabs.current.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                    label = {
                        Text(
                            text = tab.label,
                            style = KineticType.bodySm.copy(fontWeight = FontWeight.SemiBold),
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Colors.Primary,
                        selectedTextColor = Colors.Primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Colors.TextSecondary,
                        unselectedTextColor = Colors.TextSecondary,
                    ),
                )
            }
        }
    }
}
