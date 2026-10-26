package com.github.irdcat.workout.infrastructure.settings

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.settings.RestEndNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Three short pulses: noticeable through a pocket, short enough not to annoy. */
private val VibrationPattern = longArrayOf(0, 250, 150, 250, 150, 250)

@Singleton
class AndroidRestEndNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RestEndNotifier {

    override fun notify(alert: RestEndAlert) {
        if (alert == RestEndAlert.Vibrate || alert == RestEndAlert.Both) vibrate()
        if (alert == RestEndAlert.Sound || alert == RestEndAlert.Both) playSound()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VibrationPattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationPattern, -1)
        }
    }

    /** The phone's notification sound, so it follows the user's volume and Do Not Disturb. */
    private fun playSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        RingtoneManager.getRingtone(context, uri)?.play()
    }
}
