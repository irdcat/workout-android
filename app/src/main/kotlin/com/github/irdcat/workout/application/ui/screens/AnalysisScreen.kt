package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.MainNavigationBar
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.components.SegmentedControl
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import java.time.LocalDate

// ---- UI state ------------------------------------------------------------

@Immutable
data class AnalysisUiState(
    /** Names of the groups with an exercise in the sheet, to pick from. */
    val groups: List<String>,
    /** The one shown; null when the sheet has none of them. */
    val group: String?,
    /** null when there's no group to show yet. */
    val exercise: ExerciseAnalysis?,
    val range: AnalysisRange,
    val showPlanned: Boolean,
    val showActual: Boolean,
)

enum class AnalysisRange(val label: String, val weeks: Int?) {
    EightWeeks("8 weeks", 8),
    SixteenWeeks("16 weeks", 16),
    All("All", null),
}

@Immutable
data class ExerciseAnalysis(
    val name: String,
    /** e.g. "In your sheet for 30 weeks · logged on this device for 4 weeks". */
    val coverage: String,
    /** The chart's columns, oldest first. */
    val weeks: List<ChartWeek>,
    /** The week whose numbers are shown; this week unless one was tapped. null when it's out of range. */
    val selectedWeek: Int?,
    val volume: MetricPanel,
    val intensity: MetricPanel,
    val rpe: MetricPanel,
)

@Immutable
data class ChartWeek(
    /** Monday of the week. */
    val start: LocalDate,
    /** e.g. "20.4". */
    val label: String,
    val current: Boolean,
    val future: Boolean,
)

enum class ChartKind { Bars, Lines }

sealed interface MetricPanel {
    val title: String
    /** What's measured, e.g. "Sets × reps × kg". */
    val scale: String

    /** One value per week in each series; null where there's nothing (a gap, not a zero). */
    data class Chart(
        override val title: String,
        override val scale: String,
        /** e.g. "kg"; empty for RPE. */
        val suffix: String,
        val kind: ChartKind,
        val planned: List<Double?>,
        val actual: List<Double?>,
        val note: String?,
        /** Fixed bounds (RPE); otherwise fitted to the values. */
        val axis: ClosedFloatingPointRange<Double>? = null,
    ) : MetricPanel

    /** There's nothing to chart, and why. */
    data class Unavailable(
        override val title: String,
        override val scale: String,
        val heading: String,
        val text: String,
    ) : MetricPanel
}

sealed interface AnalysisEvent {
    data class GroupPicked(val name: String) : AnalysisEvent
    data class RangeSelected(val range: AnalysisRange) : AnalysisEvent
    data object TogglePlanned : AnalysisEvent
    data object ToggleActual : AnalysisEvent
    data class WeekSelected(val weekStart: LocalDate) : AnalysisEvent
}

// ---- Route ---------------------------------------------------------------

/** Analysis backed by [AnalysisViewModel]; only tab switches leave this composable. */
@Composable
fun AnalysisRoute(
    onTabSelected: (MainTab) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val analysis = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> AnalysisScreen(state = analysis.value, onEvent = viewModel::onEvent, onTabSelected = onTabSelected)
    }
}

// ---- Screen --------------------------------------------------------------

