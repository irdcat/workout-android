package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles

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
                            style = TextStyles.bodySm.copy(fontWeight = FontWeight.SemiBold),
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
