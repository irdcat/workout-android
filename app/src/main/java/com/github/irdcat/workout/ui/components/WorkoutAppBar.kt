package com.github.irdcat.workout.ui.components

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.base
import com.composables.composetheme.material3.colorScheme
import com.composables.composetheme.textStyles
import com.composables.composetheme.xl2
import com.github.irdcat.workout.ui.CURRENT_WEEK_SCREEN_PATH
import com.github.irdcat.workout.ui.HISTORY_SCREEN_PATH
import com.github.irdcat.workout.ui.UPCOMING_SCREEN_PATH
import com.github.irdcat.workout.ui.components.icons.Dumbbell

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutAppBar(titleText: String, navController: NavController) {
    CenterAlignedTopAppBar(
        colors = topAppBarColors(
            containerColor = ComposeTheme.colorScheme.primaryContainer,
            titleContentColor = ComposeTheme.colorScheme.primary
        ),
        title = {
            Text(titleText, style = ComposeTheme.textStyles.xl2)
        },
        actions = {
            WorkoutAppBarDropdownMenu(navController)
        },
        navigationIcon = {
            IconButton(onClick = {
                navController.navigate(CURRENT_WEEK_SCREEN_PATH)
            }) {
                Icon(Dumbbell, contentDescription = "Home screen")
            }
        }
    )
}

@Composable
fun WorkoutAppBarDropdownMenu(navController: NavController) {
    DropdownMenuButton {
        DropdownMenuItem(
            text = { Text("History", style = ComposeTheme.textStyles.base) },
            onClick = { navController.navigate(HISTORY_SCREEN_PATH) }
        )
        DropdownMenuItem(
            text = { Text("Upcoming", style = ComposeTheme.textStyles.base) },
            onClick = { navController.navigate(UPCOMING_SCREEN_PATH) }
        )
    }
}