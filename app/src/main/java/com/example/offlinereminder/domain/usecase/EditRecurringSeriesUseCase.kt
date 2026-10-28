package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

enum class RecurringSeriesEditMode {
    THIS_OCCURRENCE,
    THIS_AND_FOLLOWING,
    ENTIRE_SERIES
}

/**
 * Domain use case handling editing of recurring events across three RFC-compliant modes:
 * 1. THIS_OCCURRENCE: Creates an occurrence exception for the specific occurrence without mutating the series master
 *    and recalculates notification schedules for the occurrence.
 * 2. THIS_AND_FOLLOWING: Splits the series; truncates original series before this date, preserves historical occurrence
 *    exceptions, migrates future occurrence exceptions, and starts a new series from this date.
 * 3. ENTIRE_SERIES: Updates the canonical master event, preserves existing occurrence exceptions (precedence rule),
 *    and reschedules notification instances respecting the exceptions.
 */
class EditRecurringSeriesUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val scheduleNotificationsUseCase: ScheduleNotificationsForEventUseCase,
    private val cancelNotificationsUseCase: CancelNotificationsForEventUseCase,
    private val timeProvider: TimeProvider
) {

    suspend operator fun invoke(
        originalEvent: Event,
        occurrenceOriginalStart: LocalDateTime,
        occurrenceOriginalZone: ZoneId,
        editedDraft: Event,
        mode: RecurringSeriesEditMode
    ): Result<Event> = Result.runCatching {
        val now = timeProvider.nowInstant()

        when (mode) {
            RecurringSeriesEditMode.THIS_OCCURRENCE -> {
                // Mode 1: Create or update OccurrenceException
                val excId = OccurrenceException.createId(originalEvent.id, occurrenceOriginalStart, occurrenceOriginalZone)
                val existing = occurrenceExceptionRepository.getExceptionByOccurrenceId(excId)

                val draftStartLdt = editedDraft.temporal.startLocalDateTime()
                val isRescheduled = draftStartLdt != occurrenceOriginalStart
                val titleOverride = if (editedDraft.title != originalEvent.title) editedDraft.title else null
                val notesOverride = if (editedDraft.description != originalEvent.description) editedDraft.description else null

                val exception = (existing ?: OccurrenceException(
                    id = excId,
                    eventId = originalEvent.id,
                    originalStartDateTime = occurrenceOriginalStart,
                    originalZoneId = occurrenceOriginalZone
                )).copy(
                    rescheduledStartDateTime = if (isRescheduled) draftStartLdt else existing?.rescheduledStartDateTime,
                    rescheduledEndDateTime = if (isRescheduled) editedDraft.temporal.endLocalDateTime() else existing?.rescheduledEndDateTime,
                    status = if (isRescheduled) OccurrenceStatus.RESCHEDULED else (existing?.status ?: OccurrenceStatus.PENDING),
                    titleOverride = titleOverride ?: existing?.titleOverride,
                    notesOverride = notesOverride ?: existing?.notesOverride
                )

                occurrenceExceptionRepository.saveException(exception)
                val allExceptions = occurrenceExceptionRepository.getExceptionsForEvent(originalEvent.id).first()
                scheduleNotificationsUseCase(originalEvent, allExceptions)
                originalEvent
            }

            RecurringSeriesEditMode.THIS_AND_FOLLOWING -> {
                // Mode 2: Split the series
                val cutoffDate = occurrenceOriginalStart.toLocalDate().minusDays(1)
                val newEventId = UUID.randomUUID().toString()

                // 2a. Migrate any existing exceptions on or after occurrenceOriginalStart to the new event
                val existingExceptions = occurrenceExceptionRepository.getExceptionsForEvent(originalEvent.id).first()
                val migratedExceptions = mutableListOf<OccurrenceException>()
                val remainingExceptions = mutableListOf<OccurrenceException>()
                for (exc in existingExceptions) {
                    if (!exc.originalStartDateTime.isBefore(occurrenceOriginalStart)) {
                        occurrenceExceptionRepository.deleteException(exc.id)
                        val migratedExc = exc.copy(
                            id = OccurrenceException.createId(newEventId, exc.originalStartDateTime, exc.originalZoneId),
                            eventId = newEventId
                        )
                        occurrenceExceptionRepository.saveException(migratedExc)
                        migratedExceptions.add(migratedExc)
                    } else {
                        remainingExceptions.add(exc)
                    }
                }

                // 2b. Truncate the original event recurrence rule to end before this occurrence
                val originalRule = originalEvent.recurrenceRule
                val truncatedOriginal = if (originalRule != null) {
                    val updatedRule = originalRule.copy(
                        endCondition = RecurrenceEnd.UntilDate(cutoffDate)
                    )
                    originalEvent.copy(
                        recurrenceRule = updatedRule,
                        updatedAt = now
                    )
                } else {
                    originalEvent
                }

                eventRepository.saveEvent(truncatedOriginal)
                cancelNotificationsUseCase(originalEvent.id)
                scheduleNotificationsUseCase(truncatedOriginal, remainingExceptions)

                // 2c. Create new master event starting at this occurrence with the edited fields
                val newEvent = editedDraft.copy(
                    id = newEventId,
                    parentEventId = originalEvent.id,
                    createdAt = now,
                    updatedAt = now
                )

                eventRepository.saveEvent(newEvent)
                scheduleNotificationsUseCase(newEvent, migratedExceptions)

                newEvent
            }

            RecurringSeriesEditMode.ENTIRE_SERIES -> {
                // Mode 3: Modify entire series master while preserving existing occurrence exceptions (precedence rule)
                val updatedMaster = editedDraft.copy(
                    id = originalEvent.id,
                    createdAt = originalEvent.createdAt,
                    updatedAt = now
                )

                val existingExceptions = occurrenceExceptionRepository.getExceptionsForEvent(originalEvent.id).first()

                eventRepository.saveEvent(updatedMaster)
                cancelNotificationsUseCase(originalEvent.id)
                scheduleNotificationsUseCase(updatedMaster, existingExceptions)
                updatedMaster
            }
        }
    }
}
