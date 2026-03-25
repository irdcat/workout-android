package com.github.irdcat.workout.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.base
import com.composables.composetheme.colors
import com.composables.composetheme.gray300
import com.composables.composetheme.material3.colorScheme
import com.composables.composetheme.sm
import com.composables.composetheme.textStyles
import com.composables.composetheme.xl
import com.github.irdcat.workout.domain.model.ExerciseEntry
import com.github.irdcat.workout.domain.model.ExerciseSet
import com.github.irdcat.workout.domain.usecase.UpdateExerciseResultUseCase
import kotlinx.coroutines.launch

@Composable
fun ExerciseEntrySummary(entry: ExerciseEntry, updateExerciseResultUseCase: UpdateExerciseResultUseCase? = null) {
    var checkedExercises by rememberSaveable { mutableStateOf(Array(entry.sets.size) { entry.isCompleted() }) }
    val readonly = updateExerciseResultUseCase == null

    Card(
        colors = CardDefaults.cardColors(
            containerColor = ComposeTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
            Column(Modifier.weight(1f)) {
                entry.sets.forEachIndexed { index, set ->
                    if (index != 0) {
                        Row(Modifier.padding(horizontal = 16.dp)) {
                            Separator(1.dp, ComposeTheme.colors.gray300)
                        }
                    }
                    if (readonly) {
                        ExerciseSetSummary(set)
                    } else {
                        CheckableExerciseSetSummary(set, checkedExercises[index]) {
                            val new = checkedExercises.copyOf()
                            new[index] = it
                            checkedExercises = new
                        }
                    }
                }
            }
            ExerciseSetResultForm(entry, checkedExercises.all { it }, updateExerciseResultUseCase)
        }
    }
}

@Composable
fun ExerciseSetSummary(set: ExerciseSet) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.padding(4.dp)) {
            Text(set.name, style = ComposeTheme.textStyles.base)
        }
        Row(
            modifier = Modifier.padding(4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            val repsAndWeightString =
                set.reps.toString() + (set.weight.takeUnless { it.isNone() }?.let { " x $it" } ?: "")
            Text(repsAndWeightString, style = ComposeTheme.textStyles.sm, modifier = Modifier.weight(1f))
            set.tempo
                .takeUnless { it.isNotApplicable() }
                ?.toString()
                .orEmpty()
                .let {
                    Text(it, style = ComposeTheme.textStyles.sm, modifier = Modifier.weight(1f))
                }
            set.rest
                .takeUnless { it.isNone() }
                ?.toString()
                .orEmpty()
                .let {
                    Text(it, style = ComposeTheme.textStyles.sm, modifier = Modifier.weight(1f))
                }
        }
    }
}

@Composable
fun CheckableExerciseSetSummary(set: ExerciseSet, checked: Boolean, onCheck: (Boolean) -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
        Box(modifier = Modifier.weight(1f)) {
            ExerciseSetSummary(set)
        }
        Box(modifier = Modifier.padding(4.dp)) {
            Checkbox(checked, onCheck)
        }
    }
}

@Composable
fun ExerciseSetResultForm(entry: ExerciseEntry, enabled: Boolean, updateExerciseResultUseCase: UpdateExerciseResultUseCase? = null) {
    val readonly = updateExerciseResultUseCase == null
    var results by remember { mutableStateOf(entry.results) }
    val resultDestinations = entry.resultDestinations
    val coroutineScope = rememberCoroutineScope()
    var submissionLoading by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    Separator(2.dp)
    if (readonly) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Results", style = ComposeTheme.textStyles.xl, modifier = Modifier.fillMaxWidth())
            results.entries.forEach {
                val (exerciseName, result) = it
                OutlinedTextField(
                    enabled = false,
                    label = {
                        Text(exerciseName, style = ComposeTheme.textStyles.base)
                    },
                    value = result,
                    onValueChange = {}
                )
            }
        }
    } else {
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Results", style = ComposeTheme.textStyles.xl, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                results.entries.forEach {
                    val (exerciseName, result) = it
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(exerciseName, style = ComposeTheme.textStyles.base)
                        },
                        value = result,
                        onValueChange = { newValue ->
                            results = results + (exerciseName to newValue)
                        }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    if (submissionLoading) {
                        CircularProgressIndicator()
                        Spacer(Modifier.weight(1f))
                    }
                    FilledTonalButton(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeTheme.colorScheme.primaryContainer,
                            contentColor = ComposeTheme.colorScheme.primary
                        ),
                        onClick = {
                            coroutineScope.launch {
                                submissionLoading = true
                                val names = entry.results.keys
                                try {
                                    val references = mutableMapOf<String, String>()
                                    names.forEach { name ->
                                        references[resultDestinations[name]!!] = results[name]!!
                                    }
                                    updateExerciseResultUseCase.invoke(references)
                                    submissionError = null
                                } catch (e: Exception) {
                                    submissionError = e.message
                                } finally {
                                    submissionLoading = false
                                }
                            }
                        }
                    ) {
                        Text("Submit")
                    }
                }
                submissionError?.let {
                    Text("Error: $submissionError",
                        modifier = Modifier.fillMaxWidth(),
                        style = ComposeTheme.textStyles.base,
                        color = ComposeTheme.colorScheme.error)
                }
            }
        }
    }
}