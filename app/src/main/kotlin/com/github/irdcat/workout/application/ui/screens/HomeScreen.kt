package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Card
import com.github.irdcat.workout.application.ui.components.CardBorder
import com.github.irdcat.workout.application.ui.components.Chevron
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.MainNavigationBar
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.components.Skeleton
import com.github.irdcat.workout.application.ui.components.SkeletonBlock
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// ---- UI state ------------------------------------------------------------

@Immutable
data class HomeUiState(
    /** e.g. "June 2026". */
    val monthLabel: String,
    /** e.g. "1 – 7 Jun". */
    val weekRange: String,
    val sync: SyncStatus,
    val isRefreshing: Boolean = false,
    /** Shown above any content: a session can be open in a week without a plan, or a rest week. */
    val activeSession: ActiveSession? = null,
    val content: HomeContent,
)

sealed interface SyncStatus {
    /** Time of the last successful sync, e.g. "14:32". */
    val time: String

    data class Synced(override val time: String) : SyncStatus
    data class Offline(override val time: String) : SyncStatus
}

sealed interface HomeContent {
    /** The sheet has a plan for this week. */
    data class Plan(
        val workouts: List<WorkoutSummary>,
    ) : HomeContent

    /**
     * The sheet has no week matching the current one. The newest-week fields are
     * null when nothing has been loaded from the sheet yet.
     */
    data class NoPlan(
        /** e.g. "19 Oct". */
        val weekStart: String,
        /** e.g. "WRZESIEŃ". */
        val newestSheet: String?,
        /** e.g. "20 Sep". */
        val newestSheetEnd: String?,
        /** e.g. "14 – 20 Sep". */
        val latestWeekRange: String?,
    ) : HomeContent

    /** This week is marked "WOLNE" in the sheet. */
    data object RestWeek : HomeContent
}

@Immutable
data class WorkoutSummary(
    val id: String,
    /** Name from the sheet, e.g. "T1". */
    val name: String,
    /** First exercises, e.g. "Przysiad · Bench Press". */
    val title: String,
    val exerciseCount: Int,
    val supersetCount: Int,
    val status: WorkoutStatus,
)

sealed interface WorkoutStatus {
    /** [day] is a short day label, e.g. "Mon"; null when the day isn't known. */
    data class Completed(val day: String?) : WorkoutStatus
    data object InProgress : WorkoutStatus
    data object NotStarted : WorkoutStatus
}

@Immutable
data class ActiveSession(
    val workoutName: String,
    /** e.g. "12:04"; null when "measure workout time" is off. */
    val elapsed: String?,
    /** The exercise up next; null when every exercise has a result. */
    val exercise: String?,
    /** Set position within [exercise]; null until sets are logged in the app. */
    val set: Int? = null,
    val setCount: Int? = null,
    /** The rest being taken after the last set; null when not resting. */
    val rest: ActiveRest? = null,
    /** Every set of [exercise] is logged; its result waits to be reviewed. */
    val reviewing: Boolean = false,
)

@Immutable
data class ActiveRest(
    /** e.g. "2:11". */
    val left: String,
    val paused: Boolean = false,
)

// ---- Route ---------------------------------------------------------------

/** Home backed by [HomeViewModel]; only navigation leaves this composable. */
@Composable
fun HomeRoute(
    onOpenSession: () -> Unit,
    /** The session with its rest timer on top, for a rest that's still running. */
    onOpenRest: () -> Unit,
    onOpenWorkout: (id: String) -> Unit,
    onOpenLatestWeek: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            HomeNavigation.SessionStarted -> onOpenSession()
        }
    }
    when (val home = state) {
        Loadable.Loading -> HomeSkeleton()
        is Loadable.Ready -> HomeScreen(
            state = home.value,
            onRefresh = viewModel::onRefresh,
            onResumeSession = onOpenSession,
            onResumeRest = onOpenRest,
            onOpenWorkout = onOpenWorkout,
            onStartWorkout = viewModel::onStartWorkout,
            onOpenLatestWeek = onOpenLatestWeek,
            onTabSelected = onTabSelected,
        )
    }
}

