package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Chip
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.RestChip
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.application.ui.components.TempoBadge
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// ---- UI state ------------------------------------------------------------

@Immutable
data class WorkoutDetailUiState(
    /** e.g. "1 – 7 Jun". */
    val weekRange: String,
    /** e.g. "T1". */
    val workoutName: String,
    val status: WorkoutStatus,
    val items: List<WorkoutItem>,
    val footer: WorkoutDetailFooter,
)

sealed interface WorkoutItem {
    val index: Int

    data class Single(override val index: Int, val exercise: ExerciseDetail) : WorkoutItem

    /** Exercises done alternately; each member's set count is its number of rounds. */
    data class Superset(override val index: Int, val exercises: List<ExerciseDetail>) : WorkoutItem
}

@Immutable
data class ExerciseDetail(
    val id: String,
    val name: String,
    val setGroups: List<SetGroup>,
    /** null when the sheet gives no rest. */
    val restSeconds: Int?,
    val tempo: Tempo? = null,
    /** Text from the sheet's WYNIK cell. */
    val result: String? = null,
    val progress: ExerciseProgress = ExerciseProgress.Pending,
) {
    /** Planned sets; open-ended groups count as none. */
    val setCount: Int get() = setGroups.sumOf { it.sets ?: 0 }
}

/** A group of identical sets, e.g. 2 × 5 at 90 kg. */
@Immutable
data class SetGroup(
    /** null for open sets ("x" in the sheet) and unlimited ones (an empty cell). */
    val sets: Int?,
    /** e.g. "5" or "10–12". */
    val reps: String,
    /** e.g. "90 kg", "s", "RPE 8.5". */
    val load: String,
    /** An empty sets cell: as many sets as the user wants, shown as "∞". */
    val unlimited: Boolean = false,
)

sealed interface ExerciseProgress {
    data object Pending : ExerciseProgress
    data object Done : ExerciseProgress
    /**
     * The exercise the active session is on; [set] is null until sets are logged in the app.
     * With [review], every set is logged and its result waits to be reviewed.
     */
    data class Current(val set: Int? = null, val setCount: Int? = null, val review: Boolean = false) : ExerciseProgress
}

sealed interface WorkoutDetailFooter {
    /** [on] e.g. "Mon 1 Jun"; null when the date isn't known. */
    data class Completed(val on: String?) : WorkoutDetailFooter
    data object Resume : WorkoutDetailFooter
    data object Start : WorkoutDetailFooter

    /** Workout can't be started from here, e.g. "Upcoming · view only". */
    data class ViewOnly(val reason: String) : WorkoutDetailFooter
}

// ---- Route ---------------------------------------------------------------

/**
 * Workout detail backed by [WorkoutDetailViewModel]; only navigation leaves this
 * composable. The workout comes from the [WORKOUT_ID_ARG] navigation argument.
 */
@Composable
fun WorkoutDetailRoute(
    onBack: () -> Unit,
    onOpenSession: () -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            WorkoutDetailNavigation.SessionStarted -> onOpenSession()
        }
    }
    when (val detail = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> WorkoutDetailScreen(
            state = detail.value,
            onBack = onBack,
            onEditResult = { editingExerciseId = it },
            onResumeSession = onOpenSession,
            onStartWorkout = viewModel::onStartWorkout,
        )
    }
    editingExerciseId?.let { id ->
        ResultEditorRoute(exerciseId = id, onClose = { editingExerciseId = null })
    }
}

// ---- Screen --------------------------------------------------------------

@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onEditResult: (exerciseId: String) -> Unit,
    onResumeSession: () -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val markDone = state.status == WorkoutStatus.InProgress
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Colors.Canvas,
        bottomBar = {
            Footer(
                footer = state.footer,
                workoutName = state.workoutName,
                onResume = onResumeSession,
                onStart = onStartWorkout,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopRow(weekRange = state.weekRange, onBack = onBack)
            TitleRow(
                name = state.workoutName,
                exerciseCount = state.items.sumOf {
                    when (it) {
                        is WorkoutItem.Single -> 1
                        is WorkoutItem.Superset -> it.exercises.size
                    }
                },
                status = state.status,
            )
            Text(
                text = buildAnnotatedString {
                    append("Each chip is a group of sets: ")
                    withStyle(SpanStyle(color = Colors.TextPrimary, fontWeight = FontWeight.Bold)) {
                        append("sets × reps")
                    }
                    append(" at a weight. Ramp-up and back-off sets are their own groups.")
                },
                modifier = Modifier.padding(bottom = 4.dp),
                style = KineticType.bodySm,
                color = Colors.TextSecondary,
            )
            state.items.forEach { item ->
                when (item) {
                    is WorkoutItem.Single -> ExerciseCard(
                        index = item.index,
                        exercise = item.exercise,
                        markDone = markDone,
                        onEditResult = onEditResult,
                    )
                    is WorkoutItem.Superset -> SupersetCard(
                        superset = item,
                        onEditResult = onEditResult,
                    )
                }
            }
        }
    }
}

