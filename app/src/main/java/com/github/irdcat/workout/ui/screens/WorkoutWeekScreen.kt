package com.github.irdcat.workout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.irdcat.workout.domain.model.WorkoutWeek
import com.github.irdcat.workout.ui.buildWorkoutDayScreenEditPath
import com.github.irdcat.workout.ui.buildWorkoutDayScreenPath
import com.github.irdcat.workout.ui.components.WorkoutAppBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.base
import com.composables.composetheme.colors
import com.composables.composetheme.gray300
import com.composables.composetheme.green800
import com.composables.composetheme.material3.colorScheme
import com.composables.composetheme.textStyles
import com.composables.composetheme.xl
import com.github.irdcat.workout.domain.model.WorkoutDay
import com.github.irdcat.workout.ui.components.Accordion
import com.github.irdcat.workout.ui.components.Separator
import com.github.irdcat.workout.ui.components.icons.Check


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutWeekScreen(title: String, week: WorkoutWeek, navController: NavController) {
    var expandedDay by remember { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = {
            WorkoutAppBar(title, navController)
        }
    ) { innerPadding ->
        Card(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(week.days) { index, day ->
                    WorkoutDayAccordion(
                        day = day,
                        expanded = expandedDay == index,
                        onClick = {
                            expandedDay = if (expandedDay == index) {
                                null
                            } else {
                                index
                            }
                        },
                        onView = {
                            navController.navigate(
                                buildWorkoutDayScreenPath(
                                    week.startDate,
                                    day.index
                                )
                            )
                        },
                        onEdit = {
                            navController.navigate(
                                buildWorkoutDayScreenEditPath(
                                    week.startDate,
                                    day.index
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutDayAccordion(
    day: WorkoutDay,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
    onView: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    Accordion(
        title = {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(day.workoutName, Modifier.weight(1f), style = ComposeTheme.textStyles.xl)
                if (day.isCompleted()) {
                    Icon(Check, contentDescription = null, tint = ComposeTheme.colors.green800)
                }
            }
        },
        expanded,
        onClick)
    {
        Column {
            day.exercises.forEachIndexed { index, entry ->
                val names = entry.sets
                    .map { it.name }
                    .distinct()
                val summaries = names.map {
                    Pair(it, entry.sets.count { s ->
                        s.name == it
                    })
                }
                Column {
                    if (index != 0) {
                        Separator(2.dp, ComposeTheme.colors.gray300)
                    }
                    summaries.forEach {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Absolute.SpaceBetween
                        ) {
                            Text(it.first, modifier = Modifier.weight(1f), style = ComposeTheme.textStyles.base)
                            Text(
                                "${it.second} sets",
                                style = ComposeTheme.textStyles.base
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
                horizontalArrangement = Arrangement.Absolute.SpaceBetween
            ) {
                if (day.isCompleted()) {
                    OutlinedButton(onClick = onView, colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeTheme.colorScheme.secondaryContainer,
                        contentColor = ComposeTheme.colorScheme.secondary
                    )) {
                        Text("View", style = ComposeTheme.textStyles.base)
                    }
                } else {
                    Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeTheme.colorScheme.primaryContainer,
                        contentColor = ComposeTheme.colorScheme.primary
                    )) {
                        Text("Start", style = ComposeTheme.textStyles.base)
                    }
                }
            }
        }
    }
}