// ---- Loading -------------------------------------------------------------

/**
 * The week as it will look, in shimmering placeholders: shown while the app starts
 * and while the plan loads for the first time.
 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    Skeleton(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Colors.Canvas)
                .systemBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBlock(Modifier.size(width = 140.dp, height = 12.dp))
                        SkeletonBlock(Modifier.size(width = 200.dp, height = 46.dp), shape = Shapes.lg)
                    }
                    SkeletonBlock(Modifier.size(width = 112.dp, height = 44.dp), shape = Shapes.pill)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBlock(Modifier.size(width = 76.dp, height = 12.dp))
                    SkeletonBlock(
                        Modifier
                            .weight(1f)
                            .height(6.dp),
                        shape = Shapes.pill,
                    )
                    SkeletonBlock(Modifier.size(width = 72.dp, height = 14.dp))
                }
                repeat(3) { WorkoutCardSkeleton() }
            }
            HorizontalDivider(color = Colors.Border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Colors.Surface1)
                    .height(80.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(MainTab.entries.size) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SkeletonBlock(Modifier.size(24.dp), shape = Shapes.lg)
                        SkeletonBlock(Modifier.size(width = 44.dp, height = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCardSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Surface1, Shapes.xl)
            .border(1.dp, Colors.Border, Shapes.xl)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBlock(Modifier.size(44.dp), shape = Shapes.xl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(0.75f)
                    .height(18.dp),
            )
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp),
            )
        }
    }
}

// ---- Screen --------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onResumeSession: () -> Unit,
    onResumeRest: () -> Unit,
    onOpenWorkout: (id: String) -> Unit,
    onStartWorkout: (id: String) -> Unit,
    onOpenLatestWeek: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Colors.Canvas,
        bottomBar = { MainNavigationBar(selected = MainTab.Week, onSelect = onTabSelected) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WeekHeader(
                    monthLabel = state.monthLabel,
                    weekRange = state.weekRange,
                    sync = state.sync,
                    onRefresh = onRefresh,
                )
                if (state.sync is SyncStatus.Offline) {
                    OfflineBanner()
                }
                state.activeSession?.let { session ->
                    ActiveSessionCard(
                        session = session,
                        onResume = if (session.rest != null) onResumeRest else onResumeSession,
                    )
                }
                when (val content = state.content) {
                    is HomeContent.Plan -> PlanContent(
                        plan = content,
                        activeSession = state.activeSession,
                        onOpenWorkout = onOpenWorkout,
                        onStartWorkout = onStartWorkout,
                    )
                    is HomeContent.NoPlan -> NoPlanContent(
                        noPlan = content,
                        onOpenLatestWeek = onOpenLatestWeek,
                        onRefresh = onRefresh,
                    )
                    HomeContent.RestWeek -> RestWeekContent()
                }
            }
        }
    }
}

// ---- Header & sync -------------------------------------------------------

@Composable
private fun WeekHeader(
    monthLabel: String,
    weekRange: String,
    sync: SyncStatus,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Label("This week · $monthLabel")
            Text(
                text = weekRange.uppercase(),
                style = TextStyles.screenTitle,
                color = Colors.TextPrimary,
                maxLines = 1,
            )
        }
        SyncChip(sync = sync, onClick = onRefresh)
    }
}

@Composable
private fun SyncChip(sync: SyncStatus, onClick: () -> Unit) {
    val offline = sync is SyncStatus.Offline
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .semantics {
                contentDescription = if (offline) {
                    "Retry sync. Offline, showing data cached at ${sync.time}"
                } else {
                    "Refresh. Last synced ${sync.time}"
                }
            },
        shape = Shapes.pill,
        color = Colors.Surface2,
        contentColor = if (offline) Colors.Tertiary else Colors.TextPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (offline) {
                Dot(color = Colors.Tertiary)
                ChipText("Offline · ${sync.time}")
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                ChipText("Synced ${sync.time}")
            }
        }
    }
}

@Composable
private fun ChipText(text: String) {
    Text(
        text = text,
        style = TextStyles.bodySm.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Tertiary.copy(alpha = 0.14f), Shapes.xl)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = Colors.Tertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Showing saved workouts. Results will be written to your sheet when you're back online.",
            style = TextStyles.bodyNote.copy(fontWeight = FontWeight.Medium),
            color = Colors.Tertiary,
        )
    }
}

// ---- Plan for this week --------------------------------------------------

@Composable
private fun PlanContent(
    plan: HomeContent.Plan,
    /** While a session is open, other workouts can't be started. */
    activeSession: ActiveSession?,
    onOpenWorkout: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
) {
    val session = activeSession
    WorkoutsProgress(workouts = plan.workouts)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plan.workouts.forEach { workout ->
            when (workout.status) {
                is WorkoutStatus.Completed -> CompletedWorkoutCard(
                    workout = workout,
                    day = workout.status.day,
                    onClick = { onOpenWorkout(workout.id) },
                )
                WorkoutStatus.InProgress -> InProgressWorkoutCard(
                    workout = workout,
                    onClick = { onOpenWorkout(workout.id) },
                )
                WorkoutStatus.NotStarted -> if (session != null) {
                    BlockedWorkoutCard(
                        workout = workout,
                        blockedBy = session.workoutName,
                        onClick = { onOpenWorkout(workout.id) },
                    )
                } else {
                    StartableWorkoutCard(
                        workout = workout,
                        onOpen = { onOpenWorkout(workout.id) },
                        onStart = { onStartWorkout(workout.id) },
                    )
                }
            }
        }
    }
    if (session == null && plan.workouts.any { it.status == WorkoutStatus.NotStarted }) {
        Text(
            text = "Any unfinished workout this week can be started, in any order.",
            style = TextStyles.bodySm,
            color = Colors.TextSecondary,
        )
    }
}

