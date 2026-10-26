package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    planRepository: WorkoutPlanRepository,
    private val clock: Clock,
) : ViewModel() {

    /** null until the user picks a month; then the month holding today is shown. */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)

    private var months: List<TrainingMonth> = emptyList()

    val state: StateFlow<Loadable<BrowseUiState>> = combine(
        planRepository.observeMonths(),
        selectedMonth,
    ) { months, selected ->
        this.months = months
        Loadable.Ready(browseState(months, selected, LocalDate.now(clock)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun onMonthSelected(index: Int) {
        months.getOrNull(index)?.let { selectedMonth.value = it.month }
    }

    private fun browseState(months: List<TrainingMonth>, selected: YearMonth?, today: LocalDate): BrowseUiState {
        val index = months.indexOfFirst { it.month == selected }.takeIf { it >= 0 }
            ?: defaultMonthIndex(months, YearMonth.from(today))
        val month = months.getOrNull(index)
        val next = months.getOrNull(index + 1)
        // A week starting this month but holding more days of the next one sits in the next month.
        val spillover = next?.weeks?.firstOrNull { month != null && YearMonth.from(it.start) == month.month }

        return BrowseUiState(
            yearLabel = (month?.month ?: YearMonth.from(today)).year.toString(),
            months = months.map { shortMonthName(it.month) },
            selectedMonth = index,
            weeks = month?.weeks.orEmpty().map { week ->
                BrowseWeek(
                    id = week.id,
                    range = week.range,
                    timing = week.timing(today),
                    workouts = week.workouts.map { it.toSummary() },
                    isRestWeek = week.isRestWeek,
                )
            },
            spillover = spillover?.let {
                SpilloverWeek(
                    range = it.range,
                    startMonth = monthName(month!!.month),
                    sheetMonth = monthName(next.month),
                    sheetMonthIndex = index + 1,
                )
            },
        )
    }

    /** The current month, else the latest month before it, else the first one. */
    private fun defaultMonthIndex(months: List<TrainingMonth>, current: YearMonth): Int =
        months.indexOfFirst { it.month == current }.takeIf { it >= 0 }
            ?: months.indexOfLast { it.month < current }.takeIf { it >= 0 }
            ?: 0
}
