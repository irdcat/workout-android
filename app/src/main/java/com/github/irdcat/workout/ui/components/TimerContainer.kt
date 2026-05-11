package com.github.irdcat.workout.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun TimerContainer(
    initialSeconds: Int = 0,
    targetSeconds: Int = 0,
    delaySeconds: Int = 0,
    isRunning: Boolean = false,
    onFinish: () -> Unit = {},
    content: @Composable (timerSeconds: Int, remainingDelay: Int) -> Unit
) {
    var remainingDelay by remember { mutableIntStateOf(delaySeconds) }
    var timerProgress by remember { mutableIntStateOf(initialSeconds) }
    val isCountDownTimer = initialSeconds > targetSeconds

    LaunchedEffect(
        key1 = isRunning,
        key2 = initialSeconds
    ) {
        if (isRunning) {
            val startTimeMillis = System.currentTimeMillis()
            while (true) {
                val elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis
                val elapsedSeconds = elapsedTimeMillis.floorDiv(1000).toInt()
                if (delaySeconds > 0) {
                    remainingDelay -= elapsedSeconds
                } else {
                    timerProgress += if (isCountDownTimer) {
                        -elapsedSeconds
                    } else {
                        elapsedSeconds
                    }
                }
                if (timerProgress == targetSeconds) {
                    onFinish()
                    break
                }
                delay(1.seconds)
            }
        }
    }

    content.invoke(timerProgress, remainingDelay)
}