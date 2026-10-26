package com.github.irdcat.workout.infrastructure.settings

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.testing.RecordingRestEndNotifier
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import javax.inject.Inject

/** The alarm as Android's AlarmManager sees it (Robolectric's). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidRestAlarmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val alarm = AndroidRestAlarm(context)
    private val endsAt = Instant.parse("2026-06-03T18:02:30Z")

    @Before
    fun allowExactAlarms() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @Test
    fun anExactAlarmForTheEndOfTheRest() {
        alarm.schedule(endsAt, RestEndAlert.Both)

        val scheduled = alarms.peekNextScheduledAlarm()!!
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.getType())
        assertEquals(endsAt.toEpochMilli(), scheduled.triggerAtMs)
        // The alarm's broadcast, as it stands (the alert travels in it).
        val broadcast = PendingIntent.getBroadcast(
            context, 0, Intent(context, RestAlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        assertEquals("Both", shadowOf(broadcast).savedIntent.getStringExtra("alert"))
        assertEquals(true, scheduled.isAllowWhileIdle)
    }

    @Test
    fun schedulingAgainReplacesTheAlarm() {
        alarm.schedule(endsAt, RestEndAlert.Vibrate)
        alarm.schedule(endsAt.plusSeconds(15), RestEndAlert.Vibrate)

        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(endsAt.plusSeconds(15).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun cancellingOrTurningTheAlertOffRemovesIt() {
        alarm.schedule(endsAt, RestEndAlert.Vibrate)
        alarm.cancel()
        assertNull(alarms.peekNextScheduledAlarm())

        alarm.schedule(endsAt, RestEndAlert.Vibrate)
        alarm.schedule(endsAt, RestEndAlert.Off)
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun withoutExactAlarmsAllowedItIsStillSetJustInexact() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        alarm.schedule(endsAt, RestEndAlert.Vibrate)

        assertEquals(endsAt.toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtMs)
    }
}

/** The receiver the alarm goes off in, with Hilt providing a recording notifier. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class RestAlarmReceiverTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var notifier: RecordingRestEndNotifier

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun inject() = hilt.inject()

    private fun goOff(alert: String?) = RestAlarmReceiver().onReceive(
        context,
        Intent(context, RestAlarmReceiver::class.java).apply { alert?.let { putExtra("alert", it) } },
    )

    @Test
    fun alertsTheWayTheAlarmWasSet() {
        goOff("Sound")
        assertEquals(listOf(RestEndAlert.Sound), notifier.alerts)
    }

    @Test
    fun anUnknownOrMissingAlertDoesNothing() {
        goOff(null)
        goOff("Loud")
        goOff("Off")
        assertEquals(emptyList<RestEndAlert>(), notifier.alerts)
    }
}
