package com.example.offlinereminder.data.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.offlinereminder.OfflineReminderApplication
import com.example.offlinereminder.data.alarm.AlarmPlaybackService
import com.example.offlinereminder.data.alarm.AlarmState
import com.example.offlinereminder.data.alarm.AlarmStateManager
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.mapper.*
import com.example.offlinereminder.data.notification.NotificationPoster
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.NotificationConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Triggered by [android.app.AlarmManager] when a scheduled notification time arrives.
 *
 * Verifies authoritative persistence state in Room before delivery:
 * - Drops trigger if the notification instance is in a terminal state (CANCELLED, DISMISSED, COMPLETED, DELIVERED, TIMED_OUT).
 * - Drops duplicate trigger if the instance is already actively RINGING.
 * - Drops trigger and marks cancelled if the event is trashed or occurrence is completed/skipped.
 * - Resolves effective delivery type, sound, and vibration settings from schedule, profile, and settings.
 * - Branches:
 *     - NOTIFICATION -> marks DELIVERED in Room and posts normal notification tray alert.
 *     - ALARM -> marks RINGING in Room and starts [AlarmPlaybackService] for continuous media playback.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val instanceId = intent.getStringExtra(NotificationConstants.EXTRA_INSTANCE_ID) ?: return
        val eventId = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_ID) ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = databaseOverride ?: try {
                    (context.applicationContext as? OfflineReminderApplication)?.container?.database
                        ?: AppDatabase.buildDatabase(context.applicationContext)
                } catch (_: Exception) {
                    AppDatabase.buildDatabase(context.applicationContext)
                }

                val instanceEntity = db.notificationInstanceDao().getById(instanceId)
                val terminalStatuses = setOf("CANCELLED", "DISMISSED", "COMPLETED", "DELIVERED", "TIMED_OUT")
                if (instanceEntity == null || terminalStatuses.contains(instanceEntity.status)) {
                    // Prevent duplicate delivery and do not resurrect terminal states
                    return@launch
                }

                if (instanceEntity.status == NotificationInstanceStatus.RINGING.name &&
                    AlarmStateManager.getInstance().currentState() == AlarmState.RINGING &&
                    AlarmStateManager.getInstance().currentInstanceId() == instanceId
                ) {
                    // Already actively ringing this instance; ignore duplicate trigger
                    return@launch
                }

                val eventAggregate = db.eventDao().getEventWithDetailsById(eventId)
                if (eventAggregate == null ||
                    eventAggregate.event.lifecycleStatus == EventLifecycleStatus.TRASHED
                ) {
                    db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.CANCELLED.name)
                    return@launch
                }

                // Check occurrence exceptions
                val exceptions = db.occurrenceExceptionDao().getExceptionsForEvent(eventId)
                val matchingException = exceptions.firstOrNull {
                    it.originalStartDateTime == instanceEntity.occurrenceOriginalStartDateTime &&
                        it.originalZoneId == instanceEntity.occurrenceOriginalZoneId
                }

                if (matchingException != null &&
                    (matchingException.status == OccurrenceStatus.COMPLETED ||
                        matchingException.status == OccurrenceStatus.SKIPPED ||
                        matchingException.status == OccurrenceStatus.EXCLUDED)
                ) {
                    db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.CANCELLED.name)
                    return@launch
                }

                // Resolve schedule and profile
                val schedule = instanceEntity.scheduleId?.let { sId ->
                    db.notificationScheduleDao().getById(sId)?.toDomain()
                }

                val profile = schedule?.profileId?.let { pId ->
                    db.notificationProfileDao().getById(pId)?.toDomain()
                }

                val settings = try {
                    (context.applicationContext as? OfflineReminderApplication)?.container?.settingsRepository?.getSettings()
                } catch (_: Exception) {
                    null
                }

                // Resolve effective parameters
                val deliveryType = schedule?.deliveryType ?: profile?.deliveryType ?: ReminderDeliveryType.NOTIFICATION

                val rawSound = when {
                    schedule != null && (schedule.sound.type != SoundSourceType.DEFAULT || schedule.profileId == null) -> schedule.sound
                    profile != null -> profile.sound
                    else -> SoundReference(SoundSourceType.DEFAULT)
                }

                val vibrationEnabled = profile?.vibrationEnabled ?: settings?.vibrationEnabled ?: true
                val snoozeMinutes = profile?.defaultSnoozeDurationMinutes ?: settings?.defaultSnoozeDurationMinutes ?: 10

                val isSilentExtra = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_SILENT, false) ||
                    intent.getStringExtra(NotificationConstants.EXTRA_IS_SILENT)?.toBoolean() == true
                var isSilent = isSilentExtra || (settings?.soundEnabled == false)

                val quietHours = settings?.quietHours
                if (quietHours != null && quietHours.enabled) {
                    val nowTime = LocalTime.now()
                    if (quietHours.isInQuietHours(nowTime)) {
                        val isExempt = quietHours.allowCritical &&
                            (eventAggregate.event.priority == Priority.HIGH || eventAggregate.event.priority == Priority.CRITICAL)
                        if (!isExempt && quietHours.policy == QuietHoursPolicy.SILENT) {
                            isSilent = true
                        }
                    }
                }

                val effectiveSound = if (isSilent) SoundReference(SoundSourceType.SILENT) else rawSound

                // Branch by delivery type
                if (deliveryType == ReminderDeliveryType.ALARM) {
                    // Update authoritative Room status to RINGING (active alarm session)
                    db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.RINGING.name)

                    try {
                        AlarmPlaybackService.start(
                            context = context,
                            instanceId = instanceId,
                            eventId = eventId,
                            eventTitle = eventAggregate.event.title,
                            eventDesc = eventAggregate.event.description,
                            sound = effectiveSound,
                            vibrationEnabled = vibrationEnabled,
                            snoozeDurationMinutes = snoozeMinutes
                        )
                    } catch (e: Exception) {
                        // In case of foreground service start failure, gracefully fallback to notification poster
                        val poster = NotificationPoster(context)
                        poster.postNotification(
                            event = eventAggregate.toDomain(),
                            instance = instanceEntity.toDomain().copy(status = NotificationInstanceStatus.DELIVERED),
                            sound = effectiveSound,
                            isSilent = isSilent,
                            soundEnabled = settings?.soundEnabled ?: true,
                            vibrationEnabled = vibrationEnabled,
                            snoozeDurationMinutes = snoozeMinutes
                        )
                        db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.DELIVERED.name)
                    }
                } else {
                    // Normal notification mode: mark DELIVERED and post notification
                    db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.DELIVERED.name)

                    val poster = NotificationPoster(context)
                    poster.postNotification(
                        event = eventAggregate.toDomain(),
                        instance = instanceEntity.toDomain().copy(status = NotificationInstanceStatus.DELIVERED),
                        sound = effectiveSound,
                        isSilent = isSilent,
                        soundEnabled = settings?.soundEnabled ?: true,
                        vibrationEnabled = vibrationEnabled,
                        snoozeDurationMinutes = snoozeMinutes
                    )
                }
            } catch (ignored: Exception) {
                // Failsafe execution
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        @Volatile
        var databaseOverride: AppDatabase? = null
    }
}