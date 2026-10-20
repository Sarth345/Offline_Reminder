package com.example.offlinereminder.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.offlinereminder.MainActivity
import com.example.offlinereminder.R
import com.example.offlinereminder.data.notification.receiver.NotificationActionReceiver
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.notification.NotificationConstants

/**
 * Handles creation of notification channels and posting of system tray notifications
 * with interactive actions (Complete, Snooze, Dismiss).
 */
class NotificationPoster(
    private val context: Context
) {

    private val notificationManager: NotificationManager? by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultChannel = NotificationChannel(
                NotificationConstants.CHANNEL_ID_DEFAULT,
                "Event Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Primary alerts for offline event reminders"
                enableVibration(true)
            }

            val silentChannel = NotificationChannel(
                NotificationConstants.CHANNEL_ID_SILENT,
                "Quiet Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Low-priority or quiet-hours alerts without sound or vibration"
                enableVibration(false)
            }

            notificationManager?.createNotificationChannels(listOf(defaultChannel, silentChannel))
        }
    }

    fun postNotification(
        event: Event,
        instance: NotificationInstance,
        isSilent: Boolean = false,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        snoozeDurationMinutes: Int = 10
    ): Boolean {
        val effectiveSilent = isSilent || !soundEnabled
        val channelId = if (effectiveSilent) {
            NotificationConstants.CHANNEL_ID_SILENT
        } else {
            NotificationConstants.CHANNEL_ID_DEFAULT
        }

        val notificationId = instance.requestCode

        // Content intent: Opens MainActivity with eventId
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationConstants.EXTRA_EVENT_ID, event.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Mark Complete
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationConstants.ACTION_COMPLETE
            putExtra(NotificationConstants.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instance.id)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, event.id)
            putExtra(NotificationConstants.EXTRA_OCCURRENCE_START, instance.occurrenceOriginalStartDateTime.toString())
            putExtra(NotificationConstants.EXTRA_OCCURRENCE_ZONE, instance.occurrenceOriginalZoneId.id)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Snooze (configurable duration from settings)
        val snoozeMillis = (snoozeDurationMinutes.coerceAtLeast(1)) * 60 * 1000L
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationConstants.ACTION_SNOOZE
            putExtra(NotificationConstants.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instance.id)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, event.id)
            putExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MILLIS, snoozeMillis)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Delete intent: Dismiss
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationConstants.ACTION_DISMISS
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instance.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(event.title)
            .setContentText(event.description.ifBlank { "Reminder" })
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setAutoCancel(true)
            .setPriority(if (effectiveSilent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Complete", completePendingIntent)
            .addAction(0, "Snooze", snoozePendingIntent)

        if (effectiveSilent) {
            builder.setSilent(true)
        }

        if (!vibrationEnabled || effectiveSilent) {
            builder.setVibrate(longArrayOf(0L))
        } else {
            builder.setVibrate(longArrayOf(0L, 250L, 250L, 250L))
        }

        val compat = NotificationManagerCompat.from(context)
        return try {
            compat.notify(notificationId, builder.build())
            true
        } catch (e: SecurityException) {
            // Target SDK 33+ without POST_NOTIFICATIONS permission
            false
        } catch (e: Exception) {
            false
        }
    }

    fun cancelNotification(notificationId: Int) {
        val compat = NotificationManagerCompat.from(context)
        compat.cancel(notificationId)
    }
}
