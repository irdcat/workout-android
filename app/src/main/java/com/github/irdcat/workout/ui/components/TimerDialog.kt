package com.github.irdcat.workout.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.material3.colorScheme
import com.composables.composetheme.textStyles
import com.composables.composetheme.xl2
import com.github.irdcat.workout.ui.components.icons.ArrowLeft
import com.github.irdcat.workout.ui.components.icons.ArrowRight

private enum class CounterVariant {
    Down,
    Up;

    val displayText: String
        get() = when(this) {
            Down -> "Count down"
            Up -> "Count up"
        }
}

@Composable
fun TimerDialog(onClose: () -> Unit) {
    var selectedVariant by remember { mutableStateOf(CounterVariant.Down) }
    var selectedSeconds by remember { mutableIntStateOf(0) }
    var selectedDelay by remember { mutableIntStateOf(0) }
    var isOnCountingScreen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnClickOutside = false,
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = ComposeTheme.colorScheme.secondaryContainer,
                contentColor = ComposeTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Timer",
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    style = ComposeTheme.textStyles.xl2,
                    color = ComposeTheme.colorScheme.primary
                )
                AnimatedVisibility(
                    visible = !isOnCountingScreen
                ) { }
                if (!isOnCountingScreen) {
                    TimerDialogInitialScreen(
                        selectedVariant = selectedVariant,
                        onChooseVariant = {
                            selectedVariant = it
                        },
                        selectedCounter = selectedSeconds,
                        onChangeCounter = {
                            selectedSeconds = it
                        },
                        selectedDelay = selectedDelay,
                        onChangeDelay = {
                            selectedDelay = it
                        },
                        onCloseDialog = onClose,
                        onStartCounting = {
                            isOnCountingScreen = true
                        }
                    )
                } else {
                    TimerDialogCountingScreen(
                        selectedVariant = selectedVariant,
                        counterSeconds = selectedSeconds,
                        delaySeconds = selectedDelay,
                        onAbort = {
                            isOnCountingScreen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerDialogInitialScreen(
    selectedVariant: CounterVariant,
    onChooseVariant: (CounterVariant) -> Unit,
    selectedCounter: Int,
    onChangeCounter: (Int) -> Unit,
    selectedDelay: Int,
    onChangeDelay: (Int) -> Unit,
    onCloseDialog: () -> Unit,
    onStartCounting: () -> Unit
) {
    val counterSecondsOptions = (0..300 step 15).toList()
    val delaySecondsOptions = listOf(0, 3, 5, 10)

    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CounterVariant.entries.forEachIndexed { index, variant ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CounterVariant.entries.size
                    ),
                    selected = selectedVariant.ordinal == index,
                    label = {
                        Text(variant.displayText)
                    },
                    onClick = {
                        onChooseVariant(variant)
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContentColor = ComposeTheme.colorScheme.primary,
                        inactiveContentColor = ComposeTheme.colorScheme.inverseSurface,
                        activeBorderColor = ComposeTheme.colorScheme.primary,
                        inactiveBorderColor = ComposeTheme.colorScheme.inverseSurface
                    )
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Delay:",
                modifier = Modifier.weight(1f),
                color = ComposeTheme.colorScheme.inverseSurface
            )
        }
        Row(Modifier.fillMaxWidth()) {
            DurationPicker(
                selectedOption = selectedDelay,
                options = delaySecondsOptions,
                onChange = onChangeDelay
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Time:",
                modifier = Modifier.weight(1f),
                color = ComposeTheme.colorScheme.inverseSurface
            )
        }
        Row(Modifier.fillMaxWidth()) {
            DurationPicker(
                selectedOption = selectedCounter,
                options = counterSecondsOptions,
                onChange = onChangeCounter
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onCloseDialog, colors = ButtonDefaults.buttonColors(
            containerColor = ComposeTheme.colorScheme.errorContainer,
            contentColor = ComposeTheme.colorScheme.inverseSurface
        )) {
            Text("Close")
        }
        Button(onClick = onStartCounting, colors = ButtonDefaults.buttonColors(
            containerColor = ComposeTheme.colorScheme.primaryContainer,
            contentColor = ComposeTheme.colorScheme.inverseSurface
        )) {
            Text("Start")
        }
    }
}

@Composable
private fun TimerDialogCountingScreen(
    selectedVariant: CounterVariant,
    counterSeconds: Int,
    delaySeconds: Int,
    onAbort: () -> Unit
) {
    val startSeconds = if (selectedVariant == CounterVariant.Up) { 0 } else { counterSeconds }
    val targetSeconds = if (selectedVariant == CounterVariant.Up) { counterSeconds } else { 0 }

    var paused by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }

    TimerContainer(
        initialSeconds = startSeconds,
        targetSeconds = targetSeconds,
        delaySeconds = delaySeconds,
        isRunning = !paused && !finished,
        onFinish = { finished = true }
    ) { timerSeconds, remainingDelay ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth()) {
                CircularProgressIndicator(
                    progress = {
                        if (remainingDelay > 0) {
                            remainingDelay / delaySeconds.toFloat()
                        } else {
                            timerSeconds / counterSeconds.toFloat()
                        }
                    }
                )
                val text = if (remainingDelay > 0) {
                    "$remainingDelay"
                } else {
                    "$timerSeconds"
                }
                val color = if (remainingDelay > 0) {
                    ComposeTheme.colorScheme.error
                } else {
                    ComposeTheme.colorScheme.inverseSurface
                }
                Text(text, color = color)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (finished) {
                Button(
                    onClick = onAbort, colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeTheme.colorScheme.errorContainer,
                        contentColor = ComposeTheme.colorScheme.inverseSurface
                    )
                ) {
                    Text("Abort")
                }
            } else {
                Button(
                    onClick = { finished = true }, colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeTheme.colorScheme.errorContainer,
                        contentColor = ComposeTheme.colorScheme.inverseSurface
                    )
                ) {
                    Text("Stop")
                }
            }
            if (!paused && !finished) {
                Button(
                    onClick = { paused = true }, colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeTheme.colorScheme.primaryContainer,
                        contentColor = ComposeTheme.colorScheme.inverseSurface
                    )
                ) {
                    Text("Pause")
                }
            } else if (!finished) {
                Button(
                    onClick = { paused = false; }, colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeTheme.colorScheme.primaryContainer,
                        contentColor = ComposeTheme.colorScheme.inverseSurface
                    )
                ) {
                    Text("Play")
                }
            }
        }
    }
}

@Composable
private fun DurationPicker(
    selectedOption: Int,
    options: List<Int>,
    onChange: (Int) -> Unit,
) {
    val chosenIndex = options.indexOf(selectedOption)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(
            modifier = Modifier.size(36.dp),
            onClick = {
                if (chosenIndex != 0) {
                    val prevOption = options[chosenIndex - 1]
                    onChange(prevOption)
                }
            },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = ComposeTheme.colorScheme.inverseSurface
            )
        ) {
            Icon(ArrowLeft, contentDescription = null)
        }
        Text("$selectedOption",
            fontSize = 36.sp,
            color = ComposeTheme.colorScheme.inverseSurface
        )
        IconButton(
            modifier = Modifier.size(36.dp),
            onClick = {
                if (chosenIndex != options.size - 1) {
                    val nextOption = options[chosenIndex + 1]
                    onChange(nextOption)
                }
            },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = ComposeTheme.colorScheme.inverseSurface
            )
        ) {
            Icon(ArrowRight, contentDescription = null)
        }
    }
}