@Composable
fun AnalysisScreen(
    state: AnalysisUiState,
    onEvent: (AnalysisEvent) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Colors.Canvas,
        bottomBar = { MainNavigationBar(selected = MainTab.Analysis, onSelect = onTabSelected) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Label("Planned and actual")
                Text(text = "ANALYSIS", style = TextStyles.screenTitle, color = Colors.TextPrimary)
            }
            val exercise = state.exercise
            val group = state.group
            if (exercise == null || group == null) {
                Text(
                    text = "None of your analysis groups is in your sheet yet. Groups and the exercises in them are set in Settings.",
                    style = TextStyles.bodyNote,
                    color = Colors.TextSecondary,
                )
                return@Column
            }
            GroupPicker(groups = state.groups, selected = group, onPick = { onEvent(AnalysisEvent.GroupPicked(it)) })
            Text(
                text = exercise.coverage,
                style = TextStyles.bodySm,
                color = Colors.TextSecondary,
            )
            SegmentedControl(
                options = AnalysisRange.entries,
                selected = state.range,
                label = { it.label },
                description = "Time range",
                onSelect = { onEvent(AnalysisEvent.RangeSelected(it)) },
            )
            SeriesToggles(
                showPlanned = state.showPlanned,
                showActual = state.showActual,
                onTogglePlanned = { onEvent(AnalysisEvent.TogglePlanned) },
                onToggleActual = { onEvent(AnalysisEvent.ToggleActual) },
            )
            listOf(exercise.volume, exercise.intensity, exercise.rpe).forEach { panel ->
                MetricCard(
                    panel = panel,
                    analysis = exercise,
                    showPlanned = state.showPlanned,
                    showActual = state.showActual,
                    onSelectWeek = { onEvent(AnalysisEvent.WeekSelected(exercise.weeks[it].start)) },
                )
            }
            NumbersTable(exercise)
        }
    }
}

// ---- Controls ------------------------------------------------------------

/** Up to [SEGMENTS] groups side by side; more scroll sideways as chips. */
@Composable
private fun GroupPicker(groups: List<String>, selected: String, onPick: (String) -> Unit) {
    if (groups.size <= SEGMENTS) {
        SegmentedControl(options = groups, selected = selected, label = { it }, description = "Exercise group", onSelect = onPick)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = "Exercise group" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { name ->
            val isSelected = name == selected
            Surface(
                selected = isSelected,
                onClick = { onPick(name) },
                modifier = Modifier.height(44.dp),
                shape = Shapes.xl,
                color = if (isSelected) Colors.Primary else Colors.Surface1,
                contentColor = if (isSelected) Colors.OnPrimary else Colors.TextSecondary,
                border = if (isSelected) null else BorderStroke(1.dp, Colors.Border),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(text = name, style = TextStyles.bodyMd.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                }
            }
        }
    }
}

private const val SEGMENTS = 3

/** The legend doubles as the switch for each series. */
@Composable
private fun SeriesToggles(
    showPlanned: Boolean,
    showActual: Boolean,
    onTogglePlanned: () -> Unit,
    onToggleActual: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Show", modifier = Modifier.padding(end = 2.dp))
        SeriesToggle("Planned", planned = true, on = showPlanned, onToggle = onTogglePlanned)
        SeriesToggle("Actual", planned = false, on = showActual, onToggle = onToggleActual)
    }
}

@Composable
private fun SeriesToggle(label: String, planned: Boolean, on: Boolean, onToggle: () -> Unit) {
    Surface(
        checked = on,
        onCheckedChange = { onToggle() },
        modifier = Modifier.height(44.dp),
        shape = Shapes.xl,
        color = Colors.Surface1,
        contentColor = if (on) Colors.TextPrimary else Colors.TextSecondary,
        border = BorderStroke(1.dp, Colors.Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeriesSwatch(planned = planned, modifier = Modifier.alpha(if (on) 1f else 0.4f))
            Text(text = label, style = TextStyles.bodyNoteStrong)
        }
    }
}

/** A short line in the series' style: dashed grey for planned, solid accent for actual. */
@Composable
private fun SeriesSwatch(planned: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 22.dp, height = 10.dp)) {
        drawLine(
            color = if (planned) Colors.TextSecondary else Colors.Primary,
            start = Offset(1.dp.toPx(), size.height / 2),
            end = Offset(size.width - 1.dp.toPx(), size.height / 2),
            strokeWidth = if (planned) 2.dp.toPx() else 3.dp.toPx(),
            pathEffect = if (planned) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
        )
    }
}

// ---- Metric cards --------------------------------------------------------

