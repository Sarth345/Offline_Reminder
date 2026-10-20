package com.example.offlinereminder.data.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.example.offlinereminder.OfflineReminderApplication
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.notification.NotificationPoster
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.notification.NotificationConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Triggered by [android.app.AlarmManager] when a scheduled notification time arrives.
 *
 * Verifies the authoritative persistence state before posting:
 * - Ensures the notification instance is still active (not cancelled, deleted, or dismissed).
 * - Ensures the underlying event is not trashed or deleted.
 * - Ensures the specific occurrence has not already been completed or skipped.
 * - Posts the notification and transitions the instance status to DELIVERED.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val instanceId = intent.getStringExtra(NotificationConstants.EXTRA_INSTANCE_ID) ?: return
        val eventId = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_ID) ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppDatabase.DATABASE_NAME
                ).build()

                val instanceEntity = db.notificationInstanceDao().getById(instanceId)
                if (instanceEntity == null ||
                    instanceEntity.status == "CANCELLED" ||
                    instanceEntity.status == "DISMISSED" ||
                    instanceEntity.status == "COMPLETED"
                ) {
                    return@launch
                }

                val eventAggregate = db.eventDao().getEventWithDetailsById(eventId)
                if (eventAggregate == null ||
                    eventAggregate.event.lifecycleStatus == EventLifecycleStatus.TRASHED
                ) {
                    db.notificationInstanceDao().updateStatus(instanceId, "CANCELLED")
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
                    db.notificationInstanceDao().updateStatus(instanceId, "CANCELLED")
                    return@launch
                }

                val eventDomain = eventAggregate.toDomain()
                val instanceDomain = instanceEntity.toDomain()

                val settings = try {
                    (context.applicationContext as? OfflineReminderApplication)?.container?.settingsRepository?.getSettings()
                } catch (_: Exception) {
                    null
                }

                val soundEnabled = settings?.soundEnabled ?: true
                val vibrationEnabled = settings?.vibrationEnabled ?: true
                val snoozeMinutes = settings?.defaultSnoozeDurationMinutes ?: 10
                val quietHours = settings?.quietHours

                val isSilentExtra = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_SILENT, false) ||
                    intent.getStringExtra(NotificationConstants.EXTRA_IS_SILENT)?.toBoolean() == true

                var isSilent = isSilentExtra
                if (quietHours != null && quietHours.enabled) {
                    val nowTime = LocalTime.now()
                    if (quietHours.isInQuietHours(nowTime)) {
                        val isExempt = quietHours.allowCritical &&
                            (eventDomain.priority == Priority.HIGH || eventDomain.priority == Priority.CRITICAL)
                        if (!isExempt && quietHours.policy == QuietHoursPolicy.SILENT) {
                            isSilent = true
                        }
                    }
                }

                val poster = NotificationPoster(context)
                val posted = poster.postNotification(
                    event = eventDomain,
                    instance = instanceDomain,
                    isSilent = isSilent,
                    soundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    snoozeDurationMinutes = snoozeMinutes
                )

                if (posted) {
                    db.notificationInstanceDao().updateStatus(instanceId, "DELIVERED")
                }
            } catch (ignored: Exception) {
                // Failsafe execution
            } finally {
                pendingResult.finish()
            }
        }
    }
}