// ---- Header --------------------------------------------------------------

@Composable
private fun TopRow(weekRange: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilledIconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = Shapes.xl,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Colors.Surface2,
                contentColor = Colors.TextPrimary,
            ),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = weekRange.uppercase(),
            style = KineticType.labelCaps,
            color = Colors.TextSecondary,
        )
    }
}

@Composable
private fun TitleRow(name: String, exerciseCount: Int, status: WorkoutStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name.uppercase(),
            style = KineticType.screenTitle,
            color = Colors.TextPrimary,
        )
        Row(
            modifier = Modifier.padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip(text = if (exerciseCount == 1) "1 exercise" else "$exerciseCount exercises")
            when (status) {
                is WorkoutStatus.Completed -> Chip(text = "Completed", contentColor = Colors.Primary)
                WorkoutStatus.InProgress -> Chip(text = "In progress", contentColor = Colors.Primary)
                WorkoutStatus.NotStarted -> Chip(text = "Not started", contentColor = Colors.TextSecondary)
            }
        }
    }
}

// ---- Exercises -----------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    index: Int,
    exercise: ExerciseDetail,
    markDone: Boolean,
    onEditResult: (String) -> Unit,
) {
    val current = exercise.progress as? ExerciseProgress.Current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.xl,
        color = Colors.Surface1,
        border = if (current != null) {
            BorderStroke(1.5.dp, Colors.Primary)
        } else {
            BorderStroke(1.dp, Colors.Border)
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IndexBadge(index = index, done = markDone && exercise.progress == ExerciseProgress.Done)
                ExerciseName(
                    text = exercise.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp),
                )
                if (current != null) {
                    Chip(
                        text = when {
                            current.review -> "Current · review result"
                            current.set == null -> "Current"
                            else -> "Current · set ${current.set} of ${current.setCount}"
                        },
                        contentColor = Colors.Primary,
                    )
                }
            }
            SetGroupList(exercise = exercise, background = Colors.Canvas)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                exercise.tempo?.let { TempoBadge(tempo = it) }
                exercise.restSeconds?.let { RestChip(seconds = it) }
                if (exercise.setGroups.size > 1) {
                    Chip(text = "${exercise.setCount} sets", contentColor = Colors.TextSecondary)
                }
            }
            exercise.result?.let { result ->
                ResultRow(
                    result = result,
                    background = Colors.Canvas,
                    onEdit = { onEditResult(exercise.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupersetCard(superset: WorkoutItem.Superset, onEditResult: (String) -> Unit) {
    val rounds = superset.exercises.maxOfOrNull { it.setCount } ?: 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.xl,
        color = Colors.Surface1,
        border = BorderStroke(1.dp, Colors.Primary.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IndexBadge(index = superset.index, done = false, numberColor = Colors.Primary)
                ExerciseName(text = "Superset", color = Colors.Primary, modifier = Modifier.weight(1f))
                Chip(text = "${superset.exercises.size} exercises · $rounds rounds")
            }
            superset.exercises.forEachIndexed { position, exercise ->
                val tag = supersetTag(superset.index, position)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Colors.Surface2, Shapes.xl)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = tag,
                            modifier = Modifier.width(30.dp),
                            style = KineticType.dataValue.copy(fontSize = 20.sp),
                            color = Colors.Primary,
                        )
                        ExerciseName(
                            text = exercise.name,
                            fontSize = 17,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 1.dp),
                        )
                    }
                    SetGroupList(exercise = exercise, background = Colors.Surface1)
                    if (exercise.tempo != null || exercise.restSeconds != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            exercise.tempo?.let { TempoBadge(tempo = it, background = Colors.Surface3) }
                            exercise.restSeconds?.let { RestChip(seconds = it, background = Colors.Surface3) }
                        }
                    }
                    exercise.result?.let { result ->
                        ResultRow(
                            result = result,
                            background = Colors.Surface1,
                            onEdit = { onEditResult(exercise.id) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapVert,
                    contentDescription = null,
                    tint = Colors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = alternationNote(superset),
                    style = KineticType.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = Colors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun supersetTag(index: Int, position: Int): String = "$index${'a' + position}"

/**
 * "Alternate 5a → 5b → 5c each round", or when members have different set
 * counts, which rounds only some of them take part in.
 */
private fun alternationNote(superset: WorkoutItem.Superset): String {
    val tags = superset.exercises.indices.map { supersetTag(superset.index, it) }
    val order = "Alternate ${tags.joinToString(" → ")}"
    val rounds = superset.exercises.maxOfOrNull { it.setCount } ?: 0
    val members = (1..rounds).map { round ->
        superset.exercises.indices.filter { superset.exercises[it].setCount >= round }.map { tags[it] }
    }
    if (members.all { it.size == tags.size }) return "$order each round"

    // Group consecutive partial rounds that share the same members.
    val partial = mutableListOf<Triple<Int, Int, List<String>>>()
    members.forEachIndexed { i, who ->
        if (who.size == tags.size) return@forEachIndexed
        val last = partial.lastOrNull()
        if (last != null && last.second == i && last.third == who) {
            partial[partial.lastIndex] = last.copy(second = i + 1)
        } else {
            partial += Triple(i + 1, i + 1, who)
        }
    }
    val details = partial.joinToString(" ") { (from, to, who) ->
        val names = who.joinToString(" and ")
        if (from == to) "Round $from is $names only." else "Rounds $from–$to are $names only."
    }
    return "$order. $details"
}

@Composable
private fun IndexBadge(index: Int, done: Boolean, numberColor: Color = Colors.TextSecondary) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(if (done) Colors.Primary else Colors.Surface2, Shapes.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Done",
                tint = Colors.OnPrimary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = index.toString(),
                style = KineticType.dataMonoMd.copy(fontWeight = FontWeight.Bold),
                color = numberColor,
            )
        }
    }
}

@Composable
private fun ExerciseName(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Colors.TextPrimary,
    fontSize: Int = 19,
) {
    Text(
        text = text,
        modifier = modifier,
        style = KineticType.bodyLg.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.2f).sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = color,
    )
}

