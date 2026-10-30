package com.github.irdcat.workout.application.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Card
import com.github.irdcat.workout.application.ui.components.Chip
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.SecondaryButton
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.application.ui.components.TempoBadge
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// ---- UI state ------------------------------------------------------------

@Immutable
data class RestTimerUiState(
    /** e.g. "T2". */
    val workoutName: String,
    /** Session clock, e.g. "14:51"; null when "measure workout time" is off. */
    val elapsed: String?,
    /** Negative once the rest has run over. */
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val paused: Boolean = false,
    /** The length is the sheet's rest, as planned. */
    val fromSheet: Boolean = false,
    /** Lengths to restart with (working sets) or pick (warm-ups). */
    val presets: List<RestPreset> = emptyList(),
    val lastSet: LoggedSetInfo,
    val upNext: UpNext?,
    /** Set when resting after a warm-up, whose rest is the user's own. */
    val warmUp: WarmUpRest? = null,
)

@Immutable
data class RestPreset(
    val seconds: Int,
    /** The rest's length right now. */
    val selected: Boolean,
    /** The sheet's rest for the set. */
    val fromSheet: Boolean = false,
)

@Immutable
data class WarmUpRest(
    val exerciseName: String,
    /** A time picked here is saved for every warm-up of the exercise. */
    val useForAll: Boolean,
)

@Immutable
data class LoggedSetInfo(
    /** e.g. "Set 3", "Warm-up 2" or "5b · round 2". */
    val label: String,
    /** e.g. "90 kg × 5". */
    val summary: String,
)

@Immutable
data class UpNext(
    val exerciseName: String,
    /** e.g. "Set 4" or "5c · round 2". */
    val label: String,
    /** Targets, e.g. ["5 reps", "92.5 kg"]. */
    val targets: List<String>,
    val tempo: Tempo? = null,
    /** Planned sets, for "SET 4 of 5"; null for open sets and supersets. */
    val setCount: Int? = null,
    /** e.g. "5 reps × 92.5 kg". */
    val target: String = targets.joinToString(" · "),
)

sealed interface RestTimerEvent {
    data class Adjust(val seconds: Int) : RestTimerEvent
    data object TogglePause : RestTimerEvent
    /** Start this rest again from now, [seconds] long. */
    data class Restart(val seconds: Int) : RestTimerEvent
    /** Make this rest [seconds] long, keeping the time already rested. */
    data class SetLength(val seconds: Int) : RestTimerEvent
    data class UseForAllWarmUps(val enabled: Boolean) : RestTimerEvent
    data object Skip : RestTimerEvent
    data object Undo : RestTimerEvent
    data object AnotherWarmUp : RestTimerEvent
    data object Back : RestTimerEvent
    /** Leave for Home; the rest keeps running. */
    data object Home : RestTimerEvent
}

// ---- Route ---------------------------------------------------------------

/** Rest timer backed by [RestTimerViewModel]; only navigation leaves this composable. */
@Composable
fun RestTimerRoute(
    onDone: () -> Unit,
    onStartWorkingSets: () -> Unit,
    onHome: () -> Unit,
    viewModel: RestTimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back ends the rest like Skip does, so its length is recorded and the alarm cancelled.
    BackHandler { viewModel.onEvent(RestTimerEvent.Back) }
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            // Back to the screen that logged the set; it decides which set is next.
            RestTimerNavigation.Done, RestTimerNavigation.SetUndone -> onDone()
            RestTimerNavigation.StartWorkingSets -> onStartWorkingSets()
            RestTimerNavigation.Home -> onHome()
        }
    }
    when (val rest = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> RestTimerScreen(state = rest.value, onEvent = viewModel::onEvent)
    }
}

// ---- Screen --------------------------------------------------------------

