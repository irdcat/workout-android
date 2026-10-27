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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Card
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import kotlin.math.abs

// ---- UI state ------------------------------------------------------------

@Immutable
data class WorkoutSummaryUiState(
    /** e.g. "T2". */
    val workoutName: String,
    /** e.g. "Tue 2 Jun · 17:42 – 18:41". */
    val dateLine: String,
    /** null when "measure workout time" is off; the time card is then hidden. */
    val totalSeconds: Int?,
    val workingSeconds: Int,
    val restingSeconds: Int,
    /** Duration of the same workout last week; null when there is none. */
    val previousSeconds: Int? = null,
    /** null until rests are timed in the app. */
    val avgRestSeconds: Int?,
    val exercisesDone: Int,
    val exercisesTotal: Int,
    val setsDone: Int,
    val setsTotal: Int,
    val results: List<SummaryResult>,
    /** Sheet the results go to, e.g. "CZERWIEC". */
    val sheetName: String,
    /** Mirrors the "Write results to the sheet" setting. */
    val writesToSheet: Boolean = true,
)

@Immutable
data class SummaryResult(
    val exerciseId: String,
    /** Superset members carry their tag, e.g. "5a Biceps młotkowo". */
    val label: String,
    val result: String?,
    val setsDone: Int,
    val setsTotal: Int,
)

// ---- Route ---------------------------------------------------------------

/** Summary backed by [WorkoutSummaryViewModel]; only navigation leaves this composable. */
@Composable
fun WorkoutSummaryRoute(
    onSaved: () -> Unit,
    onBackToSession: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            WorkoutSummaryNavigation.Saved -> onSaved()
        }
    }
    when (val summary = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> WorkoutSummaryScreen(
            state = summary.value,
            onEditResult = { editingExerciseId = it },
            onSave = viewModel::onSave,
            onBackToSession = onBackToSession,
        )
    }
    editingExerciseId?.let { id ->
        ResultEditorRoute(exerciseId = id, onClose = { editingExerciseId = null })
    }
}

// ---- Screen --------------------------------------------------------------

@Composable
fun WorkoutSummaryScreen(
    state: WorkoutSummaryUiState,
    onEditResult: (exerciseId: String) -> Unit,
    onSave: () -> Unit,
    onBackToSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val complete = state.setsDone >= state.setsTotal
    Surface(modifier = modifier.fillMaxSize(), color = Colors.Canvas) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(state = state, complete = complete)
                state.totalSeconds?.let { TimeCard(state = state, totalSeconds = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Exercises", "${state.exercisesDone}", " / ${state.exercisesTotal}", Modifier.weight(1f))
                    StatTile("Sets", "${state.setsDone}", " / ${state.setsTotal}", Modifier.weight(1f))
                    StatTile("Avg rest", state.avgRestSeconds?.let(::formatClock) ?: "—", null, Modifier.weight(1f))
                }
                ResultsSection(state = state, onEditResult = onEditResult)
                WriteBackNote(state = state)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = Shapes.xl,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.Primary,
                        contentColor = Colors.OnPrimary,
                    ),
                ) {
                    Text(
                        text = "Save results",
                        style = KineticType.buttonLabelLg,
                    )
                }
                Button(
                    onClick = onBackToSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = Shapes.xl,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.Surface2,
                        contentColor = Colors.TextPrimary,
                    ),
                ) {
                    Text(
                        text = "Back to session",
                        style = KineticType.rowTitle,
                    )
                }
            }
        }
    }
}

// ---- Header & time -------------------------------------------------------

@Composable
private fun Header(state: WorkoutSummaryUiState, complete: Boolean) {
    Column {
        val accent = if (complete) Colors.Primary else Colors.Tertiary
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (complete) Icons.Filled.Check else Icons.Outlined.Flag,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (complete) "WORKOUT COMPLETE" else "ENDED EARLY",
                style = KineticType.labelCaps.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
        Text(
            text = state.workoutName.uppercase(),
            modifier = Modifier.padding(top = 4.dp),
            style = KineticType.screenTitle,
            color = Colors.TextPrimary,
        )
        Text(
            text = state.dateLine,
            modifier = Modifier.padding(top = 2.dp),
            style = KineticType.bodyMd,
            color = Colors.TextSecondary,
        )
    }
}

