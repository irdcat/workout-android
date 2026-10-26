package com.github.irdcat.workout.infrastructure.settings

import android.app.Application
import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.settings.RestEndAlert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidRestEndNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = AndroidRestEndNotifier(context)

    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    @Test
    fun vibrateVibrates() {
        notifier.notify(RestEndAlert.Vibrate)
        assertTrue(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun bothVibratesAndPlaysTheSound() {
        notifier.notify(RestEndAlert.Both)
        assertTrue(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun soundAloneDoesNotVibrate() {
        notifier.notify(RestEndAlert.Sound)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun offDoesNothing() {
        notifier.notify(RestEndAlert.Off)
        assertFalse(shadowOf(vibrator).isVibrating)
    }
}
