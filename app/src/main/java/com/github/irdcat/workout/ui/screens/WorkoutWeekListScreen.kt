package com.github.irdcat.workout.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.base
import com.composables.composetheme.textStyles
import com.github.irdcat.workout.domain.model.WorkoutWeek
import com.github.irdcat.workout.ui.buildWorkoutWeekScreenPath
import com.github.irdcat.workout.ui.components.List
import com.github.irdcat.workout.ui.components.ListItem
import com.github.irdcat.workout.ui.components.WorkoutAppBar

@Composable
fun WorkoutWeekListScreen(title: String, weeks: List<WorkoutWeek>, navController: NavController) {
    Scaffold(
        topBar = {
            WorkoutAppBar(title, navController)
        }
    ) { innerPadding ->
        List(modifier = Modifier.padding(innerPadding)) {
            items(weeks) { week ->
                ListItem(onClick = {
                    navController.navigate(buildWorkoutWeekScreenPath(week.startDate))
                }) {
                    Text("${week.startDate} - ${week.endDate}", style = ComposeTheme.textStyles.base)
                }
            }
        }
    }
}