private enum class GroupStatus { Done, Current, Pending }

/**
 * Where each group stands: all done or all to come for a finished or untouched
 * exercise; for the current one, by which set is next ("set 4 of 5").
 */
private fun ExerciseDetail.groupStatuses(): List<GroupStatus> = when (val progress = progress) {
    ExerciseProgress.Done -> setGroups.map { GroupStatus.Done }
    ExerciseProgress.Pending -> setGroups.map { GroupStatus.Pending }
    is ExerciseProgress.Current -> if (progress.review) setGroups.map { GroupStatus.Done } else {
        val logged = (progress.set ?: 1) - 1
        var start = 0
        setGroups.map { group ->
            val end = group.sets?.let { start + it }
            val status = when {
                end != null && logged >= end -> GroupStatus.Done
                logged >= start -> GroupStatus.Current
                else -> GroupStatus.Pending
            }
            start = end ?: start
            status
        }
    }
}

/** One row per group of sets, "2 × 5 … 92.5 kg", each marked done, current or to come. */
@Composable
private fun SetGroupList(exercise: ExerciseDetail, background: Color) {
    val statuses = exercise.groupStatuses()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, Shapes.xl)
            .padding(horizontal = 12.dp),
    ) {
        exercise.setGroups.forEachIndexed { index, group ->
            if (index > 0) HorizontalDivider(color = Colors.Border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GroupStatusIcon(statuses[index])
                Text(
                    text = when {
                        !group.unlimited -> "${group.sets ?: "x"} × ${group.reps}"
                        group.reps.isEmpty() -> "∞"
                        else -> "∞ × ${group.reps}"
                    },
                    modifier = Modifier.weight(1f),
                    style = KineticType.dataValue.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = Colors.TextPrimary,
                    maxLines = 1,
                )
                // Bodyweight goes without saying.
                if (group.load != "BW") {
                    Text(
                        text = group.load,
                        style = KineticType.dataValue.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                        color = Colors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupStatusIcon(status: GroupStatus) {
    val modifier = Modifier
        .size(18.dp)
        .semantics {
            contentDescription = when (status) {
                GroupStatus.Done -> "Sets done"
                GroupStatus.Current -> "Current sets"
                GroupStatus.Pending -> "Sets to do"
            }
        }
    when (status) {
        GroupStatus.Done -> Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Colors.Primary, modifier = modifier)
        GroupStatus.Current -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(10.dp).background(Colors.Primary, CircleShape))
        }
        GroupStatus.Pending -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(14.dp).border(2.dp, Colors.Surface3, CircleShape))
        }
    }
}

