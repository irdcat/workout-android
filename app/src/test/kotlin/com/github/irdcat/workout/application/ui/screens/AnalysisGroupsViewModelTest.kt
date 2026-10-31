package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.analysis.DefaultExerciseGroups
import com.github.irdcat.workout.domain.analysis.ExerciseGroup
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnalysisGroupsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun two(id: String, first: String, second: String) =
        workout(id, "T1", single(exercise("$id-1", "1", first, group())), single(exercise("$id-2", "2", second, group())))

    // "OHP" in both weeks, spelt with a trailing space the first time.
    private val plan = months(
        week("w1", "2026-05-25", two("M/T1", "OHP ", "Przysiad")),
        week("w2", "2026-06-01", two("J/T1", "OHP", "Front Squat")),
    )
    private val settings = FakeSettingsRepository()

    private fun TestScope.editor(): AnalysisGroupsViewModel {
        val viewModel = AnalysisGroupsViewModel(FakeWorkoutPlanRepository(plan), settings)
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun AnalysisGroupsViewModel.ready(): AnalysisGroupsUiState = (state.value as Loadable.Ready).value

    private suspend fun groups() = settings.getSettings().analysisGroups

    @Test
    fun startsFromTheDefaults() = runTest(main.dispatcher) {
        val state = editor().ready()

        assertEquals(listOf("Squat", "Bench", "Deadlift"), state.groups.map { it.name })
        assertEquals(DefaultExerciseGroups[0].exercises, state.groups[0].exercises)
        assertTrue(state.isDefault)
    }

    @Test
    fun addingRenamingMovingAndRemovingGroups() = runTest(main.dispatcher) {
        val viewModel = editor()

        viewModel.onEvent(AnalysisGroupsEvent.AddGroup(" OHP "))
        // Taken, ignoring case: nothing happens.
        viewModel.onEvent(AnalysisGroupsEvent.AddGroup("squat"))
        viewModel.onEvent(AnalysisGroupsEvent.RenameGroup("Bench", "Ławka"))
        viewModel.onEvent(AnalysisGroupsEvent.MoveGroup("OHP", -1))
        viewModel.onEvent(AnalysisGroupsEvent.RemoveGroup("Deadlift"))

        assertEquals(listOf("Squat", "Ławka", "OHP"), groups().map { it.name })
        assertEquals(DefaultExerciseGroups[1].exercises, groups()[1].exercises)
        assertFalse(viewModel.ready().isDefault)

        viewModel.onEvent(AnalysisGroupsEvent.ResetToDefaults)
        assertEquals(DefaultExerciseGroups, groups())
    }

    @Test
    fun typingOffersSheetExercisesNotInTheGroupYet() = runTest(main.dispatcher) {
        val viewModel = editor()

        viewModel.onEvent(AnalysisGroupsEvent.InputChanged("Squat", "squat"))
        assertEquals(listOf("Front Squat"), viewModel.ready().groups[0].suggestions)

        viewModel.onEvent(AnalysisGroupsEvent.InputChanged("Bench", "o"))
        // Each exercise once, as spelt most recently; those starting with the text first.
        assertEquals(listOf("OHP", "Front Squat"), viewModel.ready().groups[1].suggestions)

        viewModel.onEvent(AnalysisGroupsEvent.PickSuggestion("Squat", "Front Squat"))
        assertEquals("Front Squat", groups()[0].exercises.last())
        assertEquals("", viewModel.ready().groups[0].input)
    }

    @Test
    fun anExerciseCountsTowardOneGroup() = runTest(main.dispatcher) {
        settings.update { it.copy(analysisGroups = listOf(ExerciseGroup("A", listOf("OHP")), ExerciseGroup("B", emptyList()))) }
        val viewModel = editor()

        viewModel.onEvent(AnalysisGroupsEvent.InputChanged("B", "  ohp "))
        viewModel.onEvent(AnalysisGroupsEvent.AddExercise("B"))

        assertEquals(listOf(ExerciseGroup("A", emptyList()), ExerciseGroup("B", listOf("ohp"))), groups())

        // Already there, spelt another way: kept as it was.
        viewModel.onEvent(AnalysisGroupsEvent.PickSuggestion("B", "OHP"))
        assertEquals(listOf("ohp"), groups()[1].exercises)

        viewModel.onEvent(AnalysisGroupsEvent.RemoveExercise("B", "ohp"))
        assertEquals(emptyList<String>(), groups()[1].exercises)
    }
}
