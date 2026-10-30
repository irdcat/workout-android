package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import com.github.irdcat.workout.domain.strength.Sex
import com.github.irdcat.workout.domain.strength.StrengthScores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

/** Bodyweight steps finer than the total's: a few hundred grams already move the score. */
private const val BODYWEIGHT_STEP = "0.5"
private const val TOTAL_STEP = "2.5"

/** DOTS and both Wilks scores for what's entered; nothing is saved, the tab keeps it while the app runs. */
@HiltViewModel
class ScoresViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ScoresUiState())
    val state: StateFlow<ScoresUiState> = _state.asStateFlow()

    fun onEvent(event: ScoresEvent) {
        when (event) {
            is ScoresEvent.SexSelected -> update { it.copy(sex = event.sex) }
            is ScoresEvent.BodyweightChanged -> update { it.copy(bodyweight = event.value.filterDecimal()) }
            is ScoresEvent.AdjustBodyweight -> update { it.copy(bodyweight = stepWeight(it.bodyweight, BODYWEIGHT_STEP, event.direction)) }
            is ScoresEvent.TotalChanged -> update { it.copy(total = event.value.filterDecimal()) }
            is ScoresEvent.AdjustTotal -> update { it.copy(total = stepWeight(it.total, TOTAL_STEP, event.direction)) }
        }
    }

    private fun update(transform: (ScoresUiState) -> ScoresUiState) {
        _state.update { transform(it).withScores() }
    }
}

/** Scores for the entered numbers; none until both are above zero. */
internal fun ScoresUiState.withScores(): ScoresUiState {
    val bodyweightKg = bodyweight.toDoubleOrNull()?.takeIf { it > 0 }
    val totalKg = total.toDoubleOrNull()?.takeIf { it > 0 }
    if (bodyweightKg == null || totalKg == null) return copy(dots = null, wilks = null, wilks2020 = null)
    return copy(
        dots = StrengthScores.dots(sex, bodyweightKg, totalKg).score(),
        wilks = StrengthScores.wilks(sex, bodyweightKg, totalKg).score(),
        wilks2020 = StrengthScores.wilks2020(sex, bodyweightKg, totalKg).score(),
    )
}

private fun Double.score(): String = String.format(Locale.ROOT, "%.2f", this)
