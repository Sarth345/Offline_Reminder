package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.notification.NotificationScheduleCalculator
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.NotificationRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import java.time.LocalDateTime
import java.time.ZoneId

fun interface ImmediateNotificationDeliverer {
    fun deliverImmediate(event: Event, instance: NotificationInstance, missedCount: Int)
}

/**
 * Domain use case coordinating user rescheduling of an occurrence:
 * - Hard invariant: Original notification/alarm schedule is cancelled and deleted BEFORE new schedule is created.
 * - Handles snooze: Cancel/delete snoozed instance so old snooze never fires against rescheduled event.
 * - Handles active ringing: Invalidate/stop active ringing alarm before rescheduling.
 * - Future reminders: Schedules AlarmManager alarms.
 * - Past reminder triggers (already due): Delivers immediately (consolidated into ONE notification if multiple).
 * - Exactly-once delivery: Persists delivered state to prevent resurrection after reboot.
 * - Timezone and DST preserved throughout calculation.
 */
class RescheduleOccurrenceActionUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val rescheduleOccurrenceUseCase: RescheduleOccurrenceUseCase = RescheduleOccurrenceUseCase(),
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val timeProvider: TimeProvider,
    private val notificationRepository: NotificationRepository? = null,
    private val alarmScheduler: AlarmScheduler? = null,
    private val stopActiveAlarm: (() -> Unit)? = null,
    private val immediateNotificationDeliverer: ImmediateNotificationDeliverer? = null
) {
    suspend operator fun invoke(
        occurrence: EventOccurrence,
        newStartDateTime: LocalDateTime,
        newEndDateTime: LocalDateTime? = null,
        newZoneId: ZoneId = occurrence.zoneId
    ): Result<Unit> = Result.runCatching {
        val event = eventRepository.getEvent(occurrence.eventId)
            ?: throw IllegalArgumentException("Event not found with id: ${occurrence.eventId}")

        val nowLdt = timeProvider.nowLocalDateTime()
        val nowEpochMillis = timeProvider.nowInstant().toEpochMilli()

        // 1 & 2. Identify affected occurrence and its existing notification instances
        val targetStart = occurrence.originalStartDateTime
        val targetZone = occurrence.zoneId
        val existingInstances = notificationRepository?.getInstancesForEvent(event.id) ?: emptyList()
        val oldInstancesForOccurrence = existingInstances.filter { inst ->
            inst.occurrenceOriginalStartDateTime == targetStart &&
                inst.occurrenceOriginalZoneId == targetZone
        }

        // 3. Cancel old AlarmManager alarms
        alarmScheduler?.let { scheduler ->
            for (oldInst in oldInstancesForOccurrence) {
                scheduler.cancelAlarm(oldInst.requestCode, NotificationConstants.ACTION_ALARM)
            }
        }

        // 4. Invalidate/remove obsolete notification instances/schedules
        notificationRepository?.let { notifRepo ->
            for (oldInst in oldInstancesForOccurrence) {
                notifRepo.updateStatus(oldInst.id, NotificationInstanceStatus.CANCELLED)
                notifRepo.deleteById(oldInst.id)
            }
        }

        // If old instance was actively ringing, stop active alarm playback immediately
        if (oldInstancesForOccurrence.any { it.status == NotificationInstanceStatus.RINGING }) {
            stopActiveAlarm?.invoke()
        }

        // 5. Persist the new occurrence/reschedule state
        val result = rescheduleOccurrenceUseCase(
            event = event,
            originalStartDateTime = occurrence.originalStartDateTime,
            originalZoneId = occurrence.zoneId,
            newStartDateTime = newStartDateTime,
            newEndDateTime = newEndDateTime,
            newZoneId = newZoneId
        )

        when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                when (val mutation = result.data) {
                    is OccurrenceMutationResult.RecurringException -> {
                        occurrenceExceptionRepository.saveException(mutation.exception)
                    }
                    is OccurrenceMutationResult.OneTimeOccurrence -> {
                        val exception = OccurrenceException(
                            eventId = event.id,
                            originalStartDateTime = occurrence.originalStartDateTime,
                            originalZoneId = occurrence.zoneId,
                            status = OccurrenceStatus.RESCHEDULED,
                            rescheduledStartDateTime = newStartDateTime,
                            rescheduledEndDateTime = newEndDateTime,
                            rescheduledZoneId = newZoneId
                        )
                        occurrenceExceptionRepository.saveException(exception)
                    }
                }
            }
        }

        // 6. Calculate new reminder trigger times for this rescheduled occurrence
        if (event.lifecycleStatus == EventLifecycleStatus.ACTIVE && event.notificationSchedules.isNotEmpty()) {
            val effectiveOccurrence = EventOccurrence(
                eventId = event.id,
                originalStartDateTime = occurrence.originalStartDateTime,
                zoneId = occurrence.zoneId,
                rescheduledStartDateTime = newStartDateTime,
                rescheduledEndDateTime = newEndDateTime,
                rescheduledZoneId = newZoneId,
                status = if (newStartDateTime.isBefore(nowLdt)) OccurrenceStatus.OVERDUE else OccurrenceStatus.RESCHEDULED
            )

            val calculatedList = mutableListOf<NotificationScheduleCalculator.CalculatedNotification>()
            for (schedule in event.notificationSchedules) {
                val calc = NotificationScheduleCalculator.calculate(
                    occurrence = effectiveOccurrence,
                    schedule = schedule,
                    quietHours = null,
                    priority = event.priority
                )
                calculatedList.add(calc)
            }

            val futureCandidates = calculatedList.filter { it.triggerEpochMillis > nowEpochMillis }
            val pastCandidates = calculatedList.filter { it.triggerEpochMillis <= nowEpochMillis }

            // 7 & 8. Create and schedule future alarms
            if (notificationRepository != null && alarmScheduler != null) {
                for (cand in futureCandidates) {
                    val newInstance = NotificationInstance(
                        id = cand.instanceId,
                        eventId = cand.eventId,
                        scheduleId = cand.scheduleId,
                        occurrenceOriginalStartDateTime = cand.occurrenceOriginalStartDateTime,
                        occurrenceOriginalZoneId = cand.occurrenceOriginalZoneId,
                        triggerEpochMillis = cand.triggerEpochMillis,
                        status = NotificationInstanceStatus.SCHEDULED,
                        requestCode = cand.instanceId.hashCode()
                    )
                    notificationRepository.saveInstance(newInstance)
                    val extras = mapOf(
                        NotificationConstants.EXTRA_INSTANCE_ID to newInstance.id,
                        NotificationConstants.EXTRA_EVENT_ID to newInstance.eventId,
                        NotificationConstants.EXTRA_OCCURRENCE_START to newInstance.occurrenceOriginalStartDateTime.toString(),
                        NotificationConstants.EXTRA_OCCURRENCE_ZONE to newInstance.occurrenceOriginalZoneId.id,
                        NotificationConstants.EXTRA_DELIVERY_TYPE to cand.deliveryType.name
                    )
                    alarmScheduler.scheduleAlarm(
                        requestCode = newInstance.requestCode,
                        triggerEpochMillis = newInstance.triggerEpochMillis,
                        intentAction = NotificationConstants.ACTION_ALARM,
                        extras = extras
                    )
                }
            }

            // 9 & 10. Immediately deliver any reminder whose newly calculated trigger is already past (consolidate multiple missed into ONE)
            if (pastCandidates.isNotEmpty()) {
                val mostRecent = pastCandidates.maxByOrNull { it.triggerEpochMillis }!!
                val deliveredInstance = NotificationInstance(
                    id = mostRecent.instanceId,
                    eventId = mostRecent.eventId,
                    scheduleId = mostRecent.scheduleId,
                    occurrenceOriginalStartDateTime = mostRecent.occurrenceOriginalStartDateTime,
                    occurrenceOriginalZoneId = mostRecent.occurrenceOriginalZoneId,
                    triggerEpochMillis = mostRecent.triggerEpochMillis,
                    status = NotificationInstanceStatus.DELIVERED,
                    requestCode = mostRecent.instanceId.hashCode()
                )
                notificationRepository?.saveInstance(deliveredInstance)
                immediateNotificationDeliverer?.deliverImmediate(event, deliveredInstance, pastCandidates.size)
            }
        }
    }
}
