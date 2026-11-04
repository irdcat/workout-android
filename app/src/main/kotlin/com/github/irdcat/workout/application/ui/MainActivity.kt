package com.github.irdcat.workout.application.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.session.ACTION_OPEN_SESSION
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Set by a tap on the session notification, until the session is open. */
    private val openSessionRequested = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only for a fresh start: after a recreation (e.g. rotation) the request was already handled.
        if (savedInstanceState == null) handle(intent)
        enableEdgeToEdge()
        setContent {
            val openSession by openSessionRequested.collectAsStateWithLifecycle()
            WorkoutTheme {
                AppNavHost(
                    openSessionRequested = openSession,
                    onOpenSessionHandled = { openSessionRequested.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_SESSION) openSessionRequested.value = true
    }
}