@Composable
private fun MetricCard(
    panel: MetricPanel,
    analysis: ExerciseAnalysis,
    showPlanned: Boolean,
    showActual: Boolean,
    onSelectWeek: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Surface1, Shapes.xl)
            .border(1.dp, Colors.Border, Shapes.xl)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = panel.title,
                modifier = Modifier.weight(1f),
                style = TextStyles.bodyLg.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = Colors.TextPrimary,
            )
            Label(panel.scale)
        }
        when (panel) {
            is MetricPanel.Unavailable -> UnavailableNotice(panel)
            is MetricPanel.Chart -> {
                val index = analysis.selectedWeek
                val week = index?.let { analysis.weeks[it] }
                Label(
                    when {
                        week == null -> "Outside this range"
                        week.current -> "This week"
                        else -> "Week of ${week.label}"
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Planned", planned = true, value = index?.let { panel.planned[it] }, suffix = panel.suffix, modifier = Modifier.weight(1f))
                    Stat("Actual", planned = false, value = index?.let { panel.actual[it] }, suffix = panel.suffix, modifier = Modifier.weight(1f))
                }
                AnalysisChart(
                    chart = panel,
                    weeks = analysis.weeks,
                    selectedWeek = analysis.selectedWeek,
                    showPlanned = showPlanned,
                    showActual = showActual,
                    onSelectWeek = onSelectWeek,
                )
                panel.note?.let { note ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = Colors.TextSecondary, modifier = Modifier.size(16.dp))
                        Text(text = note, style = TextStyles.bodySm, color = Colors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, planned: Boolean, value: Double?, suffix: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SeriesSwatch(planned = planned)
            Label(label)
        }
        Text(
            text = buildAnnotatedString {
                append(value?.let(::formatValue) ?: "–")
                if (value != null && suffix.isNotEmpty()) {
                    withStyle(SpanStyle(fontSize = 16.sp, color = Colors.TextSecondary, fontWeight = FontWeight.SemiBold)) { append(" $suffix") }
                }
            },
            style = TextStyles.dataDisplayLg.copy(fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            // Text keeps text colours; the swatch beside it carries the series.
            color = Colors.TextPrimary,
        )
    }
}

@Composable
private fun UnavailableNotice(panel: MetricPanel.Unavailable) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Canvas, Shapes.xl)
            .border(1.dp, Colors.Border, Shapes.xl)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Colors.Surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = null, tint = Colors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(text = panel.heading, style = TextStyles.bodyMd.copy(fontWeight = FontWeight.SemiBold), color = Colors.TextPrimary)
            Text(text = panel.text, style = TextStyles.bodySm, color = Colors.TextSecondary)
        }
    }
}

// ---- All numbers ---------------------------------------------------------

