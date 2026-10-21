package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.ConflictSeverity
import com.example.offlinereminder.domain.model.ConflictType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.ScheduleConflict
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

class DetectEventConflictsUseCase(
    private val eventRepository: EventRepository,
    private val recurrenceEngine: RecurrenceEngine,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        targetEvent: Event,
        horizonStart: LocalDateTime? = null,
        horizonEnd: LocalDateTime? = null
    ): List<ScheduleConflict> {
        val nowLdt = timeProvider.nowLocalDateTime()
        val start = horizonStart ?: nowLdt.minusDays(7)
        val end = horizonEnd ?: nowLdt.plusDays(30)

        if (end.isBefore(start)) {
            return emptyList()
        }

        val exceptions = occurrenceExceptionRepository.getExceptionsInRange(start, end)
        val targetOccurrences = recurrenceEngine.projectOccurrences(
            event = targetEvent,
            from = start,
            to = end,
            exceptions = exceptions
        ).filter {
            it.status != OccurrenceStatus.SKIPPED && it.status != OccurrenceStatus.EXCLUDED
        }

        if (targetOccurrences.isEmpty()) {
            return emptyList()
        }

        val allActiveEvents = eventRepository.getAllActiveEvents().first()
            .filter { candidate ->
                candidate.id != targetEvent.id &&
                    candidate.lifecycleStatus == EventLifecycleStatus.ACTIVE
            }

        val isTargetAllDay = targetEvent.temporal.isAllDay
        val conflicts = mutableListOf<ScheduleConflict>()

        for (candidateEvent in allActiveEvents) {
            val candidateOccurrences = recurrenceEngine.projectOccurrences(
                event = candidateEvent,
                from = start,
                to = end,
                exceptions = exceptions
            ).filter {
                it.status != OccurrenceStatus.SKIPPED && it.status != OccurrenceStatus.EXCLUDED
            }

            val isCandidateAllDay = candidateEvent.temporal.isAllDay

            for (targetOcc in targetOccurrences) {
                for (candOcc in candidateOccurrences) {
                    if (isTargetAllDay || isCandidateAllDay) {
                        // All-day coexistence is determined on local calendar date overlap
                        val targetStartDate = targetOcc.effectiveStartDateTime.toLocalDate()
                        val targetEndDate = targetOcc.effectiveEndDateTime?.toLocalDate() ?: targetStartDate
                        val candStartDate = candOcc.effectiveStartDateTime.toLocalDate()
                        val candEndDate = candOcc.effectiveEndDateTime?.toLocalDate() ?: candStartDate

                        val datesOverlap = !targetStartDate.isAfter(candEndDate) && !candStartDate.isAfter(targetEndDate)
                        if (datesOverlap) {
                            conflicts.add(
                                ScheduleConflict(
                                    conflictingEventId = targetEvent.id,
                                    conflictingEventTitle = targetEvent.title,
                                    conflictingOccurrence = targetOcc,
                                    existingEventId = candidateEvent.id,
                                    existingEventTitle = candidateEvent.title,
                                    existingOccurrence = candOcc,
                                    type = ConflictType.ALL_DAY_COEXISTENCE,
                                    severity = ConflictSeverity.ADVISORY
                                )
                            )
                        }
                    } else {
                        // Strict half-open interval overlap [S1, E1) vs [S2, E2) across timezones
                        val targetStartInstant = targetOcc.effectiveStartDateTime.atZone(targetOcc.effectiveZoneId).toInstant()
                        val targetEndInstant = (targetOcc.effectiveEndDateTime ?: targetOcc.effectiveStartDateTime.plusHours(1))
                            .atZone(targetOcc.effectiveZoneId).toInstant()

                        val candStartInstant = candOcc.effectiveStartDateTime.atZone(candOcc.effectiveZoneId).toInstant()
                        val candEndInstant = (candOcc.effectiveEndDateTime ?: candOcc.effectiveStartDateTime.plusHours(1))
                            .atZone(candOcc.effectiveZoneId).toInstant()

                        if (targetStartInstant.isBefore(candEndInstant) && candStartInstant.isBefore(targetEndInstant)) {
                            conflicts.add(
                                ScheduleConflict(
                                    conflictingEventId = targetEvent.id,
                                    conflictingEventTitle = targetEvent.title,
                                    conflictingOccurrence = targetOcc,
                                    existingEventId = candidateEvent.id,
                                    existingEventTitle = candidateEvent.title,
                                    existingOccurrence = candOcc,
                                    type = ConflictType.DIRECT_OVERLAP,
                                    severity = ConflictSeverity.HARD
                                )
                            )
                        }
                    }
                }
            }
        }

        return conflicts
    }
}
