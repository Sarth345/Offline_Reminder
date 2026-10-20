package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventFilterParams
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain use case executing composable advanced search and filtering on events.
 * Pure Kotlin, zero Android/Compose/Room dependencies.
 */
class SearchEventsUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider
) {

    operator fun invoke(params: EventFilterParams): Flow<List<Event>> =
        eventRepository.getAllEvents().map { allEvents ->
            val nowLdt = timeProvider.nowLocalDateTime()
            val filtered = mutableListOf<Event>()

            for (event in allEvents) {
                // 1. Lifecycle Status Filter
                if (params.lifecycleStatuses.isNotEmpty()) {
                    if (!params.lifecycleStatuses.contains(event.lifecycleStatus)) continue
                } else {
                    if (event.lifecycleStatus != EventLifecycleStatus.ACTIVE) continue
                }

                // 2. Text Query Search (title and description)
                if (params.query.isNotBlank()) {
                    val q = params.query.trim().lowercase()
                    val titleMatch = event.title.lowercase().contains(q)
                    val descMatch = event.description.lowercase().contains(q)
                    if (!titleMatch && !descMatch) continue
                }

                // 3. Category Filter
                if (params.categoryIds.isNotEmpty() && !params.categoryIds.contains(event.categoryId)) {
                    continue
                }

                // 4. Event Type Filter
                if (params.eventTypeIds.isNotEmpty() && !params.eventTypeIds.contains(event.eventTypeId)) {
                    continue
                }

                // 5. Group Filter (event matches if it belongs to any of the selected groups)
                if (params.groupIds.isNotEmpty() && event.groupIds.intersect(params.groupIds).isEmpty()) {
                    continue
                }

                // 6. Tag Filter (event matches if it has any of the selected tags)
                if (params.tagIds.isNotEmpty() && event.tagIds.intersect(params.tagIds).isEmpty()) {
                    continue
                }

                // 7. Priority Filter
                if (params.priorities.isNotEmpty() && !params.priorities.contains(event.priority)) {
                    continue
                }

                // 8. Favorites Filter
                if (params.onlyFavorites && !event.isFavorite) {
                    continue
                }

                // 9. Date Range Intersection Filter
                if (params.startDate != null || params.endDate != null) {
                    val matchesDateRange = matchesDateRange(event, params.startDate, params.endDate)
                    if (!matchesDateRange) continue
                }

                // 10. Overdue Filter
                if (params.onlyOverdue) {
                    val isOverdue = checkIsOverdue(event, nowLdt)
                    if (!isOverdue) continue
                }

                filtered.add(event)
            }

            // Order results by scheduled start date ascending
            filtered.sortedBy { it.temporal.startDate }
        }

    private fun getEventDateBounds(temporal: EventDateTime): Pair<LocalDate, LocalDate> =
        when (temporal) {
            is EventDateTime.DateOnly -> temporal.startDate to temporal.startDate
            is EventDateTime.DateTime -> temporal.dateTime.toLocalDate() to temporal.dateTime.toLocalDate()
            is EventDateTime.DateRange -> temporal.startDate to temporal.endDate
            is EventDateTime.DateTimeRange -> temporal.startDateTime.toLocalDate() to temporal.endDateTime.toLocalDate()
        }

    private suspend fun matchesDateRange(
        event: Event,
        filterStart: LocalDate?,
        filterEnd: LocalDate?
    ): Boolean {
        if (event.recurrenceRule == null) {
            val (eventStart, eventEnd) = getEventDateBounds(event.temporal)
            if (filterStart != null && eventEnd.isBefore(filterStart)) return false
            if (filterEnd != null && eventStart.isAfter(filterEnd)) return false
            return true
        } else {
            // Recurring event: bounded occurrence projection
            val windowStart = (filterStart ?: event.temporal.startDate).atStartOfDay()
            val windowEnd = (filterEnd ?: (filterStart?.plusMonths(3) ?: event.temporal.startDate.plusMonths(3))).atTime(23, 59, 59)
            val exceptions = occurrenceExceptionRepository.getExceptionsInRange(windowStart, windowEnd)
            val occurrences = recurrenceEngine.projectOccurrences(
                event = event,
                from = windowStart,
                to = windowEnd,
                exceptions = exceptions,
                evaluateOverdue = false,
                timeProvider = timeProvider
            )
            return occurrences.any { occ ->
                val occDate = occ.effectiveStartDateTime.toLocalDate()
                val afterOrOnStart = filterStart == null || !occDate.isBefore(filterStart)
                val beforeOrOnEnd = filterEnd == null || !occDate.isAfter(filterEnd)
                afterOrOnStart && beforeOrOnEnd
            }
        }
    }

    private suspend fun checkIsOverdue(event: Event, nowLdt: LocalDateTime): Boolean {
        if (event.lifecycleStatus != EventLifecycleStatus.ACTIVE) return false

        if (event.recurrenceRule == null) {
            val initialOcc = event.createInitialOccurrence()
            if (!initialOcc.effectiveStartDateTime.isBefore(nowLdt)) return false
            val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(initialOcc.occurrenceId)
            return exception?.status != OccurrenceStatus.COMPLETED &&
                exception?.status != OccurrenceStatus.EXCLUDED &&
                exception?.status != OccurrenceStatus.SKIPPED
        } else {
            val lookbackFrom = nowLdt.minusDays(30)
            val exceptions = occurrenceExceptionRepository.getExceptionsInRange(lookbackFrom, nowLdt)
            val occurrences = recurrenceEngine.projectOccurrences(
                event = event,
                from = lookbackFrom,
                to = nowLdt,
                exceptions = exceptions,
                evaluateOverdue = true,
                timeProvider = timeProvider
            )
            return occurrences.any {
                it.status == OccurrenceStatus.OVERDUE ||
                    (it.status == OccurrenceStatus.PENDING && it.effectiveStartDateTime.isBefore(nowLdt))
            }
        }
    }
}