@Composable
private fun ResultRow(result: String, background: Color, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(background, Shapes.lg)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "RESULT",
            style = KineticType.labelCaps,
            color = Colors.Primary,
        )
        Text(
            text = result,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            style = KineticType.bodyNote,
            color = Colors.TextPrimary,
        )
        IconButton(
            onClick = onEdit,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Colors.TextSecondary),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit result",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---- Footer --------------------------------------------------------------

@Composable
private fun Footer(
    footer: WorkoutDetailFooter,
    workoutName: String,
    onResume: () -> Unit,
    onStart: () -> Unit,
) {
    Column(modifier = Modifier.background(Colors.Surface1)) {
        HorizontalDivider(color = Colors.Border)
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        ) {
            when (footer) {
                is WorkoutDetailFooter.Completed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Colors.Primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Colors.TextPrimary, fontWeight = FontWeight.Bold)) {
                                append("Completed${footer.on?.let { " $it" }.orEmpty()}.")
                            }
                            append(" Results are read from your sheet's WYNIK column.")
                        },
                        style = KineticType.bodyNote,
                        color = Colors.TextSecondary,
                    )
                }
                WorkoutDetailFooter.Resume -> PrimaryAction(text = "Resume session", onClick = onResume)
                WorkoutDetailFooter.Start -> PrimaryAction(text = "Start $workoutName", onClick = onStart)
                is WorkoutDetailFooter.ViewOnly -> Text(
                    text = footer.reason,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(vertical = 12.dp),
                    style = KineticType.bodyNoteStrong,
                    color = Colors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = Shapes.xl,
        colors = ButtonDefaults.buttonColors(
            containerColor = Colors.Primary,
            contentColor = Colors.OnPrimary,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = KineticType.buttonLabelLg,
        )
    }
}

// ---- Previews ------------------------------------------------------------

private fun kg(sets: Int, reps: String, weight: String) = SetGroup(sets, reps, "$weight kg")

private val previewCompleted = WorkoutDetailUiState(
    weekRange = "1 – 7 Jun",
    workoutName = "T1",
    status = WorkoutStatus.Completed("Mon"),
    footer = WorkoutDetailFooter.Completed("Mon 1 Jun"),
    items = listOf(
        WorkoutItem.Single(
            1,
            ExerciseDetail(
                id = "t1-1",
                name = "Przysiad",
                setGroups = listOf(
                    kg(1, "6", "60"), kg(1, "5", "75"), kg(1, "5", "85"),
                    kg(2, "5", "90"), kg(2, "8", "85"),
                ),
                restSeconds = 150,
                result = "RPE 9.5",
                progress = ExerciseProgress.Done,
            ),
        ),
        WorkoutItem.Single(
            2,
            ExerciseDetail(
                id = "t1-2",
                name = "Bench Press",
                setGroups = listOf(kg(1, "8", "50"), kg(1, "6", "65"), kg(3, "5", "75"), kg(2, "8", "71")),
                restSeconds = 150,
                result = "Ostatnia seria 6 repów",
                progress = ExerciseProgress.Done,
            ),
        ),
        WorkoutItem.Single(
            3,
            ExerciseDetail(
                id = "t1-3",
                name = "Wyciskanie hantli leżąc skos dodatni (30 stopni)",
                setGroups = listOf(kg(2, "12", "20")),
                restSeconds = 150,
                result = "RPE 9.5",
                progress = ExerciseProgress.Done,
            ),
        ),
        WorkoutItem.Single(
            4,
            ExerciseDetail(
                id = "t1-4",
                name = "Wykroki",
                setGroups = listOf(kg(2, "8", "20")),
                restSeconds = 120,
                result = "RPE 9",
                progress = ExerciseProgress.Done,
            ),
        ),
        WorkoutItem.Superset(
            5,
            listOf(
                ExerciseDetail(
                    id = "t1-5a", name = "California Press Hantlami",
                    setGroups = listOf(kg(3, "10–12", "15")), restSeconds = 45,
                    result = "10 repów", progress = ExerciseProgress.Done,
                ),
                ExerciseDetail(
                    id = "t1-5b", name = "Wznosy Bokiem Hantlami",
                    setGroups = listOf(kg(3, "8–12", "8")), restSeconds = 45,
                    result = "10 repów", progress = ExerciseProgress.Done,
                ),
                ExerciseDetail(
                    id = "t1-5c", name = "Wznosy Nóg",
                    setGroups = listOf(kg(3, "10", "5")), restSeconds = 45,
                    result = "RPE 8.5", progress = ExerciseProgress.Done,
                ),
            ),
        ),
    ),
)

