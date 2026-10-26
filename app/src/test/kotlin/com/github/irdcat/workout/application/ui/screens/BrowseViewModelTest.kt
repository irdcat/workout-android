package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.infrastructure.google.SheetFixtures
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.fixedClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowseViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.browse(today: String): BrowseViewModel {
        val viewModel = BrowseViewModel(FakeWorkoutPlanRepository(SheetFixtures.months()), fixedClock(today))
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun BrowseViewModel.ready(): BrowseUiState = (state.value as Loadable.Ready).value

    @Test
    fun opensOnTheCurrentMonth() = runTest(main.dispatcher) {
        val browse = browse("2026-06-10").ready()

        assertEquals("2026", browse.yearLabel)
        assertEquals(listOf("Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep"), browse.months)
        assertEquals("Jun", browse.months[browse.selectedMonth])
        assertEquals(
            listOf("1 – 7 Jun", "8 – 14 Jun", "15 – 21 Jun", "22 – 28 Jun"),
            browse.weeks.map { it.range },
        )
        assertEquals(
            listOf(WeekTiming.Past, WeekTiming.Current, WeekTiming.Upcoming, WeekTiming.Upcoming),
            browse.weeks.map { it.timing },
        )
        assertEquals(
            SpilloverWeek(range = "29 Jun – 5 Jul", startMonth = "June", sheetMonth = "July", sheetMonthIndex = 5),
            browse.spillover,
        )
    }

    @Test
    fun withoutAPlanThisMonthOpensTheLatestMonthBefore() = runTest(main.dispatcher) {
        val browse = browse("2026-10-21").ready()

        assertEquals("Sep", browse.months[browse.selectedMonth])
        assertTrue(browse.weeks.all { it.timing == WeekTiming.Past })
        assertNull(browse.spillover)
    }

    @Test
    fun selectingAMonthShowsItsWeeks() = runTest(main.dispatcher) {
        val viewModel = browse("2026-06-10")

        viewModel.onMonthSelected(viewModel.ready().months.indexOf("May"))

        val may = viewModel.ready()
        assertEquals("May", may.months[may.selectedMonth])
        assertTrue(may.weeks.first { it.range == "4 – 10 May" }.isRestWeek)
    }
}
