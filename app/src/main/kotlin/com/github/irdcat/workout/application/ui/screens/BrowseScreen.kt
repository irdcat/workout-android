package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.MainNavigationBar
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// ---- UI state ------------------------------------------------------------

@Immutable
data class BrowseUiState(
    /** e.g. "2026". */
    val yearLabel: String,
    /** Short month labels, one per loaded sheet, e.g. "Jun". */
    val months: List<String>,
    val selectedMonth: Int,
    val weeks: List<BrowseWeek>,
    /** A week that starts in the selected month but lives in the next month's sheet. */
    val spillover: SpilloverWeek? = null,
)

@Immutable
data class BrowseWeek(
    val id: String,
    /** e.g. "1 – 7 Jun". */
    val range: String,
    val timing: WeekTiming,
    val workouts: List<WorkoutSummary>,
    /** Marked "WOLNE" in the sheet. */
    val isRestWeek: Boolean = false,
)

enum class WeekTiming { Past, Current, Upcoming }

@Immutable
data class SpilloverWeek(
    /** e.g. "29 Jun – 5 Jul". */
    val range: String,
    /** e.g. "June". */
    val startMonth: String,
    /** e.g. "July". */
    val sheetMonth: String,
    /** Index into [BrowseUiState.months] of the sheet that holds the week. */
    val sheetMonthIndex: Int,
)

// ---- Route ---------------------------------------------------------------

/** Browse backed by [BrowseViewModel]; only navigation leaves this composable. */
@Composable
fun BrowseRoute(
    onOpenWorkout: (id: String) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val browse = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> BrowseScreen(
            state = browse.value,
            onMonthSelected = viewModel::onMonthSelected,
            onOpenWorkout = onOpenWorkout,
            onTabSelected = onTabSelected,
        )
    }
}

// ---- Screen --------------------------------------------------------------

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onMonthSelected: (index: Int) -> Unit,
    onOpenWorkout: (id: String) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The current week starts expanded; any other week can be expanded on tap.
    var expandedWeekId by rememberSaveable(state.selectedMonth) {
        mutableStateOf(state.weeks.firstOrNull { it.timing == WeekTiming.Current }?.id)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Colors.Canvas,
        bottomBar = { MainNavigationBar(selected = MainTab.Browse, onSelect = onTabSelected) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "ALL WORKOUTS · ${state.yearLabel}",
                    style = KineticType.labelCaps,
                    color = Colors.TextSecondary,
                )
                Text(
                    text = "BROWSE",
                    style = KineticType.screenTitle,
                    color = Colors.TextPrimary,
                )
            }

            MonthTabs(
                months = state.months,
                selected = state.selectedMonth,
                onSelect = onMonthSelected,
            )

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.weeks.forEach { week ->
                    WeekCard(
                        week = week,
                        expanded = week.id == expandedWeekId,
                        onToggle = {
                            expandedWeekId = if (expandedWeekId == week.id) null else week.id
                        },
                        onOpenWorkout = onOpenWorkout,
                    )
                }
                state.spillover?.let { spillover ->
                    SpilloverNote(
                        spillover = spillover,
                        onClick = { onMonthSelected(spillover.sheetMonthIndex) },
                    )
                }
            }

            Text(
                text = "Only unfinished workouts in the current week can be started.",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = KineticType.bodySm,
                color = Colors.TextSecondary,
            )
        }
    }
}

// ---- Month tabs ----------------------------------------------------------