private val previewT2Items = listOf(
    WorkoutItem.Single(
        1,
        ExerciseDetail(
            id = "t2-1",
            name = "Half Tucked Front Lever Hold",
            setGroups = listOf(SetGroup(4, "8–15", "s")),
            restSeconds = 150,
            result = "10 s, 15 s, 15 s, 15 s",
            progress = ExerciseProgress.Done,
        ),
    ),
    WorkoutItem.Single(
        2,
        ExerciseDetail(
            id = "t2-2",
            name = "RDL",
            setGroups = listOf(kg(1, "6", "60"), kg(1, "6", "75"), kg(1, "5", "90"), kg(2, "5", "92.5")),
            restSeconds = 150,
            tempo = Tempo("3010", appliesTo = "last group"),
            progress = ExerciseProgress.Current(set = 4, setCount = 5),
        ),
    ),
    WorkoutItem.Single(
        3,
        ExerciseDetail(
            id = "t2-3",
            name = "Podciąganie najsilniejszy chwyt",
            setGroups = listOf(kg(3, "6", "20")),
            restSeconds = 150,
        ),
    ),
    WorkoutItem.Single(
        4,
        ExerciseDetail(
            id = "t2-4",
            name = "Żurawie",
            setGroups = listOf(SetGroup(3, "8", "RPE 8.5")),
            restSeconds = 120,
        ),
    ),
    WorkoutItem.Superset(
        5,
        listOf(
            ExerciseDetail("t2-5a", "Biceps młotkowo", listOf(kg(3, "10–12", "15")), restSeconds = 45),
            ExerciseDetail("t2-5b", "Reverse Flies Hantlami", listOf(kg(2, "10", "6")), restSeconds = 45),
            ExerciseDetail("t2-5c", "Wycieraczki", listOf(SetGroup(2, "8", "RPE 8.5")), restSeconds = 45),
        ),
    ),
)

private val previewInProgress = WorkoutDetailUiState(
    weekRange = "1 – 7 Jun",
    workoutName = "T2",
    status = WorkoutStatus.InProgress,
    footer = WorkoutDetailFooter.Resume,
    items = previewT2Items,
)

private val previewNotStartedItems = previewT2Items.map { item ->
    when (item) {
        is WorkoutItem.Single -> item.copy(
            exercise = item.exercise.copy(result = null, progress = ExerciseProgress.Pending),
        )
        is WorkoutItem.Superset -> item
    }
}

@Composable
private fun WorkoutDetailPreviewContent(state: WorkoutDetailUiState) {
    WorkoutTheme {
        WorkoutDetailScreen(
            state = state,
            onBack = {},
            onEditResult = {},
            onResumeSession = {},
            onStartWorkout = {},
        )
    }
}

@Preview(name = "Completed", widthDp = 390, heightDp = 1600, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WorkoutDetailCompletedPreview() {
    WorkoutDetailPreviewContent(previewCompleted)
}

@Preview(name = "In progress", widthDp = 390, heightDp = 1400, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WorkoutDetailInProgressPreview() {
    WorkoutDetailPreviewContent(previewInProgress)
}

@Preview(name = "Not started, can start", widthDp = 390, heightDp = 1400, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WorkoutDetailStartablePreview() {
    WorkoutDetailPreviewContent(
        previewInProgress.copy(
            status = WorkoutStatus.NotStarted,
            footer = WorkoutDetailFooter.Start,
            items = previewNotStartedItems,
        )
    )
}

@Preview(name = "Not started, view only", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WorkoutDetailViewOnlyPreview() {
    WorkoutDetailPreviewContent(
        previewInProgress.copy(
            weekRange = "8 – 14 Jun",
            status = WorkoutStatus.NotStarted,
            footer = WorkoutDetailFooter.ViewOnly("Upcoming · view only"),
            items = previewNotStartedItems,
        )
    )
}
