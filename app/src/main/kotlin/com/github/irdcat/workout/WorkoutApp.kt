package com.github.irdcat.workout

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.github.irdcat.workout.application.session.SessionNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltAndroidApp
class WorkoutApp : Application() {

    @Inject lateinit var sessionNotifier: SessionNotifier

    override fun onCreate() {
        super.onCreate()
        // Started (visible) means some screen of the app is on display; below that it's in the background.
        sessionNotifier.start(
            ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
        )
    }
}