@Composable
fun RestTimerScreen(
    state: RestTimerUiState,
    onEvent: (RestTimerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Colors.Canvas) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RestTopBar(state = state, onEvent = onEvent)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RestRing(state = state, onEvent = onEvent)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AdjustButton(label = "− 15 s", description = "Subtract 15 seconds") { onEvent(RestTimerEvent.Adjust(-15)) }
                        PauseButton(paused = state.paused) { onEvent(RestTimerEvent.TogglePause) }
                        AdjustButton(label = "+ 15 s", description = "Add 15 seconds") { onEvent(RestTimerEvent.Adjust(+15)) }
                    }
                }
                when (val warmUp = state.warmUp) {
                    null -> {
                        if (state.presets.isNotEmpty()) {
                            Presets(title = "Restart with", presets = state.presets) { onEvent(RestTimerEvent.Restart(it)) }
                        }
                        state.upNext?.let { UpNextCard(upNext = it, lastSet = state.lastSet) }
                    }
                    else -> {
                        WarmUpRestCard(warmUp = warmUp, presets = state.presets, onEvent = onEvent)
                        state.upNext?.let { WorkingSetUpNext(it) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(RestTimerEvent.Skip) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = Shapes.xl,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.Primary,
                        contentColor = Colors.OnPrimary,
                    ),
                ) {
                    Icon(imageVector = Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            state.upNext == null -> "End rest"
                            state.remainingSeconds <= 0 -> "Start ${state.upNext.label.lowercase()}"
                            else -> "Skip rest · start ${state.upNext.label.lowercase()}"
                        },
                        style = TextStyles.buttonLabelLg,
                    )
                }
                if (state.warmUp != null) {
                    SecondaryButton(
                        text = "Another warm-up",
                        onClick = { onEvent(RestTimerEvent.AnotherWarmUp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Back to Home (the timer keeps running), what the rest follows, and undoing that set. */
@Composable
private fun RestTopBar(state: RestTimerUiState, onEvent: (RestTimerEvent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBarIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Back to home, the timer keeps running",
            onClick = { onEvent(RestTimerEvent.Home) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "REST · AFTER ${state.lastSet.label.uppercase()}",
                style = TextStyles.labelCaps,
                color = Colors.TextSecondary,
            )
            Text(
                text = listOfNotNull(state.workoutName, state.elapsed).joinToString(" · "),
                style = TextStyles.dataValue.copy(fontWeight = FontWeight.SemiBold),
                color = Colors.TextSecondary,
            )
        }
        TopBarIconButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            description = "Undo ${state.lastSet.label.replaceFirstChar { it.lowercase() }}",
            onClick = { onEvent(RestTimerEvent.Undo) },
        )
    }
}

@Composable
private fun TopBarIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = Shapes.xl,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Colors.Surface2,
            contentColor = Colors.TextPrimary,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

// ---- Ring ----------------------------------------------------------------

@Composable
private fun RestRing(state: RestTimerUiState, onEvent: (RestTimerEvent) -> Unit) {
    val remaining = state.remainingSeconds
    val total = state.totalSeconds
    val overtime = remaining < 0
    val fraction = if (total > 0) (remaining.toFloat() / total).coerceIn(0f, 1f) else 0f
    val arcColor = when {
        state.paused -> Colors.TextSecondary
        overtime -> Colors.Tertiary
        else -> Colors.Primary
    }
    val track = Colors.Surface2
    Box(modifier = Modifier.size(216.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            // Remaining rest drains clockwise from 12 o'clock; full ring once over time.
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = if (overtime) 360f else 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (state.paused) {
            PausedTime(state = state, onEvent = onEvent)
        } else {
            Column(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = if (overtime) {
                        "${spokenDuration(-remaining)} over the rest time"
                    } else {
                        "${spokenDuration(remaining)} remaining"
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (overtime) "+${formatClock(-remaining)}" else formatClock(remaining),
                    style = TextStyles.dataDisplayXl.copy(fontSize = 80.sp, lineHeight = 74.sp),
                    color = if (overtime) Colors.Tertiary else Colors.TextPrimary,
                )
                Text(
                    text = when {
                        overtime -> "over ${formatClock(total)} rest"
                        state.fromSheet -> "of ${formatClock(total)} · from the sheet"
                        else -> "of ${formatClock(total)} rest"
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    style = TextStyles.bodyMd,
                    color = Colors.TextSecondary,
                )
            }
        }
    }
}

/** While paused the length can be typed; each time that reads as one becomes the rest's length. */
@Composable
private fun PausedTime(state: RestTimerUiState, onEvent: (RestTimerEvent) -> Unit) {
    var typed by rememberSaveable { mutableStateOf(formatRestTime(state.totalSeconds)) }
    // ±15 s or a preset changed it meanwhile.
    LaunchedEffect(state.totalSeconds) {
        if (parseRestTime(typed) != state.totalSeconds) typed = formatRestTime(state.totalSeconds)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "PAUSED · TYPE A TIME", style = TextStyles.labelCaps, color = Colors.Primary)
        val underline = Colors.Primary
        BasicTextField(
            value = typed,
            onValueChange = { value ->
                typed = value.filterRestTime()
                parseRestTime(typed)?.takeIf { it != state.totalSeconds }?.let { onEvent(RestTimerEvent.SetLength(it)) }
            },
            modifier = Modifier
                .width(190.dp)
                .drawBehind {
                    val stroke = 3.dp.toPx()
                    drawLine(underline, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                }
                .semantics { contentDescription = "Rest time, minutes and seconds" },
            textStyle = TextStyles.dataDisplayXl.copy(
                fontSize = 80.sp,
                lineHeight = 84.sp,
                color = Colors.TextPrimary,
                textAlign = TextAlign.Center,
            ),
            singleLine = true,
            cursorBrush = SolidColor(Colors.Primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        Text(
            text = "${formatClock(state.remainingSeconds.coerceAtLeast(0))} left of ${formatClock(state.totalSeconds)}",
            modifier = Modifier.padding(top = 8.dp),
            style = TextStyles.bodyMd,
            color = Colors.TextSecondary,
        )
    }
}

@Composable
private fun AdjustButton(label: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 96.dp)
            .heightIn(min = 52.dp)
            .semantics { contentDescription = description },
        shape = Shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Colors.Surface2,
            contentColor = Colors.TextPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Text(text = label, style = TextStyles.bodyMd.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Colors.TextPrimary,
            contentColor = Colors.OnPrimary,
        ),
    ) {
        Icon(
            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = if (paused) "Resume rest timer" else "Pause rest timer",
            modifier = Modifier.size(32.dp),
        )
    }
}

// ---- Lengths -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Presets(title: String, presets: List<RestPreset>, onSelect: (Int) -> Unit) {
    Column {
        Label(title, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { preset ->
                Surface(
                    selected = preset.selected,
                    onClick = { onSelect(preset.seconds) },
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .widthIn(min = 56.dp),
                    shape = Shapes.xl,
                    color = if (preset.selected) Colors.Primary else Colors.Surface2,
                    contentColor = if (preset.selected) Colors.OnPrimary else Colors.TextPrimary,
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = buildAnnotatedString {
                                append(formatClock(preset.seconds))
                                if (preset.fromSheet) {
                                    withStyle(SpanStyle(fontSize = 11.sp, fontFamily = TextStyles.bodySm.fontFamily)) {
                                        append(" sheet")
                                    }
                                }
                            },
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = TextStyles.dataValue.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarmUpRestCard(warmUp: WarmUpRest, presets: List<RestPreset>, onEvent: (RestTimerEvent) -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Presets(title = "Warm-up rest · your time", presets = presets) { onEvent(RestTimerEvent.SetLength(it)) }
            HorizontalDivider(color = Colors.Border)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use for all warm-ups",
                        style = TextStyles.bodyMd.copy(fontWeight = FontWeight.Bold),
                        color = Colors.TextPrimary,
                    )
                    Text(
                        text = if (warmUp.useForAll) {
                            "The sheet has no warm-up rest. Saved for ${warmUp.exerciseName}."
                        } else {
                            "The sheet has no warm-up rest. Only this rest changes."
                        },
                        style = TextStyles.bodyNote,
                        color = Colors.TextSecondary,
                    )
                }
                Switch(
                    checked = warmUp.useForAll,
                    onCheckedChange = { onEvent(RestTimerEvent.UseForAllWarmUps(it)) },
                    modifier = Modifier.semantics {
                        contentDescription = "Use this time for all warm-ups of this exercise"
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Colors.OnPrimary,
                        checkedTrackColor = Colors.Primary,
                        uncheckedThumbColor = Colors.TextSecondary,
                        uncheckedTrackColor = Colors.Border,
                        uncheckedBorderColor = Colors.Border,
                    ),
                )
            }
        }
    }
}

// ---- Up next -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpNextCard(upNext: UpNext, lastSet: LoggedSetInfo) {
    Card {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label("Up next")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = upNext.exerciseName,
                    modifier = Modifier.weight(1f),
                    style = TextStyles.bodyLg.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                    color = Colors.TextPrimary,
                )
                Text(
                    text = buildAnnotatedString {
                        append(upNext.label.uppercase())
                        upNext.setCount?.let {
                            withStyle(SpanStyle(color = Colors.TextSecondary, fontWeight = FontWeight.SemiBold)) {
                                append(" of $it")
                            }
                        }
                    },
                    style = TextStyles.headlineMd.copy(fontWeight = FontWeight.Bold),
                    color = Colors.Primary,
                )
            }
            if (upNext.targets.isNotEmpty() || upNext.tempo != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    upNext.targets.forEach { Chip(text = it) }
                    upNext.tempo?.let { TempoBadge(tempo = it) }
                }
            }
            Text(
                text = "Last set logged: ${lastSet.summary}",
                style = TextStyles.bodyNote,
                color = Colors.TextSecondary,
            )
        }
    }
}