@Composable
private fun MonthTabs(months: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        listState.animateScrollToItem((selected - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(months) { index, month ->
            val isSelected = index == selected
            Surface(
                selected = isSelected,
                onClick = { onSelect(index) },
                modifier = Modifier
                    .height(44.dp)
                    .semantics { role = Role.Tab },
                shape = Shapes.pill,
                color = if (isSelected) Colors.Primary else Colors.Surface2,
                contentColor = if (isSelected) Colors.OnPrimary else Colors.TextPrimary,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = month,
                        style = KineticType.rowTitle,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---- Weeks ---------------------------------------------------------------

@Composable
private fun WeekCard(
    week: BrowseWeek,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val isCurrent = week.timing == WeekTiming.Current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.xl,
        color = Colors.Surface1,
        border = if (isCurrent) BorderStroke(1.5.dp, Colors.Primary) else BorderStroke(1.dp, Colors.Border),
    ) {
        Column {
            Surface(onClick = onToggle, color = Color.Transparent) {
                WeekHeaderRow(week = week)
            }
            if (expanded && week.workouts.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Colors.Border)
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    week.workouts.forEach { workout ->
                        WeekWorkoutRow(workout = workout, onClick = { onOpenWorkout(workout.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeaderRow(week: BrowseWeek) {
    val done = week.workouts.count { it.status is WorkoutStatus.Completed }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = week.range,
                    style = KineticType.bodyLg.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = Colors.TextPrimary,
                )
                if (week.timing == WeekTiming.Current) {
                    Text(
                        text = "CURRENT",
                        style = KineticType.labelCaps.copy(fontWeight = FontWeight.Bold),
                        color = Colors.Primary,
                    )
                }
            }
            Text(
                text = when {
                    week.isRestWeek -> "Rest week"
                    week.timing == WeekTiming.Upcoming -> "Upcoming · view only"
                    else -> workoutsLabel(week.workouts.size)
                },
                style = KineticType.bodySm,
                color = Colors.TextSecondary,
            )
        }
        WeekProgress(workouts = week.workouts)
        Text(
            text = "$done/${week.workouts.size}",
            modifier = Modifier.width(40.dp),
            style = KineticType.dataValue.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = Colors.TextPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun WeekProgress(workouts: List<WorkoutSummary>) {
    Row(
        modifier = Modifier.width(56.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        workouts.forEach { workout ->
            val color = when (workout.status) {
                is WorkoutStatus.Completed -> Colors.Primary
                WorkoutStatus.InProgress -> Colors.Primary.copy(alpha = 0.4f)
                WorkoutStatus.NotStarted -> Colors.Border
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(color, Shapes.pill),
            )
        }
    }
}

@Composable
private fun WeekWorkoutRow(workout: WorkoutSummary, onClick: () -> Unit) {
    val inProgress = workout.status == WorkoutStatus.InProgress
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = workout.name,
                modifier = Modifier.width(28.dp),
                style = KineticType.dataValue,
                color = if (inProgress) Colors.Primary else Colors.TextSecondary,
                maxLines = 1,
            )
            Text(
                text = workout.title,
                modifier = Modifier.weight(1f),
                style = KineticType.bodyMd,
                color = Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (workout.status) {
                    is WorkoutStatus.Completed -> "Done"
                    WorkoutStatus.InProgress -> "In progress"
                    WorkoutStatus.NotStarted -> "Not started"
                },
                style = KineticType.bodySm.copy(
                    fontWeight = if (inProgress) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (inProgress) Colors.Primary else Colors.TextSecondary,
            )
        }
    }
}

private fun workoutsLabel(count: Int): String =
    if (count == 1) "1 workout" else "$count workouts"

// ---- Spillover -----------------------------------------------------------

@Composable
private fun SpilloverNote(spillover: SpilloverWeek, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(color = Colors.Border, cornerRadius = 12.dp),
        shape = Shapes.xl,
        color = Color.Transparent,
        contentColor = Colors.TextSecondary,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "${spillover.range} starts in ${spillover.startMonth} but is listed in the ${spillover.sheetMonth} sheet.",
                style = KineticType.bodyNote,
            )
        }
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val strokeWidth = 1.dp.toPx()
    val dash = 4.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        ),
    )
}

// ---- Previews ------------------------------------------------------------

private val previewMonths = listOf("Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep")

private fun previewWeek(
    id: String,
    range: String,
    timing: WeekTiming,
    statuses: List<WorkoutStatus>,
) = BrowseWeek(
    id = id,
    range = range,
    timing = timing,
    workouts = statuses.mapIndexed { i, status ->
        WorkoutSummary(
            id = "$id-t${i + 1}",
            name = "T${i + 1}",
            title = if (i == 1) "Half Tucked Front Lever · RDL" else "Przysiad · Bench Press",
            exerciseCount = 7,
            supersetCount = 1,
            status = status,
        )
    },
)

private val notStarted = List(3) { WorkoutStatus.NotStarted }

@Preview(name = "Current month", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun BrowseCurrentMonthPreview() {
    WorkoutTheme {
        BrowseScreen(
            state = BrowseUiState(
                yearLabel = "2026",
                months = previewMonths,
                selectedMonth = 4,
                weeks = listOf(
                    previewWeek(
                        id = "jun-1",
                        range = "1 – 7 Jun",
                        timing = WeekTiming.Current,
                        statuses = listOf(
                            WorkoutStatus.Completed("Mon"),
                            WorkoutStatus.InProgress,
                            WorkoutStatus.NotStarted,
                        ),
                    ),
                    previewWeek("jun-2", "8 – 14 Jun", WeekTiming.Upcoming, notStarted),
                    previewWeek("jun-3", "15 – 21 Jun", WeekTiming.Upcoming, notStarted),
                    previewWeek("jun-4", "22 – 28 Jun", WeekTiming.Upcoming, notStarted),
                ),
                spillover = SpilloverWeek(
                    range = "29 Jun – 5 Jul",
                    startMonth = "June",
                    sheetMonth = "July",
                    sheetMonthIndex = 5,
                ),
            ),
            onMonthSelected = {},
            onOpenWorkout = {},
            onTabSelected = {},
        )
    }
}

@Preview(name = "Past month", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun BrowsePastMonthPreview() {
    val done = listOf(
        WorkoutStatus.Completed("Mon"),
        WorkoutStatus.Completed("Wed"),
        WorkoutStatus.Completed("Fri"),
    )
    WorkoutTheme {
        BrowseScreen(
            state = BrowseUiState(
                yearLabel = "2026",
                months = previewMonths,
                selectedMonth = 3,
                weeks = listOf(
                    previewWeek("may-1", "4 – 10 May", WeekTiming.Past, done),
                    previewWeek("may-2", "11 – 17 May", WeekTiming.Past, done),
                    previewWeek(
                        id = "may-3",
                        range = "18 – 24 May",
                        timing = WeekTiming.Past,
                        statuses = listOf(
                            WorkoutStatus.Completed("Tue"),
                            WorkoutStatus.Completed("Thu"),
                            WorkoutStatus.NotStarted,
                        ),
                    ),
                    previewWeek("may-4", "25 – 31 May", WeekTiming.Past, done),
                ),
            ),
            onMonthSelected = {},
            onOpenWorkout = {},
            onTabSelected = {},
        )
    }
}
