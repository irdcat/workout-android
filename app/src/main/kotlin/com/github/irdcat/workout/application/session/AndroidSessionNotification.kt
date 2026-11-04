package com.github.irdcat.workout.application.session

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.irdcat.workout.R
import com.github.irdcat.workout.application.ui.MainActivity
import com.github.irdcat.workout.application.ui.theme.Colors
import androidx.compose.ui.graphics.toArgb
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** The intent action that opens the session screen; the notification's tap sends it to [MainActivity]. */
const val ACTION_OPEN_SESSION = "com.github.irdcat.workout.action.OPEN_SESSION"

internal const val SESSION_CHANNEL_ID = "session"
internal const val SESSION_NOTIFICATION_ID = 1

/**
 * [SessionNotification] as an ongoing, silent Android notification. Without the
 * notification permission (Android 13+) nothing is shown.
 */
@Singleton
class AndroidSessionNotification @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SessionNotification {

    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // Checked by canNotify().
    override fun show(content: SessionNotificationContent) {
        if (!canNotify()) return
        createChannel()
        val builder = NotificationCompat.Builder(context, SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_session)
            .setColor(Colors.Primary.toArgb())
            .setSubText("Session in progress")
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(openSession())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val chronometer = content.chronometer
        if (chronometer == null) {
            builder.setShowWhen(false)
        } else {
            builder.setShowWhen(true)
                .setWhen(chronometer.base.toEpochMilli())
                .setUsesChronometer(true)
                .setChronometerCountDown(chronometer.countDown)
        }
        manager.notify(SESSION_NOTIFICATION_ID, builder.build())
    }

    override fun hide() {
        manager.cancel(SESSION_NOTIFICATION_ID)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(SESSION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Session in progress")
            .setDescription("The workout you're doing, while the app is in the background")
            .setShowBadge(false)
            .build()
        manager.createNotificationChannel(channel)
    }

    /** Back to the app, on the session; the activity is reused when it's still there. */
    private fun openSession(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SESSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionNotificationModule {

    @Binds
    abstract fun bindSessionNotification(impl: AndroidSessionNotification): SessionNotification
}
