package com.github.irdcat.workout.infrastructure.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.github.irdcat.workout.domain.settings.RestAlarm
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.settings.RestEndNotifier
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val EXTRA_ALERT = "alert"

/**
 * The rest alert as an [AlarmManager] alarm, so it goes off with the app in the
 * background or closed. Exact when the user allows "Alarms & reminders" (Android 12+);
 * otherwise Android may deliver it a little late.
 */
@Singleton
class AndroidRestAlarm @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RestAlarm {

    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAt: Instant, alert: RestEndAlert) {
        val alarms = alarmManager ?: return
        if (alert == RestEndAlert.Off) {
            cancel()
            return
        }
        val at = endsAt.toEpochMilli()
        val intent = pendingIntent(alert)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override fun cancel() {
        // Extras don't take part in matching, so any alert finds the scheduled one.
        alarmManager?.cancel(pendingIntent(RestEndAlert.Off))
    }

    private fun pendingIntent(alert: RestEndAlert): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RestAlarmReceiver::class.java).putExtra(EXTRA_ALERT, alert.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Goes off when a rest ends and alerts the way the alarm was set up. */
@AndroidEntryPoint
class RestAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: RestEndNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val alert = intent.getStringExtra(EXTRA_ALERT)?.let { name -> RestEndAlert.entries.firstOrNull { it.name == name } }
        if (alert != null && alert != RestEndAlert.Off) notifier.notify(alert)
    }
}