/** Every week's numbers side by side: the chart in table form. */
@Composable
private fun NumbersTable(analysis: ExerciseAnalysis) {
    val charts = listOf(analysis.volume, analysis.intensity, analysis.rpe).filterIsInstance<MetricPanel.Chart>()
    if (charts.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Surface1, Shapes.xl)
            .border(1.dp, Colors.Border, Shapes.xl)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = "All numbers",
                modifier = Modifier.weight(1f),
                style = TextStyles.bodyLg.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = Colors.TextPrimary,
            )
            Text(
                text = buildAnnotatedString {
                    append("PLANNED · ")
                    withStyle(SpanStyle(color = Colors.Primary)) { append("ACTUAL") }
                },
                style = TextStyles.labelCaps.copy(fontSize = 11.sp),
                color = Colors.TextSecondary,
            )
        }
        // Widths fit the widest values ("2,185 · 1,260") on one line.
        fun columnWeight(chart: MetricPanel.Chart) = when (chart) {
            analysis.volume -> 1.35f
            analysis.intensity -> 1.1f
            else -> 0.85f
        }
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            TableHeader("Week", Modifier.width(48.dp))
            charts.forEach { chart ->
                TableHeader(
                    when (chart) {
                        analysis.volume -> "Volume ${chart.suffix}".trim()
                        analysis.intensity -> "Top kg"
                        else -> "RPE"
                    },
                    Modifier.weight(columnWeight(chart)),
                )
            }
        }
        analysis.weeks.forEachIndexed { index, week ->
            HorizontalDivider(color = Colors.Border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .clearAndSetSemantics {
                        contentDescription = "Week of ${week.label}" + charts.joinToString("") { chart ->
                            ", ${chart.title}: planned ${chart.planned[index]?.let(::formatValue) ?: "none"}, " +
                                "actual ${chart.actual[index]?.let(::formatValue) ?: "none"}"
                        }
                        role = Role.Image
                        if (index == analysis.selectedWeek) selected = true
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = week.label.replace('.', '/') + if (week.current) " now" else "",
                    modifier = Modifier.width(48.dp),
                    style = TextStyles.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = if (week.current) Colors.Primary else Colors.TextSecondary,
                )
                charts.forEach { chart ->
                    Text(
                        text = buildAnnotatedString {
                            cell(chart.planned[index], Colors.TextPrimary)
                            withStyle(SpanStyle(color = Colors.TextSecondary)) { append(" · ") }
                            cell(chart.actual[index], Colors.Primary)
                        },
                        modifier = Modifier.weight(columnWeight(chart)),
                        style = TextStyles.dataValue.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.cell(value: Double?, color: Color) {
    withStyle(SpanStyle(color = if (value == null) Colors.Surface3 else color)) { append(value?.let(::formatValue) ?: "–") }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyles.labelCaps.copy(fontSize = 11.sp),
        color = Colors.TextSecondary,
    )
}

// ---- Previews ------------------------------------------------------------

private val previewWeeks = listOf("20.4", "27.4", "4.5", "11.5", "18.5", "25.5", "1.6", "8.6").mapIndexed { index, label ->
    ChartWeek(LocalDate.of(2026, 4, 20).plusWeeks(index.toLong()), label, current = index == 6, future = index == 7)
}

@Preview(name = "Analysis", widthDp = 390, heightDp = 1880, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun AnalysisPreview() {
    WorkoutTheme {
        AnalysisScreen(
            state = AnalysisUiState(
                groups = listOf("Squat", "Bench", "Deadlift"),
                group = "Deadlift",
                exercise = ExerciseAnalysis(
                    name = "Deadlift",
                    coverage = "In your sheet for 30 weeks · logged on this device for 4 weeks",
                    weeks = previewWeeks,
                    selectedWeek = 6,
                    volume = MetricPanel.Chart(
                        "Volume", "Sets × reps × kg", "kg", ChartKind.Bars,
                        listOf(1980.0, 2040.0, 2100.0, 2100.0, 2160.0, 2160.0, 2185.0, 2250.0),
                        listOf(null, null, 2100.0, 2025.0, 2160.0, 2060.0, 1260.0, null),
                        "Actual so far this week. No logs on this device before 4 May.",
                    ),
                    intensity = MetricPanel.Chart(
                        "Intensity", "Top set weight", "kg", ChartKind.Lines,
                        listOf(85.0, 87.5, 90.0, 90.0, 92.5, 92.5, 92.5, 95.0),
                        listOf(null, null, 90.0, 90.0, 92.5, 100.0, 90.0, null),
                        "25 May: you lifted 100 kg, 7.5 kg above plan.",
                    ),
                    rpe = MetricPanel.Chart(
                        "RPE", "Effort, 6 to 10", "", ChartKind.Lines,
                        listOf(null, null, 8.0, null, 8.5, null, 8.5, null),
                        listOf(null, null, 8.5, 8.0, 9.0, 7.0, 8.0, null),
                        "The sheet gives an RPE target for 3 of 8 weeks. Gaps are not zeros.",
                        axis = 6.0..10.0,
                    ),
                ),
                range = AnalysisRange.EightWeeks,
                showPlanned = true,
                showActual = true,
            ),
            onEvent = {},
            onTabSelected = {},
        )
    }
}
