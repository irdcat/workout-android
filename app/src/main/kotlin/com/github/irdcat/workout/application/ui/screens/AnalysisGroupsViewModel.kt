package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.analysis.DefaultExerciseGroups
import com.github.irdcat.workout.domain.analysis.ExerciseGroup
import com.github.irdcat.workout.domain.analysis.exerciseKey
import com.github.irdcat.workout.domain.analysis.isFreeGroupName
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_SUGGESTIONS = 5

/**
 * The exercise groups Analysis compares, edited in place: every change is saved to the
 * settings straight away. Exercise names from the cached plan are offered while typing.
 */
@HiltViewModel
class AnalysisGroupsViewModel @Inject constructor(
    planRepository: WorkoutPlanRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** What's typed in each group's field, by group name. */
    private val inputs = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Every exercise in the plan once, as the sheet spells it most recently. */
    private val sheetExercises = planRepository.observeMonths().map { months ->
        months.flatMap { it.weeks }.sortedBy { it.start }
            .flatMap { week -> week.workouts.flatMap { it.exercises } }
            .associateBy({ exerciseKey(it.name) }, { it.name.trim() })
            .values.sorted()
    }

    val state: StateFlow<Loadable<AnalysisGroupsUiState>> = combine(
        settingsRepository.settings,
        sheetExercises,
        inputs,
    ) { settings, exercises, inputs ->
        val groups = settings.analysisGroups
        Loadable.Ready(
            AnalysisGroupsUiState(
                groups = groups.map { group ->
                    val input = inputs[group.name].orEmpty()
                    GroupEditor(group.name, group.exercises, input, suggestions(exercises, input, group))
                },
                isDefault = groups == DefaultExerciseGroups,
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun onEvent(event: AnalysisGroupsEvent) {
        when (event) {
            is AnalysisGroupsEvent.InputChanged -> inputs.update { it + (event.group to event.value) }
            is AnalysisGroupsEvent.AddExercise -> addExercise(event.group, inputs.value[event.group].orEmpty())
            is AnalysisGroupsEvent.PickSuggestion -> addExercise(event.group, event.exercise)
            AnalysisGroupsEvent.ResetToDefaults -> {
                updateGroups { DefaultExerciseGroups }
                inputs.value = emptyMap()
            }
            AnalysisGroupsEvent.Back -> Unit // Navigation; handled by the route.
            else -> {
                updateGroups { it.edited(event) }
                // A renamed or removed group's typed text goes with it.
                if (event is AnalysisGroupsEvent.RenameGroup) inputs.update { it - event.name }
                if (event is AnalysisGroupsEvent.RemoveGroup) inputs.update { it - event.name }
            }
        }
    }

    /** The groups after a change to the list itself, or to a group's exercises; unchanged when it can't be made. */
    private fun List<ExerciseGroup>.edited(event: AnalysisGroupsEvent): List<ExerciseGroup> {
        val names = map { it.name }
        return when (event) {
            is AnalysisGroupsEvent.AddGroup ->
                if (isFreeGroupName(names, event.name)) this + ExerciseGroup(event.name.trim(), emptyList()) else this
            is AnalysisGroupsEvent.RenameGroup ->
                if (isFreeGroupName(names, event.newName, renaming = event.name)) {
                    map { if (it.name == event.name) it.copy(name = event.newName.trim()) else it }
                } else {
                    this
                }
            is AnalysisGroupsEvent.RemoveGroup -> filterNot { it.name == event.name }
            is AnalysisGroupsEvent.MoveGroup -> {
                val from = names.indexOf(event.name)
                val to = from + event.direction
                if (from < 0 || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
            }
            is AnalysisGroupsEvent.RemoveExercise ->
                map { if (it.name == event.group) it.copy(exercises = it.exercises - event.exercise) else it }
            else -> this
        }
    }

    /** Adds [exercise] to [group], taking it out of any other: an exercise counts toward one group. */
    private fun addExercise(group: String, exercise: String) {
        val name = exercise.trim()
        if (name.isEmpty()) return
        val key = exerciseKey(name)
        updateGroups { groups ->
            groups.map { candidate ->
                val others = candidate.exercises.filterNot { exerciseKey(it) == key }
                when {
                    candidate.name != group -> candidate.copy(exercises = others)
                    // Already in it, perhaps spelt differently: kept as it was.
                    others.size < candidate.exercises.size -> candidate
                    else -> candidate.copy(exercises = candidate.exercises + name)
                }
            }
        }
        inputs.update { it - group }
    }

    private fun updateGroups(transform: (List<ExerciseGroup>) -> List<ExerciseGroup>) {
        viewModelScope.launch { settingsRepository.update { it.copy(analysisGroups = transform(it.analysisGroups)) } }
    }

    /** Sheet exercises containing [input] and not in [group] yet, those starting with it first. */
    private fun suggestions(exercises: List<String>, input: String, group: ExerciseGroup): List<String> {
        val needle = input.trim()
        if (needle.isEmpty()) return emptyList()
        val taken = group.exercises.map(::exerciseKey).toSet()
        return exercises
            .filter { it.contains(needle, ignoreCase = true) && exerciseKey(it) !in taken }
            .sortedBy { !it.startsWith(needle, ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
    }
}