@Composable
private fun ActiveSessionCard(session: ActiveSession, onResume: () -> Unit) {
    Surface(
        onClick = onResume,
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.xl,
        color = Colors.Primary,
        contentColor = Colors.OnPrimary,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Dot(color = Colors.OnPrimary)
                    Text(
                        text = "SESSION IN PROGRESS",
                        style = TextStyles.labelCaps.copy(fontWeight = FontWeight.Bold),
                    )
                }
                session.elapsed?.let {
                    Text(
                        text = it,
                        style = TextStyles.dataDisplayLg.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = session.workoutName.uppercase(),
                    style = TextStyles.headlineLg,
                )
                Text(
                    text = sessionLine(session),
                    style = TextStyles.bodyMd.copy(fontWeight = FontWeight.Medium),
                )
            }
            // Visual affordance only — the whole card is the click target.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(Colors.OnPrimary, Shapes.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Colors.Primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Resume session",
                    style = TextStyles.buttonLabel.copy(fontSize = 16.sp),
                    color = Colors.Primary,
                )
            }
        }
    }
}

/** "RDL · set 4 of 5", or while resting "RDL · resting 2:11 left · set 4 of 5 next". */
private fun sessionLine(session: ActiveSession): AnnotatedString = buildAnnotatedString {
    val exercise = session.exercise ?: return@buildAnnotatedString append("All exercises logged")
    if (session.reviewing) return@buildAnnotatedString append("$exercise · review the result")
    val set = session.set?.let { set -> "set $set" + (session.setCount?.let { " of $it" } ?: "") }
    append(exercise)
    val rest = session.rest
    if (rest == null) {
        set?.let { append(" · $it") }
        return@buildAnnotatedString
    }
    append(if (rest.paused) " · rest paused, " else " · resting ")
    withStyle(SpanStyle(fontFamily = TextStyles.dataValue.fontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
        append(rest.left)
    }
    append(" left")
    set?.let { append(" · $it next") }
}

@Composable
private fun WorkoutsProgress(workouts: List<WorkoutSummary>) {
    val done = workouts.count { it.status is WorkoutStatus.Completed }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("Workouts")
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            workouts.forEach { workout ->
                val color = when (workout.status) {
                    is WorkoutStatus.Completed -> Colors.Primary
                    WorkoutStatus.InProgress -> Colors.Primary.copy(alpha = 0.4f)
                    WorkoutStatus.NotStarted -> Colors.Border
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(color, Shapes.pill),
                )
            }
        }
        Text(
            text = "$done of ${workouts.size} done",
            style = TextStyles.bodyNoteStrong,
            color = Colors.TextSecondary,
        )
    }
}

