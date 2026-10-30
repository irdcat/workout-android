package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Card
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.MainNavigationBar
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.components.SegmentedControl
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import com.github.irdcat.workout.domain.strength.Sex

// ---- UI state ------------------------------------------------------------

@Immutable
data class ScoresUiState(
    val sex: Sex = Sex.Male,
    /** kg, as typed. */
    val bodyweight: String = "",
    /** Squat + bench + deadlift in kg, as typed. */
    val total: String = "",
    /** e.g. "452.62"; null until both numbers are entered. */
    val dots: String? = null,
    /** The original (1994) formula. */
    val wilks: String? = null,
    val wilks2020: String? = null,
)

sealed interface ScoresEvent {
    data class SexSelected(val sex: Sex) : ScoresEvent
    data class BodyweightChanged(val value: String) : ScoresEvent
    /** [direction] is -1 or +1. */
    data class AdjustBodyweight(val direction: Int) : ScoresEvent
    data class TotalChanged(val value: String) : ScoresEvent
    /** [direction] is -1 or +1. */
    data class AdjustTotal(val direction: Int) : ScoresEvent
}

private val Sex.label: String
    get() = when (this) {
        Sex.Male -> "Male"
        Sex.Female -> "Female"
    }

// ---- Route ---------------------------------------------------------------

/** Scores backed by [ScoresViewModel]; only tab switches leave this composable. */
@Composable
fun ScoresRoute(
    onTabSelected: (MainTab) -> Unit,
    viewModel: ScoresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScoresScreen(state = state, onEvent = viewModel::onEvent, onTabSelected = onTabSelected)
}

// ---- Screen --------------------------------------------------------------

@Composable
fun ScoresScreen(
    state: ScoresUiState,
    onEvent: (ScoresEvent) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Colors.Canvas,
        bottomBar = { MainNavigationBar(selected = MainTab.Scores, onSelect = onTabSelected) },
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
                Label("DOTS and Wilks")
                Text(text = "SCORES", style = TextStyles.screenTitle, color = Colors.TextPrimary)
            }
            // Three side by side don't fit a phone at this size.
            ScoreCard(name = "DOTS", value = state.dots, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScoreCard(name = "Wilks", value = state.wilks, modifier = Modifier.weight(1f))
                ScoreCard(name = "Wilks 2020", value = state.wilks2020, modifier = Modifier.weight(1f))
            }
            SegmentedControl(
                options = Sex.entries,
                selected = state.sex,
                label = { it.label },
                description = "Sex",
                onSelect = { onEvent(ScoresEvent.SexSelected(it)) },
            )
            WideStepper(
                label = "Bodyweight · kg",
                what = "bodyweight",
                value = state.bodyweight,
                onValueChange = { onEvent(ScoresEvent.BodyweightChanged(it)) },
                onAdjust = { onEvent(ScoresEvent.AdjustBodyweight(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            WideStepper(
                label = "Total · kg",
                what = "total",
                value = state.total,
                onValueChange = { onEvent(ScoresEvent.TotalChanged(it)) },
                onAdjust = { onEvent(ScoresEvent.AdjustTotal(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            Text(
                text = "Total is your best squat, bench press and deadlift added up. " +
                    "Wilks is the original formula; Wilks 2020 is its revision, which scores about a fifth higher.",
                style = TextStyles.bodySm,
                color = Colors.TextSecondary,
            )
        }
    }
}

@Composable
private fun ScoreCard(name: String, value: String?, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clearAndSetSemantics { contentDescription = "$name ${value ?: "not available"}" },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Label(name)
            Text(
                text = value ?: "—",
                style = TextStyles.dataDisplayLg,
                color = if (value != null) Colors.TextPrimary else Colors.TextSecondary,
            )
        }
    }
}

// ---- Previews ------------------------------------------------------------

@Preview(name = "Scores", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun ScoresPreview() {
    WorkoutTheme {
        ScoresScreen(
            state = ScoresUiState(bodyweight = "90", total = "700").withScores(),
            onEvent = {},
            onTabSelected = {},
        )
    }
}
