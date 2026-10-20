package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.notification.NotificationScheduleCalculator
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.NotificationRepository
import com.example.offlinereminder.domain.repository.SettingsRepository

/**
 * Use case to project occurrences within a bounded horizon and schedule corresponding alarms.
 * Idempotently reconciles existing alarms: creates new ones, updates rescheduled ones, and cancels obsolete ones.
 */
class ScheduleNotificationsForEventUseCase(
    private val notificationRepository: NotificationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider,
    private val settingsRepository: SettingsRepository? = null
) {

    suspend operator fun invoke(
        event: Event,
        exceptions: List<OccurrenceException> = emptyList(),
        quietHours: QuietHours? = null,
        horizonDays: Long = NotificationConstants.DEFAULT_HORIZON_DAYS
    ): Result<Int> = Result.runCatching {
        val effectiveQuietHours = quietHours ?: settingsRepository?.getSettings()?.quietHours
        // If event is trashed or has no schedules, reconcile/cancel all existing
        if (event.isTrashed || event.notificationSchedules.isEmpty()) {
            val existing = notificationRepository.getInstancesForEvent(event.id)
            for (inst in existing) {
                if (inst.isPending) {
                    alarmScheduler.cancelAlarm(inst.requestCode, NotificationConstants.ACTION_ALARM)
                    notificationRepository.updateStatus(inst.id, NotificationInstanceStatus.CANCELLED)
                }
            }
            return@runCatching 0
        }

        val nowLdt = timeProvider.nowLocalDateTime()
        val horizonEnd = nowLdt.plusDays(horizonDays)
        val nowEpochMillis = timeProvider.nowInstant().toEpochMilli()

        // 1. Project occurrences in horizon
        val projectedOccurrences = recurrenceEngine.projectOccurrences(
            event = event,
            from = nowLdt.minusDays(1),
            to = horizonEnd,
            exceptions = exceptions
        ).filter { it.status == OccurrenceStatus.PENDING || it.status == OccurrenceStatus.RESCHEDULED }

        // 2. Calculate candidates
        val candidates = mutableListOf<NotificationScheduleCalculator.CalculatedNotification>()
        for (occ in projectedOccurrences) {
            for (schedule in event.notificationSchedules) {
                val calc = NotificationScheduleCalculator.calculate(
                    occurrence = occ,
                    schedule = schedule,
                    quietHours = effectiveQuietHours,
                    priority = event.priority
                )
                if (calc.triggerEpochMillis > nowEpochMillis) {
                    candidates.add(calc)
                }
            }
        }

        // 3. Query existing instances for this event
        val existingInstances = notificationRepository.getInstancesForEvent(event.id)
        val existingMap = existingInstances.associateBy { it.id }
        val candidateIds = candidates.map { it.instanceId }.toSet()

        // 4. Cancel any existing instances that are no longer valid candidates
        for (existing in existingInstances) {
            if (existing.isPending && !candidateIds.contains(existing.id)) {
                alarmScheduler.cancelAlarm(existing.requestCode, NotificationConstants.ACTION_ALARM)
                notificationRepository.updateStatus(existing.id, NotificationInstanceStatus.CANCELLED)
            }
        }

        // 5. Schedule candidates
        var scheduledCount = 0
        for (candidate in candidates) {
            val existing = existingMap[candidate.instanceId]
            val shouldSchedule = existing == null ||
                existing.status == NotificationInstanceStatus.CANCELLED ||
                existing.triggerEpochMillis != candidate.triggerEpochMillis

            if (shouldSchedule) {
                val instance = candidate.toInstance()
                notificationRepository.saveInstance(instance)
                val success = alarmScheduler.scheduleAlarm(
                    requestCode = candidate.requestCode,
                    triggerEpochMillis = candidate.triggerEpochMillis,
                    intentAction = NotificationConstants.ACTION_ALARM,
                    extras = mapOf(
                        NotificationConstants.EXTRA_INSTANCE_ID to candidate.instanceId,
                        NotificationConstants.EXTRA_EVENT_ID to candidate.eventId,
                        NotificationConstants.EXTRA_OCCURRENCE_START to candidate.occurrenceOriginalStartDateTime.toString(),
                        NotificationConstants.EXTRA_OCCURRENCE_ZONE to candidate.occurrenceOriginalZoneId.id,
                        NotificationConstants.EXTRA_IS_SILENT to candidate.isSilent.toString()
                    )
                )
                if (success) {
                    scheduledCount++
                }
            }
        }

        scheduledCount
    }
}