@Composable
private fun CompletedWorkoutCard(workout: WorkoutSummary, day: String?, onClick: () -> Unit) {
    WorkoutCard(onClick = onClick) {
        WorkoutRow(
            workout = workout,
            badgeColor = Colors.Surface2,
            badgeTextColor = Colors.TextSecondary,
            subtitle = { SubtitleText("${plural(workout.exerciseCount, "exercise")} · Completed${day?.let { " $it" }.orEmpty()}") },
            trailing = {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Completed",
                    tint = Colors.Primary,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
    }
}

@Composable
private fun InProgressWorkoutCard(workout: WorkoutSummary, onClick: () -> Unit) {
    WorkoutCard(onClick = onClick, border = BorderStroke(1.5.dp, Colors.Primary)) {
        WorkoutRow(
            workout = workout,
            badgeColor = Colors.Primary,
            badgeTextColor = Colors.OnPrimary,
            subtitle = {
                Text(
                    text = buildAnnotatedString {
                        append("${plural(workout.exerciseCount, "exercise")} · ")
                        withStyle(SpanStyle(color = Colors.Primary, fontWeight = FontWeight.SemiBold)) {
                            append("In progress")
                        }
                    },
                    style = TextStyles.bodyNote,
                    color = Colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailing = { Chevron() },
        )
    }
}

@Composable
private fun BlockedWorkoutCard(workout: WorkoutSummary, blockedBy: String, onClick: () -> Unit) {
    WorkoutCard(onClick = onClick) {
        WorkoutRow(
            workout = workout,
            badgeColor = Colors.Surface2,
            badgeTextColor = Colors.TextSecondary,
            subtitle = { SubtitleText("${plural(workout.exerciseCount, "exercise")} · Finish $blockedBy to start") },
            trailing = { Chevron() },
        )
    }
}

@Composable
private fun StartableWorkoutCard(workout: WorkoutSummary, onOpen: () -> Unit, onStart: () -> Unit) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(onClick = onOpen, color = Color.Transparent) {
                WorkoutRow(
                    workout = workout,
                    badgeColor = Colors.Surface2,
                    badgeTextColor = Colors.TextPrimary,
                    subtitle = {
                        val supersets = workout.supersetCount
                        SubtitleText(
                            buildString {
                                append(plural(workout.exerciseCount, "exercise"))
                                if (supersets > 0) {
                                    append(" · $supersets superset")
                                    if (supersets > 1) append("s")
                                }
                            }
                        )
                    },
                    trailing = null,
                )
            }
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .heightIn(min = 48.dp),
                shape = Shapes.xl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Primary,
                    contentColor = Colors.OnPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Start ${workout.name}",
                    style = TextStyles.buttonLabel.copy(fontSize = 16.sp),
                )
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    onClick: () -> Unit,
    border: BorderStroke = CardBorder,
    content: @Composable () -> Unit,
) {
    Card(onClick = onClick, border = border, content = content)
}

@Composable
private fun WorkoutRow(
    workout: WorkoutSummary,
    badgeColor: Color,
    badgeTextColor: Color,
    subtitle: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(badgeColor, Shapes.xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = workout.name,
                style = TextStyles.headlineMd.copy(fontWeight = FontWeight.Bold),
                color = badgeTextColor,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workout.title,
                style = TextStyles.bodyLg.copy(fontWeight = FontWeight.SemiBold),
                color = Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle()
        }
        trailing?.invoke()
    }
}

@Composable
private fun SubtitleText(text: String) {
    Text(
        text = text,
        style = TextStyles.bodyNote,
        color = Colors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---- Rest week -----------------------------------------------------------

@Composable
private fun RestWeekContent() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DumbbellIllustration()
            Text(
                text = "REST WEEK",
                style = TextStyles.headlineLg.copy(fontSize = 34.sp, lineHeight = 36.sp),
                color = Colors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Your sheet marks this week as a break. Nothing to log until next week.",
                modifier = Modifier.widthIn(max = 290.dp),
                style = TextStyles.bodyMd.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = Colors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- No plan for this week -----------------------------------------------

@Composable
private fun NoPlanContent(
    noPlan: HomeContent.NoPlan,
    onOpenLatestWeek: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DumbbellIllustration()
            Text(
                text = "NO PLAN FOR THIS WEEK",
                style = TextStyles.headlineLg.copy(fontSize = 34.sp, lineHeight = 36.sp),
                color = Colors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = buildAnnotatedString {
                    if (noPlan.newestSheet == null) {
                        append("Nothing has been loaded from your spreadsheet yet.")
                    } else {
                        append("Your spreadsheet has no week starting ${noPlan.weekStart}. The newest sheet is ")
                        withStyle(SpanStyle(color = Colors.TextPrimary, fontWeight = FontWeight.Bold)) {
                            append(noPlan.newestSheet)
                        }
                        append(", ending ${noPlan.newestSheetEnd}.")
                    }
                },
                modifier = Modifier.widthIn(max = 290.dp),
                style = TextStyles.bodyMd.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = Colors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        noPlan.latestWeekRange?.let { latestWeekRange ->
            Button(
                onClick = onOpenLatestWeek,
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
                    text = "Open latest week · $latestWeekRange",
                    style = TextStyles.buttonLabel,
                )
            }
        }
        Button(
            onClick = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = Shapes.xl,
            colors = ButtonDefaults.buttonColors(
                containerColor = Colors.Surface2,
                contentColor = Colors.TextPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Check the sheet again",
                style = TextStyles.buttonLabel,
            )
        }
    }
    Text(
        text = "Add the new week to your sheet, then pull to refresh. Past weeks stay available offline.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        style = TextStyles.bodySm,
        color = Colors.TextSecondary,
        textAlign = TextAlign.Center,
    )
}

/** Dumbbell drawn on a 132×56 grid, matching the artboard's illustration. */
@Composable
private fun DumbbellIllustration() {
    val color = Colors.Border
    Canvas(modifier = Modifier.size(width = 132.dp, height = 56.dp)) {
        val u = size.width / 132f
        val radius = CornerRadius(3 * u)
        drawLine(
            color = color,
            start = Offset(0f, 28 * u),
            end = Offset(132 * u, 28 * u),
            strokeWidth = 5 * u,
            cap = StrokeCap.Round,
        )
        listOf(
            Triple(34f, 6f, Size(10f, 44f)),
            Triple(46f, 13f, Size(8f, 30f)),
            Triple(88f, 6f, Size(10f, 44f)),
            Triple(78f, 13f, Size(8f, 30f)),
        ).forEach { (x, y, plate) ->
            drawRoundRect(
                color = color,
                topLeft = Offset(x * u, y * u),
                size = Size(plate.width * u, plate.height * u),
                cornerRadius = radius,
            )
        }
    }
}

// ---- Shared bits ---------------------------------------------------------

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

// ---- Previews ------------------------------------------------------------

private val previewWorkouts = listOf(
    WorkoutSummary("t1", "T1", "Przysiad · Bench Press", 7, 1, WorkoutStatus.Completed("Mon")),
    WorkoutSummary("t2", "T2", "Half Tucked Front Lever · RDL", 7, 1, WorkoutStatus.NotStarted),
    WorkoutSummary("t3", "T3", "Przysiad · Bench Press", 7, 1, WorkoutStatus.NotStarted),
)

private val previewNoPlan = HomeContent.NoPlan(
    weekStart = "19 Oct",
    newestSheet = "WRZESIEŃ",
    newestSheetEnd = "20 Sep",
    latestWeekRange = "14 – 20 Sep",
)

@Composable
private fun HomeScreenPreviewContent(state: HomeUiState) {
    WorkoutTheme {
        HomeScreen(
            state = state,
            onRefresh = {},
            onResumeSession = {},
            onResumeRest = {},
            onOpenWorkout = {},
            onStartWorkout = {},
            onOpenLatestWeek = {},
            onTabSelected = {},
        )
    }
}

@Preview(name = "Session in progress", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeSessionInProgressPreview() {
    HomeScreenPreviewContent(
        HomeUiState(
            monthLabel = "June 2026",
            weekRange = "1 – 7 Jun",
            sync = SyncStatus.Synced("14:32"),
            activeSession = previewSession,
            content = HomeContent.Plan(
                workouts = previewWorkouts.map {
                    if (it.id == "t2") it.copy(status = WorkoutStatus.InProgress) else it
                },
            ),
        )
    )
}

@Preview(name = "Loading", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeSkeletonPreview() {
    WorkoutTheme { HomeSkeleton() }
}

private val previewSession = ActiveSession(
    workoutName = "T2",
    elapsed = "12:04",
    exercise = "RDL",
    set = 4,
    setCount = 5,
    rest = ActiveRest(left = "2:11"),
)

/** A session left open from last week stays reachable when this week has no plan. */
@Preview(name = "No plan, session open", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeNoPlanWithSessionPreview() {
    HomeScreenPreviewContent(
        HomeUiState(
            monthLabel = "October 2026",
            weekRange = "19 – 25 Oct",
            sync = SyncStatus.Synced("14:32"),
            activeSession = previewSession,
            content = previewNoPlan,
        )
    )
}

@Preview(name = "No session, offline", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeIdleOfflinePreview() {
    HomeScreenPreviewContent(
        HomeUiState(
            monthLabel = "June 2026",
            weekRange = "1 – 7 Jun",
            sync = SyncStatus.Offline("14:32"),
            content = HomeContent.Plan(workouts = previewWorkouts),
        )
    )
}

@Preview(name = "No session, online", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeIdleOnlinePreview() {
    HomeScreenPreviewContent(
        HomeUiState(
            monthLabel = "June 2026",
            weekRange = "1 – 7 Jun",
            sync = SyncStatus.Synced("14:32"),
            content = HomeContent.Plan(workouts = previewWorkouts),
        )
    )
}

@Preview(name = "Week complete", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeWeekCompletePreview() {
    HomeScreenPreviewContent(
        HomeUiState(
            monthLabel = "June 2026",
            weekRange = "1 – 7 Jun",
            sync = SyncStatus.Synced("14:32"),
            content = HomeContent.Plan(
                workouts = listOf(
                    previewWorkouts[0],
                    previewWorkouts[1].copy(status = WorkoutStatus.Completed("Wed")),
                    previewWorkouts[2].copy(status = WorkoutStatus.Completed("Fri")),
                ),
            ),
        )
    )
}

@Preview(name = "No plan this week", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeNoPlanPreview() {
    HomeScreenPreviewContent(
        HomeUiState(
            monthLabel = "October 2026",
            weekRange = "19 – 25 Oct",
            sync = SyncStatus.Synced("14:32"),
            content = previewNoPlan,
        )
    )
}

@Preview(name = "No plan this week, offline", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun HomeNoPlanOfflinePreview() {
    HomeScreenPreviewContent(
        HomeUiState(
            monthLabel = "October 2026",
            weekRange = "19 – 25 Oct",
            sync = SyncStatus.Offline("14:32"),
            content = previewNoPlan,
        )
    )
}
