package com.example.offlinereminder.data.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
import kotlinx.coroutines.runBlocking
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
        val instanceId = intent.getStringExtra(NotificationConstants.EXTRA_INSTANCE_ID) ?: run {
            Log.w(TAG, "onReceive aborted: missing EXTRA_INSTANCE_ID")
            return
        }
        val eventId = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_ID) ?: run {
            Log.w(TAG, "onReceive aborted: missing EXTRA_EVENT_ID")
            return
        }

        Log.d(TAG, "onReceive triggered for instanceId='$instanceId', eventId='$eventId'")


        runBlocking(Dispatchers.IO) {
            try {
                val db = databaseOverride ?: try {
                    (context.applicationContext as? OfflineReminderApplication)?.container?.database
                        ?: AppDatabase.buildDatabase(context.applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error obtaining database instance in NotificationReceiver", e)
                    AppDatabase.buildDatabase(context.applicationContext)
                }

                val instanceEntity = db.notificationInstanceDao().getById(instanceId)
                val terminalStatuses = setOf("CANCELLED", "DISMISSED", "COMPLETED", "DELIVERED", "TIMED_OUT")
                if (instanceEntity == null || terminalStatuses.contains(instanceEntity.status)) {
                    Log.d(TAG, "Dropping trigger: instance is null or in terminal state: ${instanceEntity?.status}")
                    return@runBlocking
                }

                if (instanceEntity.status == NotificationInstanceStatus.RINGING.name &&
                    AlarmStateManager.getInstance().currentState() == AlarmState.RINGING &&
                    AlarmStateManager.getInstance().currentInstanceId() == instanceId
                ) {
                    Log.d(TAG, "Dropping duplicate trigger: already actively ringing instance '$instanceId'")
                    return@runBlocking
                }

                val eventAggregate = db.eventDao().getEventWithDetailsById(eventId)
                if (eventAggregate == null ||
                    eventAggregate.event.lifecycleStatus == EventLifecycleStatus.TRASHED
                ) {
                    Log.d(TAG, "Dropping trigger and cancelling instance: event is null or trashed")
                    db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.CANCELLED.name)
                    return@runBlocking
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
                    Log.d(TAG, "Dropping trigger: occurrence exception status is ${matchingException.status}")
                    db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.CANCELLED.name)
                    return@runBlocking
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
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load settings in NotificationReceiver", e)
                    null
                }

                // Resolve effective parameters
                val deliveryTypeFromIntent = intent.getStringExtra(NotificationConstants.EXTRA_DELIVERY_TYPE)?.let {
                    try {
                        ReminderDeliveryType.valueOf(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                var deliveryType = deliveryTypeFromIntent ?: schedule?.deliveryType ?: profile?.deliveryType ?: ReminderDeliveryType.NOTIFICATION
                if (deliveryType == ReminderDeliveryType.ALARM && settings?.alarmModeEnabled == false) {
                    Log.d(TAG, "Alarm mode is disabled in settings. Downgrading to notification.")
                    deliveryType = ReminderDeliveryType.NOTIFICATION
                }

                val defaultSoundForMode = if (deliveryType == ReminderDeliveryType.ALARM) {
                    settings?.alarmSound ?: SoundReference(SoundSourceType.DEFAULT)
                } else {
                    settings?.notificationSound ?: SoundReference(SoundSourceType.DEFAULT)
                }

                val rawSound = when {
                    schedule != null && (schedule.sound.type != SoundSourceType.DEFAULT || schedule.profileId == null) -> {
                        if (schedule.sound.type == SoundSourceType.DEFAULT) defaultSoundForMode else schedule.sound
                    }
                    profile != null -> {
                        if (profile.sound.type == SoundSourceType.DEFAULT) defaultSoundForMode else profile.sound
                    }
                    else -> defaultSoundForMode
                }

                val vibrationEnabled = profile?.vibrationEnabled ?: settings?.vibrationEnabled ?: true
                val snoozeMinutes = profile?.defaultSnoozeDurationMinutes ?: settings?.defaultSnoozeDurationMinutes ?: 15

                val isSilentExtra = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_SILENT, false) ||
                    intent.getStringExtra(NotificationConstants.EXTRA_IS_SILENT)?.toBoolean() == true
                var isSilent = isSilentExtra || (settings?.soundEnabled == false) || (rawSound.type == SoundSourceType.SILENT)

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
                    Log.d(TAG, "Executing ALARM delivery for '${eventAggregate.event.title}', sound=$effectiveSound")
                    // Update authoritative Room status to RINGING (active alarm session)
                    db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.RINGING.name)

                    try {
                        val occurrenceStart = instanceEntity.occurrenceOriginalStartDateTime.toString()
                        val occurrenceZone = instanceEntity.occurrenceOriginalZoneId
                        val location = eventAggregate.event.locationPlaceName?.ifBlank { null }
                            ?: eventAggregate.event.locationAddressText?.ifBlank { null }
                        AlarmPlaybackService.start(
                            context = context,
                            instanceId = instanceId,
                            eventId = eventId,
                            eventTitle = eventAggregate.event.title,
                            eventDesc = eventAggregate.event.description,
                            sound = effectiveSound,
                            vibrationEnabled = vibrationEnabled,
                            snoozeDurationMinutes = snoozeMinutes,
                            occurrenceStart = occurrenceStart,
                            occurrenceZone = occurrenceZone,
                            location = location
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "AlarmPlaybackService.start failed, falling back to NotificationPoster", e)
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
                    // Normal notification mode: check if notifications enabled in settings
                    if (settings?.notificationsEnabled == false) {
                        Log.d(TAG, "Notifications are disabled in settings. Skipping post.")
                        db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.DELIVERED.name)
                        return@runBlocking
                    }

                    Log.d(TAG, "Executing NOTIFICATION delivery for '${eventAggregate.event.title}', sound=$effectiveSound")
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
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled exception in NotificationReceiver", e)


            }
        }
    }

    companion object {
        private const val TAG = "PlanerizeReceiver"

        @Volatile
        var databaseOverride: AppDatabase? = null
    }
}
