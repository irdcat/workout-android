package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.MainNavigationBar
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import com.github.irdcat.workout.domain.settings.RestEndAlert

// ---- UI state ------------------------------------------------------------

@Immutable
data class SettingsUiState(
    val email: String,
    val sheetUrl: String,
    val blacklist: List<String>,
    val writeResults: Boolean,
    /** e.g. "Today 14:32". */
    val lastSynced: String,
    val savedSheets: Int,
    val savedWeeks: Int,
    val isSyncing: Boolean = false,
    /** Results saved in the app that aren't in the sheet yet. */
    val unwrittenResults: Int = 0,
    val measureTime: Boolean,
    val autoRestTimer: Boolean,
    val keepScreenOn: Boolean,
    val restAlert: RestEndAlert,
    /** One of 0.5, 1, 2.5, 5. */
    val weightStep: String,
    val prefillWeight: Boolean,
    val hideAnalysisTab: Boolean = false,
    /** e.g. "1.0". */
    val versionName: String,
)

private val RestEndAlert.label: String
    get() = when (this) {
        RestEndAlert.Vibrate -> "Vibrate"
        RestEndAlert.Sound -> "Sound"
        RestEndAlert.Both -> "Both"
        RestEndAlert.Off -> "Off"
    }

sealed interface SettingsEvent {
    data object SignOut : SettingsEvent
    data object EditSpreadsheet : SettingsEvent
    data object EditBlacklist : SettingsEvent
    data class WriteResultsChanged(val enabled: Boolean) : SettingsEvent
    data object SyncNow : SettingsEvent
    /** Sent only after the user confirms. */
    data object ClearSavedData : SettingsEvent
    data class MeasureTimeChanged(val enabled: Boolean) : SettingsEvent
    data class AutoRestTimerChanged(val enabled: Boolean) : SettingsEvent
    data class KeepScreenOnChanged(val enabled: Boolean) : SettingsEvent
    data class RestAlertSelected(val alert: RestEndAlert) : SettingsEvent
    data class WeightStepSelected(val step: String) : SettingsEvent
    data class PrefillWeightChanged(val enabled: Boolean) : SettingsEvent
    data class HideAnalysisTabChanged(val hidden: Boolean) : SettingsEvent
}

private val WeightSteps = listOf("0.5", "1", "2.5", "5")

// ---- Route ---------------------------------------------------------------

/** Settings backed by [SettingsViewModel]; only navigation leaves this composable. */
@Composable
fun SettingsRoute(
    onEditSpreadsheet: () -> Unit,
    onSignedOut: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            SettingsNavigation.SignedOut -> onSignedOut()
        }
    }
    when (val settings = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> SettingsScreen(
            state = settings.value,
            onEvent = { event ->
                when (event) {
                    // Spreadsheet link and blacklist are both edited on the setup screen.
                    SettingsEvent.EditSpreadsheet, SettingsEvent.EditBlacklist -> onEditSpreadsheet()
                    else -> viewModel.onEvent(event)
                }
            },
            onTabSelected = onTabSelected,
        )
    }
}

