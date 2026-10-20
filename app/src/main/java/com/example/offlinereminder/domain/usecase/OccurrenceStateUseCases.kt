package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Use case to complete an occurrence.
 * For recurring events, generates an [OccurrenceException] without mutating the master [Event].
 * For one-time events, updates the occurrence instance.
 */
class CompleteOccurrenceUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(
        event: Event,
        originalStartDateTime: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
        actualExecutionDateTime: LocalDateTime? = null
    ): Result<OccurrenceMutationResult> {
        val now = timeProvider.nowInstant()

        return if (event.isRecurring) {
            val exception = OccurrenceException(
                eventId = event.id,
                originalStartDateTime = originalStartDateTime,
                originalZoneId = zoneId,
                status = OccurrenceStatus.COMPLETED,
                completedAt = now,
                actualExecutionDateTime = actualExecutionDateTime
            )
            Result.Success(OccurrenceMutationResult.RecurringException(exception))
        } else {
            val updatedOccurrence = event.createInitialOccurrence(
                status = OccurrenceStatus.COMPLETED,
                completedAt = now
            ).copy(actualExecutionDateTime = actualExecutionDateTime)
            Result.Success(OccurrenceMutationResult.OneTimeOccurrence(updatedOccurrence))
        }
    }
}

/**
 * Use case to skip an occurrence.
 */
class SkipOccurrenceUseCase {
    operator fun invoke(
        event: Event,
        originalStartDateTime: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<OccurrenceMutationResult> {
        return if (event.isRecurring) {
            val exception = OccurrenceException(
                eventId = event.id,
                originalStartDateTime = originalStartDateTime,
                originalZoneId = zoneId,
                status = OccurrenceStatus.SKIPPED
            )
            Result.Success(OccurrenceMutationResult.RecurringException(exception))
        } else {
            val updatedOccurrence = event.createInitialOccurrence(
                status = OccurrenceStatus.SKIPPED
            )
            Result.Success(OccurrenceMutationResult.OneTimeOccurrence(updatedOccurrence))
        }
    }
}

/**
 * Use case to reschedule an occurrence to a new scheduled date-time.
 * Preserves the canonical original occurrence identity ([originalStartDateTime], [originalZoneId])
 * while setting the replacement scheduled window ([newStartDateTime], [newEndDateTime], [newZoneId]).
 */
class RescheduleOccurrenceUseCase {
    operator fun invoke(
        event: Event,
        originalStartDateTime: LocalDateTime,
        originalZoneId: ZoneId = ZoneId.systemDefault(),
        newStartDateTime: LocalDateTime,
        newEndDateTime: LocalDateTime? = null,
        newZoneId: ZoneId = originalZoneId
    ): Result<OccurrenceMutationResult> {
        if (newEndDateTime != null && newEndDateTime.isBefore(newStartDateTime)) {
            return Result.Failure(
                IllegalArgumentException("newEndDateTime ($newEndDateTime) cannot be before newStartDateTime ($newStartDateTime)")
            )
        }

        return if (event.isRecurring) {
            val exception = OccurrenceException(
                eventId = event.id,
                originalStartDateTime = originalStartDateTime,
                originalZoneId = originalZoneId,
                status = OccurrenceStatus.RESCHEDULED,
                rescheduledStartDateTime = newStartDateTime,
                rescheduledEndDateTime = newEndDateTime,
                rescheduledZoneId = newZoneId
            )
            Result.Success(OccurrenceMutationResult.RecurringException(exception))
        } else {
            val occurrence = EventOccurrence(
                eventId = event.id,
                originalStartDateTime = originalStartDateTime,
                zoneId = originalZoneId,
                rescheduledStartDateTime = newStartDateTime,
                rescheduledEndDateTime = newEndDateTime,
                rescheduledZoneId = newZoneId,
                status = OccurrenceStatus.RESCHEDULED
            )
            Result.Success(OccurrenceMutationResult.OneTimeOccurrence(occurrence))
        }
    }
}

/**
 * Polymorphic result of mutating an occurrence state.
 */
sealed interface OccurrenceMutationResult {
    data class RecurringException(val exception: OccurrenceException) : OccurrenceMutationResult
    data class OneTimeOccurrence(val occurrence: EventOccurrence) : OccurrenceMutationResult
}
