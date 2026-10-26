package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.readyValue
import com.github.irdcat.workout.application.ui.components.updateReady
import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.resultTarget
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.infrastructure.coroutines.ApplicationScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.github.irdcat.workout.domain.workout.SetGroup as PlanSetGroup

private val LastResultDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Edits the WYNIK text of one exercise. The result is saved on the phone straight away;
 * it's written to the sheet with the next sync, or right away when edited outside a session.
 *
 * To [review] it before the exercise is finished, it starts from the sets logged (the
 * result the app would write anyway) when there's none yet, and saving finishes the exercise.
 */
@HiltViewModel(assistedFactory = ResultEditorViewModel.Factory::class)
class ResultEditorViewModel @AssistedInject constructor(
    @Assisted private val exerciseId: String,
    @Assisted private val review: Boolean,
    private val planRepository: WorkoutPlanRepository,
    private val sessionRepository: ActiveSessionRepository,
    private val logRepository: SessionLogRepository,
    private val resultRepository: ResultRepository,
    private val findLastTime: FindLastTime,
    private val settingsRepository: SettingsRepository,
    private val syncWorkoutPlan: SyncWorkoutPlan,
    private val clock: Clock,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(exerciseId: String, review: Boolean): ResultEditorViewModel
    }

    private val _state = MutableStateFlow<Loadable<ResultEditorUiState>>(Loadable.Loading)
    val state: StateFlow<Loadable<ResultEditorUiState>> = _state.asStateFlow()

    /** Emits once the sheet should close: true when the result was saved. */
    private val _closed = Channel<Boolean>(Channel.BUFFERED)
    val closed: Flow<Boolean> = _closed.receiveAsFlow()

    /** The exercise being edited and its workout, once loaded. */
    private var editing: Pair<Workout, Exercise>? = null

    init {
        viewModelScope.launch {
            val workout = planRepository.observeMonths().first()
                .asSequence().flatMap { it.weeks }.flatMap { it.workouts }
                .firstOrNull { workout -> workout.exercises.any { it.id == exerciseId } }
            val exercise = workout?.exercises?.first { it.id == exerciseId }
            if (workout == null || exercise == null) {
                // Gone from the plan, e.g. a sync removed it: nothing to edit.
                _closed.send(false)
                return@launch
            }
            editing = workout to exercise
            _state.value = Loadable.Ready(editorState(workout, exercise))
        }
    }

    fun onEvent(event: ResultEditorEvent) {
        when (event) {
            is ResultEditorEvent.TextChanged -> _state.updateReady { it.copy(text = event.value) }
            is ResultEditorEvent.TargetSelected -> _state.updateReady { it.copy(selectedTarget = event.index) }
            is ResultEditorEvent.Insert -> _state.updateReady { it.copy(text = appendSnippet(it.text, event.snippet)) }
            ResultEditorEvent.UseLastTime -> _state.updateReady { it.copy(text = it.lastTime?.text ?: it.text) }
            ResultEditorEvent.Clear -> _state.updateReady { it.copy(text = "") }
            ResultEditorEvent.Save -> save()
            ResultEditorEvent.Dismiss -> viewModelScope.launch { _closed.send(false) }
        }
    }

    private fun save() {
        val editor = _state.readyValue() ?: return
        val (workout, exercise) = editing ?: return
        val group = exercise.setGroups.getOrNull(editor.selectedTarget) ?: exercise.setGroups.last()
        viewModelScope.launch {
            resultRepository.save(SavedResult(workout.resultTarget(exercise, group), editor.text.trim(), clock.instant()))
            // During a session results go to the sheet when it's saved; otherwise now.
            val session = sessionRepository.getActiveSession()?.takeIf { it.workoutId == workout.id }
            if (session != null && review) logRepository.finishExercises(session, listOf(exercise.id))
            if (session == null && settingsRepository.getSettings().writeResultsToSheet) {
                applicationScope.launch { syncWorkoutPlan() }
            }
            _closed.send(true)
        }
    }

    private suspend fun editorState(workout: Workout, exercise: Exercise): ResultEditorUiState {
        val groups = exercise.setGroups
        // The row that already has a result, or the last (working or back-off sets).
        val selected = groups.indexOfLast { it.result != null }.takeIf { it >= 0 } ?: groups.lastIndex
        val session = sessionRepository.getActiveSession()?.takeIf { it.workoutId == workout.id }
        val logged = session?.let { logRepository.observe(it).first().setsOf(exercise.id) }.orEmpty().filterNot { it.warmUp }
        val lastTime = findLastTime(workout.id)
        return ResultEditorUiState(
            exerciseLabel = exercise.number.toString(),
            exerciseName = exercise.name,
            targets = if (groups.size > 1) groups.mapIndexed { index, group -> "${rowName(index, groups.size)} · ${group.label()}" } else emptyList(),
            selectedTarget = selected,
            text = groups.getOrNull(selected)?.result
                ?: logged.takeIf { review }?.joinToString(", ") { it.chipLabel(exercise.repsUnitOf(it.setGroupId)) }.orEmpty(),
            suggestions = suggestions(logged, exercise),
            review = review,
            lastTime = lastTime?.resultLike(exercise)?.let { text ->
                // The sheet doesn't keep dates: one is known only when last time was trained in the app.
                LastResult(date = lastTime.session?.session?.startedAt?.atZone(clock.zone)?.format(LastResultDate), text = text)
            },
        )
    }

    private fun rowName(index: Int, count: Int): String = when (index) {
        0 -> "First row"
        count - 1 -> "Last row"
        else -> "Row ${index + 1}"
    }

    /** "2×5 92.5 kg", "x×5 RPE 8" for open sets, "∞×10" for unlimited ones. */
    private fun PlanSetGroup.label(): String {
        val sets = when (val sets = sets) {
            is SetCount.Fixed -> sets.count.toString()
            SetCount.Unlimited -> "∞"
            else -> "x"
        }
        return "$sets×${reps.label()} ${load.label()}".trim()
    }

    /** Snippets from this session's sets: all of them, the heaviest, and the last RPE. */
    private fun suggestions(sets: List<LoggedSet>, exercise: Exercise): List<String> = listOfNotNull(
        sets.joinToString(", ") { it.chipLabel(exercise.repsUnitOf(it.setGroupId)) }.ifEmpty { null },
        sets.filter { it.addedWeightKg != null }.maxByOrNull { it.addedWeightKg!! }
            ?.let { "top ${it.chipLabel(exercise.repsUnitOf(it.setGroupId))}" }.takeIf { sets.size > 1 },
        sets.lastOrNull { it.rpe != null }?.rpe?.let { "RPE ${it.kg()}" },
    ).distinct()

    private fun appendSnippet(text: String, snippet: String): String =
        if (text.isBlank()) snippet else "${text.trimEnd()}, $snippet"
}