// ---- Screen --------------------------------------------------------------

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Colors.Canvas,
        bottomBar = { MainNavigationBar(selected = MainTab.Settings, onSelect = onTabSelected) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                SectionLabel("Workout", modifier = Modifier)
                Text(
                    text = "SETTINGS",
                    style = KineticType.screenTitle,
                    color = Colors.TextPrimary,
                )
            }

            SettingsCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                AccountRow(email = state.email, onSignOut = { onEvent(SettingsEvent.SignOut) })
            }

            Section("Spreadsheet") {
                SettingRow(
                    title = "Spreadsheet link",
                    subtitle = state.sheetUrl,
                    singleLineSubtitle = true,
                    onClick = { onEvent(SettingsEvent.EditSpreadsheet) },
                    trailing = { Chevron() },
                )
                Divider()
                SettingRow(
                    title = "Sheet blacklist",
                    subtitle = if (state.blacklist.isEmpty()) "No sheets excluded" else state.blacklist.joinToString(", "),
                    singleLineSubtitle = true,
                    onClick = { onEvent(SettingsEvent.EditBlacklist) },
                    trailing = {
                        if (state.blacklist.isNotEmpty()) {
                            Text(
                                text = if (state.blacklist.size == 1) "1 sheet" else "${state.blacklist.size} sheets",
                                style = KineticType.bodyMd,
                                color = Colors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                        Chevron()
                    },
                )
                Divider()
                SwitchRow(
                    title = "Write results to the sheet",
                    subtitle = "Logged sets and notes go into each exercise's WYNIK cell.",
                    checked = state.writeResults,
                    onCheckedChange = { onEvent(SettingsEvent.WriteResultsChanged(it)) },
                )
            }

            Section("Sync and offline data") {
                SettingRow(
                    title = "Last synced",
                    subtitle = "${state.lastSynced} · ${plural(state.savedSheets, "sheet")}, " +
                        "${plural(state.savedWeeks, "week")} saved on this phone" +
                        if (state.unwrittenResults > 0) ". ${plural(state.unwrittenResults, "result")} not in the sheet yet" else "",
                    trailing = {
                        RowButton(
                            text = if (state.isSyncing) "Syncing…" else "Sync now",
                            enabled = !state.isSyncing,
                            onClick = { onEvent(SettingsEvent.SyncNow) },
                            leading = if (state.isSyncing) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Colors.TextSecondary,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    },
                )
                Divider()
                SettingRow(
                    title = "Clear saved data",
                    subtitle = "Deletes the local copy and reloads from the sheet. Unsynced results are kept.",
                    trailing = {
                        RowButton(
                            text = "Clear",
                            contentColor = Colors.Critical,
                            enabled = !state.isSyncing,
                            onClick = { confirmClear = true },
                        )
                    },
                )
            }

            Section("Workout") {
                SwitchRow(
                    title = "Measure workout time",
                    subtitle = "Starts at your first logged set and stops when you finish. Shown on the summary.",
                    checked = state.measureTime,
                    onCheckedChange = { onEvent(SettingsEvent.MeasureTimeChanged(it)) },
                )
                Divider()
                SwitchRow(
                    title = "Start rest timer automatically",
                    subtitle = "After each set that has a rest time. Sets with no rest never start one.",
                    checked = state.autoRestTimer,
                    onCheckedChange = { onEvent(SettingsEvent.AutoRestTimerChanged(it)) },
                )
                Divider()
                SwitchRow(
                    title = "Keep screen on",
                    subtitle = "While a session is open.",
                    checked = state.keepScreenOn,
                    onCheckedChange = { onEvent(SettingsEvent.KeepScreenOnChanged(it)) },
                )
                Divider()
                SegmentedSetting(
                    title = "When rest ends",
                    options = RestEndAlert.entries,
                    selected = state.restAlert,
                    label = { it.label },
                    onSelect = { onEvent(SettingsEvent.RestAlertSelected(it)) },
                )
            }

            Section("Logging") {
                SegmentedSetting(
                    title = "Default weight step (kg)",
                    options = WeightSteps,
                    selected = state.weightStep,
                    label = { it },
                    onSelect = { onEvent(SettingsEvent.WeightStepSelected(it)) },
                )
                Divider()
                SwitchRow(
                    title = "Prefill weight from last time",
                    subtitle = "For sets that only give an RPE target.",
                    checked = state.prefillWeight,
                    onCheckedChange = { onEvent(SettingsEvent.PrefillWeightChanged(it)) },
                )
            }

            Section("Beta features") {
                SwitchRow(
                    title = "Hide Analysis tab",
                    subtitle = "Leaves Analysis out of the navigation bar. Nothing logged is lost; turn it off to bring the tab back.",
                    checked = state.hideAnalysisTab,
                    onCheckedChange = { onEvent(SettingsEvent.HideAnalysisTabChanged(it)) },
                )
            }

            Text(
                text = "Workout ${state.versionName} · Weights in kg",
                modifier = Modifier.fillMaxWidth(),
                style = KineticType.bodySm,
                color = Colors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Colors.Surface1,
            titleContentColor = Colors.TextPrimary,
            textContentColor = Colors.TextSecondary,
            title = { Text("Clear saved data?") },
            text = {
                Text("The local copy of your spreadsheet is deleted and loaded again. Results that haven't synced yet are kept.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onEvent(SettingsEvent.ClearSavedData)
                    },
                ) {
                    Text("Clear", color = Colors.Critical, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", color = Colors.TextPrimary)
                }
            },
        )
    }
}

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

// ---- Building blocks -----------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        SectionLabel(title, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp))
        SettingsCard(content = content)
    }
}