/** After a warm-up: the working set it leads to, in one line. */
@Composable
private fun WorkingSetUpNext(upNext: UpNext) {
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Label("Up next")
                Text(
                    text = "${upNext.exerciseName} · " +
                        if (upNext.label.startsWith("Set ")) "working ${upNext.label.lowercase()}" else upNext.label,
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyles.bodyLg.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = Colors.TextPrimary,
                )
            }
            Text(
                text = upNext.target,
                style = TextStyles.headlineMd.copy(fontWeight = FontWeight.Bold),
                color = Colors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

// ---- Previews ------------------------------------------------------------

private val SheetPresets = listOf(60, 90, 120, 150, 180).map { RestPreset(it, selected = it == 150, fromSheet = it == 150) }

@Composable
private fun RestTimerPreviewContent(state: RestTimerUiState) {
    WorkoutTheme {
        RestTimerScreen(state = state, onEvent = {})
    }
}

@Preview(name = "Resting", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun RestTimerRestingPreview() {
    RestTimerPreviewContent(
        RestTimerUiState(
            workoutName = "T2",
            elapsed = "14:51",
            remainingSeconds = 131,
            totalSeconds = 150,
            fromSheet = true,
            presets = SheetPresets,
            lastSet = LoggedSetInfo(label = "Set 3", summary = "90 kg × 5"),
            upNext = UpNext(
                exerciseName = "RDL",
                label = "Set 4",
                targets = listOf("5 reps", "92.5 kg"),
                tempo = Tempo("3010"),
                setCount = 5,
            ),
        )
    )
}

@Preview(name = "After a warm-up, paused", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun RestTimerWarmUpPreview() {
    RestTimerPreviewContent(
        RestTimerUiState(
            workoutName = "T2",
            elapsed = "11:36",
            remainingSeconds = 48,
            totalSeconds = 60,
            paused = true,
            presets = listOf(30, 45, 60, 90, 120).map { RestPreset(it, selected = it == 60) },
            lastSet = LoggedSetInfo(label = "Warm-up 3", summary = "60 kg × 5"),
            upNext = UpNext(exerciseName = "RDL", label = "Set 1", targets = listOf("6 reps", "60 kg"), target = "6 × 60 kg"),
            warmUp = WarmUpRest(exerciseName = "RDL", useForAll = true),
        )
    )
}

@Preview(name = "Superset, next member", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun RestTimerSupersetPreview() {
    RestTimerPreviewContent(
        RestTimerUiState(
            workoutName = "T2",
            elapsed = "51:40",
            remainingSeconds = 28,
            totalSeconds = 45,
            fromSheet = true,
            lastSet = LoggedSetInfo(label = "5b · round 2", summary = "6 kg × 10"),
            upNext = UpNext(
                exerciseName = "Wycieraczki",
                label = "5c · round 2",
                targets = listOf("8 reps", "RPE 8.5"),
            ),
        )
    )
}

@Preview(name = "Over time", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun RestTimerOvertimePreview() {
    RestTimerPreviewContent(
        RestTimerUiState(
            workoutName = "T2",
            elapsed = "17:33",
            remainingSeconds = -12,
            totalSeconds = 150,
            presets = SheetPresets,
            lastSet = LoggedSetInfo(label = "Set 3", summary = "90 kg × 5"),
            upNext = UpNext(
                exerciseName = "RDL",
                label = "Set 4",
                targets = listOf("5 reps", "92.5 kg"),
                tempo = Tempo("3010"),
                setCount = 5,
            ),
        )
    )
}