@Composable
private fun TimeCard(state: WorkoutSummaryUiState, totalSeconds: Int) {
    Card(border = BorderStroke(1.dp, Colors.Primary.copy(alpha = 0.25f))) {
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label("Time spent")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = formatClock(totalSeconds),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = spokenDuration(totalSeconds)
                    },
                    style = KineticType.dataDisplayXl.copy(fontSize = 88.sp, lineHeight = 80.sp),
                    color = Colors.Primary,
                )
                state.previousSeconds?.let { previous ->
                    Text(
                        text = comparison(totalSeconds, previous, state.workoutName),
                        style = KineticType.bodyNote,
                        color = Colors.TextSecondary,
                    )
                }
            }
            val working = state.workingSeconds.coerceAtLeast(0)
            val resting = state.restingSeconds.coerceAtLeast(0)
            if (working + resting > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(Shapes.pill),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (working > 0) {
                        Box(
                            Modifier
                                .weight(working.toFloat())
                                .fillMaxSize()
                                .background(Colors.Primary),
                        )
                    }
                    if (resting > 0) {
                        Box(
                            Modifier
                                .weight(resting.toFloat())
                                .fillMaxSize()
                                .background(Colors.Surface3),
                        )
                    }
                }
            }
            // Hidden when no rest was timed in the app, so the split isn't known.
            if (working + resting > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TimeLegend(color = Colors.Primary, value = formatClock(working), label = "working")
                    TimeLegend(color = Colors.Surface3, value = formatClock(resting), label = "resting")
                }
            }
        }
    }
}

@Composable
private fun TimeLegend(color: Color, value: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            text = value,
            style = KineticType.dataValue.copy(fontSize = 19.sp),
            color = Colors.TextPrimary,
        )
        Text(text = label, style = KineticType.bodyNote, color = Colors.TextSecondary)
    }
}

@Composable
private fun StatTile(label: String, value: String, suffix: String?, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Label(label)
            Text(
                text = buildAnnotatedString {
                    append(value)
                    suffix?.let {
                        withStyle(SpanStyle(fontSize = 17.sp, color = Colors.TextSecondary, fontWeight = FontWeight.SemiBold)) {
                            append(it)
                        }
                    }
                },
                style = KineticType.dataDisplayLg.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                color = Colors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

// ---- Results -------------------------------------------------------------

@Composable
private fun ResultsSection(state: WorkoutSummaryUiState, onEditResult: (String) -> Unit) {
    Column {
        Label(
            text = if (state.writesToSheet) "Results · written to the WYNIK column" else "Results",
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        )
        Card {
            Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                state.results.forEachIndexed { index, result ->
                    if (index > 0) HorizontalDivider(color = Colors.Border)
                    ResultRow(result = result, onEdit = { onEditResult(result.exerciseId) })
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: SummaryResult, onEdit: () -> Unit) {
    val allSets = result.setsDone >= result.setsTotal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = if (allSets && result.result != null) Colors.Primary else Colors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.label,
                style = KineticType.rowTitle,
                color = Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.result ?: "No result yet",
                style = KineticType.bodySm,
                color = if (result.result != null) Colors.TextPrimary else Colors.TextSecondary,
            )
        }
        Text(
            text = "${result.setsDone}/${result.setsTotal}",
            style = KineticType.dataValue.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
            color = if (allSets) Colors.TextPrimary else Colors.Tertiary,
        )
        if (result.result != null) {
            IconButton(
                onClick = onEdit,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Colors.TextSecondary),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit result for ${result.label}",
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .padding(end = 4.dp),
                shape = Shapes.xl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Surface2,
                    contentColor = Colors.Primary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = "Add result",
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clearAndSetSemantics { contentDescription = "Add result for ${result.label}" },
                    style = KineticType.bodyNoteStrong,
                )
            }
        }
    }
}

