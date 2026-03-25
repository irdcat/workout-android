package com.github.irdcat.workout.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.material3.colorScheme
import com.composables.composetheme.textStyles
import com.composables.composetheme.xl2

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
    var selectedVariant by remember { mutableStateOf<CounterVariant?>(null) }
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
                            selectedSeconds = it
                        },
                        onCloseDialog = onClose
                    )
                } else {
                    TimerDialogCountingScreen(
                        selectedVariant = selectedVariant!!,
                        counterSeconds = selectedSeconds,
                        delaySeconds = selectedDelay
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerDialogInitialScreen(
    selectedVariant: CounterVariant?,
    onChooseVariant: (CounterVariant) -> Unit,
    selectedCounter: Int,
    onChangeCounter: (Int) -> Unit,
    selectedDelay: Int,
    onChangeDelay: (Int) -> Unit,
    onCloseDialog: () -> Unit,
) {
    val counterSecondsOptions = (0..300 step 15).toList()
    val delaySecondsOptions = listOf(3, 5, 10)

    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CounterVariant.entries.forEachIndexed { index, variant ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CounterVariant.entries.size
                    ),
                    selected = selectedVariant?.ordinal == index,
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Delay:", modifier = Modifier.weight(1f), color = ComposeTheme.colorScheme.inverseSurface)
        DurationPicker(
            selectedOption = selectedDelay,
            options = delaySecondsOptions,
            onChange = onChangeDelay
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Time:", modifier = Modifier.weight(1f), color = ComposeTheme.colorScheme.inverseSurface)
        DurationPicker(
            selectedOption = selectedCounter,
            options = counterSecondsOptions,
            onChange = onChangeCounter
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onCloseDialog, colors = ButtonDefaults.buttonColors(
            containerColor = ComposeTheme.colorScheme.errorContainer,
            contentColor = ComposeTheme.colorScheme.inverseSurface
        )) {
            Text("Close")
        }
    }
}

@Composable
private fun TimerDialogCountingScreen(
    selectedVariant: CounterVariant,
    counterSeconds: Int,
    delaySeconds: Int,
) {

}

@Composable
private fun DurationPicker(
    selectedOption: Int,
    options: List<Int>,
    onChange: (Int) -> Unit,
) {

}