package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.analysis.ExerciseHistories
import com.github.irdcat.workout.domain.analysis.ExerciseHistory
import com.github.irdcat.workout.domain.analysis.VolumeUnit
import com.github.irdcat.workout.domain.analysis.WeekFigures
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.workout.RepsUnit
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.domain.workout.weekOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

private const val SelectedKey = "analysis.exercise"
private const val MaxOptions = 8

private val NoteDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** The user's choices on the screen. */
private data class AnalysisChoices(
    val query: String? = null,
    val range: AnalysisRange = AnalysisRange.EightWeeks,
    val showPlanned: Boolean = true,
    val showActual: Boolean = true,
    /** A tapped week, by its Monday; null for the current one. */
    val selectedWeek: LocalDate? = null,
)

/**
 * Planned against actual for one exercise at a time: volume, top set weight and RPE per
 * week, from the cached plan and the sets logged on this device.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    planRepository: WorkoutPlanRepository,
    logRepository: SessionLogRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val choices = MutableStateFlow(AnalysisChoices())
    private val selected: StateFlow<String?> = savedState.getStateFlow(SelectedKey, null)

    val state: StateFlow<Loadable<AnalysisUiState>> = combine(
        planRepository.observeMonths(),
        logRepository.observeAllSets(),
        selected,
        choices,
    ) { months, sets, selected, choices ->
        val histories = ExerciseHistories(months, sets)
        val today = LocalDate.now(clock)
        val key = selected?.takeIf { key -> histories.catalog.any { it.key == key } }
            ?: histories.lastLogged
            ?: months.weekOf(today)?.workouts?.firstOrNull()?.exercises?.firstOrNull()
                ?.let { exercise -> histories.catalog.firstOrNull { it.name == exercise.name }?.key }
            ?: histories.catalog.firstOrNull()?.key
        val history = key?.let(histories::history)
        val query = choices.query ?: history?.name.orEmpty()
        val matches = matching(histories, query.takeUnless { it == history?.name }.orEmpty())
        Loadable.Ready(
            AnalysisUiState(
                query = query,
                options = matches.take(MaxOptions),
                matchCount = matches.size,
                exerciseCount = histories.catalog.size,
                exercise = history?.let { analysis(it, months.weekOf(today)?.start ?: today.with(DayOfWeek.MONDAY), choices) },
                range = choices.range,
                showPlanned = choices.showPlanned,
                showActual = choices.showActual,
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun onEvent(event: AnalysisEvent) {
        when (event) {
            is AnalysisEvent.QueryChanged -> choices.update { it.copy(query = event.value) }
            is AnalysisEvent.ExercisePicked -> {
                savedState[SelectedKey] = event.key
                choices.update { it.copy(query = null, selectedWeek = null) }
            }
            AnalysisEvent.SearchClosed -> choices.update { it.copy(query = null) }
            is AnalysisEvent.RangeSelected -> choices.update { it.copy(range = event.range, selectedWeek = null) }
            AnalysisEvent.TogglePlanned -> choices.update { it.copy(showPlanned = !it.showPlanned) }
            AnalysisEvent.ToggleActual -> choices.update { it.copy(showActual = !it.showActual) }
            is AnalysisEvent.WeekSelected -> choices.update { it.copy(selectedWeek = event.weekStart) }
        }
    }

    // ---- Mapping ---------------------------------------------------------

    /** Names containing [query], ignoring case; those starting with it first. All of them for an empty query. */
    private fun matching(histories: ExerciseHistories, query: String): List<ExerciseOption> {
        val needle = query.trim()
        return histories.catalog
            .mapNotNull { exercise ->
                val start = if (needle.isEmpty()) 0 else exercise.name.indexOf(needle, ignoreCase = true)
                if (start < 0) {
                    null
                } else {
                    ExerciseOption(exercise.key, exercise.name, exercise.weeksPlanned, exercise.weeksLogged, start, needle.length)
                }
            }
            .sortedBy { it.matchStart != 0 }
    }

    private fun analysis(history: ExerciseHistory, currentWeek: LocalDate, choices: AnalysisChoices): ExerciseAnalysis {
        val columns = columns(history, currentWeek, choices.range)
        val figures = columns.map { start -> history.weeks.firstOrNull { it.weekStart in start..start.plusDays(6) } }
        val currentIndex = columns.indexOf(currentWeek)
        val selectedIndex = choices.selectedWeek?.let(columns::indexOf)?.takeIf { it >= 0 } ?: currentIndex
        return ExerciseAnalysis(
            name = history.name,
            coverage = "In your sheet for ${weeks(history.weeksPlanned)} · logged on this device for ${weeks(history.weeksLogged)}",
            weeks = columns.map { start ->
                ChartWeek(
                    start = start,
                    label = "${start.dayOfMonth}.${start.monthValue}",
                    current = start == currentWeek,
                    future = start > currentWeek,
                )
            },
            selectedWeek = selectedIndex.takeIf { it >= 0 },
            volume = volumePanel(history, columns, figures, currentIndex),
            intensity = intensityPanel(history, columns, figures),
            rpe = rpePanel(history, figures),
        )
    }

    /** Consecutive weeks: the range up to next week, or for "All" every week with anything in it. */
    private fun columns(history: ExerciseHistory, currentWeek: LocalDate, range: AnalysisRange): List<LocalDate> {
        val (first, last) = when (val count = range.weeks) {
            null -> {
                val starts = history.weeks.map { it.weekStart }
                (starts.minOrNull()?.let { minOf(it, currentWeek) } ?: currentWeek) to
                    (starts.maxOrNull()?.let { maxOf(it, currentWeek) } ?: currentWeek)
            }
            else -> currentWeek.minusWeeks(count - 2L) to currentWeek.plusWeeks(1)
        }
        return generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= last }.toList()
    }

    private fun volumePanel(history: ExerciseHistory, columns: List<LocalDate>, figures: List<WeekFigures?>, currentIndex: Int): MetricPanel {
        val (scale, suffix) = when (val unit = history.volumeUnit) {
            VolumeUnit.Kilograms -> "Sets × reps × kg" to "kg"
            is VolumeUnit.Count -> when (unit.unit) {
                RepsUnit.Seconds -> "Hold time, seconds" to "s"
                RepsUnit.Lengths -> "Total lengths" to "lengths"
                RepsUnit.Steps -> "Total steps" to "steps"
                RepsUnit.RepsPerSide -> "Sets × reps per side" to "reps"
                RepsUnit.Reps -> "Sets × reps" to "reps"
            }
        }
        val current = figures.getOrNull(currentIndex)
        val notes = listOfNotNull(
            "Actual so far this week.".takeIf { current?.actualVolume != null && current.plannedVolume != null },
            when {
                history.firstLogged == null -> "Nothing logged on this device yet."
                history.firstLogged > columns.first() -> "No logs on this device before ${history.firstLogged.format(NoteDate)}."
                else -> null
            },
            history.estimatedRange?.let { "Reps given as a range ($it) are charted at their upper end." },
        )
        return MetricPanel.Chart(
            title = "Volume",
            scale = scale,
            suffix = suffix,
            kind = ChartKind.Bars,
            planned = figures.map { it?.plannedVolume },
            actual = figures.map { it?.actualVolume },
            note = notes.joinToString(" ").ifEmpty { null },
        )
    }

    private fun intensityPanel(history: ExerciseHistory, columns: List<LocalDate>, figures: List<WeekFigures?>): MetricPanel {
        if (history.weightless) {
            return MetricPanel.Unavailable(
                title = "Intensity",
                scale = "Top set weight",
                heading = "Not available",
                text = "Bodyweight exercise. The sheet gives no weight, so there is nothing to chart.",
            )
        }
        // The week furthest off plan, either way.
        val furthest = figures.indices
            .mapNotNull { index ->
                val week = figures[index]
                val planned = week?.plannedTop
                val actual = week?.actualTop
                if (planned == null || actual == null || planned == actual) null else Triple(columns[index], actual, actual - planned)
            }
            .maxByOrNull { abs(it.third) }
        return MetricPanel.Chart(
            title = "Intensity",
            scale = "Top set weight",
            suffix = "kg",
            kind = ChartKind.Lines,
            planned = figures.map { it?.plannedTop },
            actual = figures.map { it?.actualTop },
            note = furthest?.let { (week, actual, difference) ->
                "${week.format(NoteDate)}: you lifted ${actual.kg()} kg, ${abs(difference).kg()} kg ${if (difference > 0) "above" else "below"} plan."
            },
        )
    }

    private fun rpePanel(history: ExerciseHistory, figures: List<WeekFigures?>): MetricPanel {
        if (history.noRpe) {
            return MetricPanel.Unavailable(
                title = "RPE",
                scale = "Effort, 6 to 10",
                heading = "No RPE yet",
                text = "Neither the sheet nor your logs have an RPE for this exercise. Add one when you log a set and it will show here.",
            )
        }
        val withTarget = figures.count { it?.plannedRpe != null }
        return MetricPanel.Chart(
            title = "RPE",
            scale = "Effort, 6 to 10",
            suffix = "",
            kind = ChartKind.Lines,
            planned = figures.map { it?.plannedRpe },
            actual = figures.map { it?.actualRpe },
            note = when (withTarget) {
                0 -> "The sheet gives no RPE target in these weeks."
                figures.size -> null
                else -> "The sheet gives an RPE target for $withTarget of ${figures.size} weeks. Gaps are not zeros."
            },
            axis = 6.0..10.0,
        )
    }

    private fun weeks(count: Int) = if (count == 1) "1 week" else "$count weeks"
}
