package com.example.offlinereminder.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.offlinereminder.MainActivity
import com.example.offlinereminder.R
import com.example.offlinereminder.data.alarm.AlarmPlaybackService
import com.example.offlinereminder.data.notification.receiver.NotificationActionReceiver
import com.example.offlinereminder.data.sound.SoundContentProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.presentation.alarm.AlarmAlertActivity

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
                description = "Primary alerts for Planerize event reminders"
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

            val alarmChannel = NotificationChannel(
                NotificationConstants.CHANNEL_ID_ALARM,
                "Alarm Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active ringing alarm notifications"
                setSound(null, null)
                enableVibration(false)
            }

            notificationManager?.createNotificationChannels(listOf(defaultChannel, silentChannel, alarmChannel))
        }
    }

    private fun resolveNotificationChannelId(
        sound: SoundReference,
        isSilent: Boolean,
        vibrationEnabled: Boolean
    ): String {
        if (isSilent || sound.type == SoundSourceType.SILENT) {
            return NotificationConstants.CHANNEL_ID_SILENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            when (sound.type) {
                SoundSourceType.CUSTOM -> {
                    val customSoundId = sound.customSoundId
                    if (!customSoundId.isNullOrBlank()) {
                        val channelId = "offline_reminders_custom_${customSoundId}"
                        if (notificationManager?.getNotificationChannel(channelId) == null) {
                            val soundUri = SoundContentProvider.buildSoundUri(context, customSoundId)
                            val audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                            val customChannel = NotificationChannel(
                                channelId,
                                "Custom Sound Reminder",
                                NotificationManager.IMPORTANCE_HIGH
                            ).apply {
                                setSound(soundUri, audioAttributes)
                                enableVibration(vibrationEnabled)
                            }
                            notificationManager?.createNotificationChannel(customChannel)
                        }
                        return channelId
                    }
                }
                SoundSourceType.SYSTEM -> {
                    val uriString = sound.systemUriString
                    if (!uriString.isNullOrBlank()) {
                        val channelId = "offline_reminders_sys_${uriString.hashCode()}"
                        if (notificationManager?.getNotificationChannel(channelId) == null) {
                            val soundUri = Uri.parse(uriString)
                            val audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                            val sysChannel = NotificationChannel(
                                channelId,
                                "System Sound Reminder",
                                NotificationManager.IMPORTANCE_HIGH
                            ).apply {
                                setSound(soundUri, audioAttributes)
                                enableVibration(vibrationEnabled)
                            }
                            notificationManager?.createNotificationChannel(sysChannel)
                        }
                        return channelId
                    }
                }
                else -> { /* Use default channel */ }
            }
        }

        return NotificationConstants.CHANNEL_ID_DEFAULT
    }

    fun deleteCustomSoundChannel(customSoundId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "offline_reminders_custom_${customSoundId}"
            notificationManager?.deleteNotificationChannel(channelId)
        }
    }

    fun postNotification(
        event: Event,
        instance: NotificationInstance,
        sound: SoundReference = SoundReference(SoundSourceType.DEFAULT),
        isSilent: Boolean = false,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        snoozeDurationMinutes: Int = 10
    ): Boolean {
        val effectiveSilent = isSilent || !soundEnabled || sound.type == SoundSourceType.SILENT
        val channelId = resolveNotificationChannelId(sound, effectiveSilent, vibrationEnabled)
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

        // Action 2: Snooze (configurable duration from settings/profile)
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

    fun buildAlarmForegroundNotification(
        instanceId: String,
        eventId: String,
        eventTitle: String,
        eventDesc: String,
        snoozeDurationMinutes: Int = 10
    ): Notification {
        val notificationId = instanceId.hashCode()

        // Full screen alert intent (conditional)
        val fullScreenIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss
        val dismissIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_DISMISS_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
        }
        val dismissPendingIntent = PendingIntent.getService(
            context,
            notificationId + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze
        val snoozeMillis = (snoozeDurationMinutes.coerceAtLeast(1)) * 60 * 1000L
        val snoozeIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_SNOOZE_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MILLIS, snoozeMillis)
        }
        val snoozePendingIntent = PendingIntent.getService(
            context,
            notificationId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(eventTitle)
            .setContentText(eventDesc.ifBlank { "Alarm ringing" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Snooze", snoozePendingIntent)
            .build()
    }

    fun postMissedAlarmNotification(
        eventId: String,
        eventTitle: String,
        notificationId: Int
    ) {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID_DEFAULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Missed Alarm: $eventTitle")
            .setContentText("Alarm timed out after 10 minutes")
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }

    fun cancelNotification(notificationId: Int) {
        val compat = NotificationManagerCompat.from(context)
        compat.cancel(notificationId)
    }
}