@Composable
private fun WriteBackNote(state: WorkoutSummaryUiState) {
    val withResult = state.results.count { it.result != null }
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (state.writesToSheet) Icons.Outlined.CloudUpload else Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = if (state.writesToSheet) Colors.Primary else Colors.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Colors.TextPrimary, fontWeight = FontWeight.Bold)) {
                        append(
                            when {
                                !state.writesToSheet -> "Results stay on this device."
                                withResult == 0 -> "No results to write yet."
                                withResult == 1 -> "1 result will be written to ${state.sheetName}."
                                else -> "$withResult results will be written to ${state.sheetName}."
                            }
                        )
                    }
                    append(
                        if (state.writesToSheet) {
                            " If you're offline they sync later."
                        } else {
                            " Writing to the sheet is turned off in Settings."
                        }
                    )
                },
                style = KineticType.bodyNote,
                color = Colors.TextSecondary,
            )
        }
    }
}

// ---- Formatting ----------------------------------------------------------

/** e.g. "4 min shorter than last week's T2 (1:02:31)". */
private fun comparison(total: Int, previous: Int, workoutName: String): String {
    val minutes = abs(total - previous) / 60
    val reference = "last week's $workoutName (${formatClock(previous)})"
    return when {
        minutes == 0 -> "About the same as $reference"
        total < previous -> "$minutes min shorter than $reference"
        else -> "$minutes min longer than $reference"
    }
}

// ---- Previews ------------------------------------------------------------

private val previewResults = listOf(
    SummaryResult("t2-1", "Half Tucked Front Lever Hold", "10 s, 15 s, 15 s, 15 s", 4, 4),
    SummaryResult("t2-2", "RDL", "RPE 9", 5, 5),
    SummaryResult("t2-3", "Podciąganie najsilniejszy chwyt", "W ostatniej serii ostatnie nie weszło", 3, 3),
    SummaryResult("t2-4", "Żurawie", "RPE 9.5", 3, 3),
    SummaryResult("t2-5a", "5a Biceps młotkowo", "11, 11, 10 repów", 3, 3),
    SummaryResult("t2-5b", "5b Reverse Flies Hantlami", "RPE 7.5 - 8", 2, 2),
    SummaryResult("t2-5c", "5c Wycieraczki", null, 2, 2),
)

private val previewComplete = WorkoutSummaryUiState(
    workoutName = "T2",
    dateLine = "Tue 2 Jun · 17:42 – 18:41",
    totalSeconds = 58 * 60 + 24,
    workingSeconds = 27 * 60 + 4,
    restingSeconds = 31 * 60 + 20,
    previousSeconds = 3600 + 2 * 60 + 31,
    avgRestSeconds = 86,
    exercisesDone = 7,
    exercisesTotal = 7,
    setsDone = 22,
    setsTotal = 22,
    results = previewResults,
    sheetName = "CZERWIEC",
)

@Composable
private fun SummaryPreviewContent(state: WorkoutSummaryUiState) {
    WorkoutTheme {
        WorkoutSummaryScreen(state = state, onEditResult = {}, onSave = {}, onBackToSession = {})
    }
}

@Preview(name = "Complete", widthDp = 390, heightDp = 1240, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WorkoutSummaryCompletePreview() {
    SummaryPreviewContent(previewComplete)
}

@Preview(name = "Ended early", widthDp = 390, heightDp = 1240, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WorkoutSummaryEndedEarlyPreview() {
    SummaryPreviewContent(
        previewComplete.copy(
            dateLine = "Tue 2 Jun · 17:42 – 18:20",
            totalSeconds = 38 * 60 + 5,
            workingSeconds = 17 * 60 + 40,
            restingSeconds = 20 * 60 + 25,
            exercisesDone = 4,
            setsDone = 14,
            results = previewResults.take(4) + listOf(
                SummaryResult("t2-5a", "5a Biceps młotkowo", null, 1, 3),
                SummaryResult("t2-5b", "5b Reverse Flies Hantlami", null, 0, 2),
                SummaryResult("t2-5c", "5c Wycieraczki", null, 0, 2),
            ),
        )
    )
}

@Preview(name = "Sheet writing off, first time", widthDp = 390, heightDp = 1240, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WorkoutSummaryLocalOnlyPreview() {
    SummaryPreviewContent(
        previewComplete.copy(
            previousSeconds = null,
            writesToSheet = false,
        )
    )
}
