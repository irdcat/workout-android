package com.github.irdcat.workout.application.session

import com.github.irdcat.workout.infrastructure.coroutines.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The ongoing notification for the session in progress. There's one at a time: showing replaces it. */
interface SessionNotification {

    fun show(content: SessionNotificationContent)

    fun hide()
}

/**
 * Shows the session in progress as a notification while the app is out of sight, the
 * way Home's card shows it, and takes it away once the app is back or the session ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionNotifier @Inject constructor(
    private val sessionStatus: SessionStatusSource,
    private val notification: SessionNotification,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    /** Runs for as long as the app does; [appInForeground] says whether any of its screens is visible. */
    fun start(appInForeground: Flow<Boolean>): Job = scope.launch {
        appInForeground
            .distinctUntilChanged()
            // The session is only followed while the app is in the background; the chronometer keeps its time.
            .flatMapLatest { foreground ->
                if (foreground) flowOf(null) else sessionStatus.changes.map { it?.toNotificationContent() }
            }
            .distinctUntilChanged()
            .collect { content -> if (content == null) notification.hide() else notification.show(content) }
    }
}
