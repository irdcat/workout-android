package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.irdcat.workout.application.ui.theme.Colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Screen state that is either still loading or ready to show. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
}

/** Applies [transform] only once the state is [Loadable.Ready]. */
inline fun <T> MutableStateFlow<Loadable<T>>.updateReady(transform: (T) -> T) {
    update { current -> if (current is Loadable.Ready) Loadable.Ready(transform(current.value)) else current }
}

/** Value of a ready state, or null while loading. */
fun <T> MutableStateFlow<Loadable<T>>.readyValue(): T? = (value as? Loadable.Ready)?.value

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = Colors.Canvas) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Colors.Primary)
        }
    }
}

/**
 * Collects one-off events (usually navigation) while the screen is at least
 * started, on the main thread so none are dropped during a configuration change.
 */
@Composable
fun <T> CollectEvents(events: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                events.collect { currentOnEvent(it) }
            }
        }
    }
}