@Composable
private fun SettingsCard(
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.xl,
        color = Colors.Surface1,
        border = BorderStroke(1.dp, Colors.Border),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun AccountRow(email: String, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Colors.Surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = email.firstOrNull()?.uppercase() ?: "",
                style = KineticType.dataValue.copy(fontSize = 20.sp),
                color = Colors.Primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = email,
                style = KineticType.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                color = Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "Signed in with Google", style = KineticType.bodySm, color = Colors.TextSecondary)
        }
        RowButton(text = "Sign out", onClick = onSignOut)
    }
}

/** A row with a title, optional subtitle and trailing content; the whole row is clickable when [onClick] is set. */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    singleLineSubtitle: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = KineticType.rowTitle,
                color = Colors.TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 1.dp),
                    style = KineticType.bodySm,
                    color = Colors.TextSecondary,
                    maxLines = if (singleLineSubtitle) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trailing()
        }
    }
}

/** The whole row toggles, so the label is part of the switch's touch target. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Colors.Primary,
                    checkedThumbColor = Colors.OnPrimary,
                    checkedBorderColor = Colors.Primary,
                    uncheckedTrackColor = Colors.Surface3,
                    uncheckedThumbColor = Colors.TextSecondary,
                    uncheckedBorderColor = Colors.Surface3,
                ),
            )
        },
    )
}

@Composable
private fun <T> SegmentedSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(bottom = 8.dp),
            style = KineticType.rowTitle,
            color = Colors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = Shapes.lg,
                    color = if (isSelected) Colors.Primary else Colors.Surface2,
                    contentColor = if (isSelected) Colors.OnPrimary else Colors.TextPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label(option),
                            style = KineticType.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentColor: Color = Colors.TextPrimary,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(44.dp),
        shape = Shapes.xl,
        colors = ButtonDefaults.buttonColors(
            containerColor = Colors.Surface2,
            contentColor = contentColor,
            disabledContainerColor = Colors.Surface2,
            disabledContentColor = Colors.TextSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (leading != null) {
            leading()
            Box(modifier = Modifier.size(8.dp))
        }
        Text(
            text = text,
            style = KineticType.bodyNoteStrong,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = KineticType.labelCaps,
        color = Colors.TextSecondary,
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(color = Colors.Border)
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = Colors.TextSecondary,
        modifier = Modifier.size(20.dp),
    )
}

// ---- Previews ------------------------------------------------------------

private val previewSettings = SettingsUiState(
    email = "your.name@gmail.com",
    sheetUrl = "docs.google.com/spreadsheets/d/1dpBR…",
    blacklist = listOf("Zawodnik", "Ogólne"),
    writeResults = true,
    lastSynced = "Today 14:32",
    savedSheets = 8,
    savedWeeks = 30,
    measureTime = true,
    autoRestTimer = true,
    keepScreenOn = true,
    restAlert = RestEndAlert.Vibrate,
    weightStep = "2.5",
    prefillWeight = true,
    versionName = "1.0",
)

@Preview(name = "Settings", widthDp = 390, heightDp = 1260, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun SettingsPreview() {
    WorkoutTheme {
        SettingsScreen(state = previewSettings, onEvent = {}, onTabSelected = {})
    }
}

@Preview(name = "Syncing, nothing excluded", widthDp = 390, heightDp = 1260, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun SettingsSyncingPreview() {
    WorkoutTheme {
        SettingsScreen(
            state = previewSettings.copy(
                blacklist = emptyList(),
                writeResults = false,
                isSyncing = true,
                autoRestTimer = false,
                restAlert = RestEndAlert.Both,
                weightStep = "1",
            ),
            onEvent = {},
            onTabSelected = {},
        )
    }
}
