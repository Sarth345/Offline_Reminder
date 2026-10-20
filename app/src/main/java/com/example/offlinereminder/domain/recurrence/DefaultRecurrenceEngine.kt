package com.example.offlinereminder.domain.recurrence

import com.example.offlinereminder.core.time.DateTimeUtils
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Default implementation of [RecurrenceEngine].
 *
 * Implements deterministic bounded recurrence calculations according to iCalendar RFC-5545 semantics
 * and project-specific requirements:
 * - Clamping monthly days to month-end (e.g. 31st in 30-day months or February).
 * - Clamping yearly Feb 29 to Feb 28 in non-leap years.
 * - Deterministic occurrence identity preservation across rescheduling and mutations.
 * - Strict interval intersection within the requested projection range [[from], [to]].
 * - Zero Android, Room, or UI dependencies.
 */
class DefaultRecurrenceEngine(
    private val defaultTimeProvider: TimeProvider = SystemTimeProvider()
) : RecurrenceEngine {

    companion object {
        private const val MAX_ITERATIONS = 50_000
    }

    override fun projectOccurrences(
        event: Event,
        from: LocalDateTime,
        to: LocalDateTime,
        exceptions: List<OccurrenceException>,
        evaluateOverdue: Boolean,
        timeProvider: TimeProvider?
    ): List<EventOccurrence> {
        if (to.isBefore(from)) {
            return emptyList()
        }

        val eventExceptions = exceptions.filter { it.eventId == event.id }

        return if (event.recurrenceRule == null) {
            projectNonRecurring(
                event = event,
                from = from,
                to = to,
                eventExceptions = eventExceptions,
                evaluateOverdue = evaluateOverdue,
                timeProvider = timeProvider
            )
        } else {
            projectRecurring(
                event = event,
                rule = event.recurrenceRule,
                from = from,
                to = to,
                eventExceptions = eventExceptions,
                evaluateOverdue = evaluateOverdue,
                timeProvider = timeProvider
            )
        }
    }

    private fun projectNonRecurring(
        event: Event,
        from: LocalDateTime,
        to: LocalDateTime,
        eventExceptions: List<OccurrenceException>,
        evaluateOverdue: Boolean,
        timeProvider: TimeProvider?
    ): List<EventOccurrence> {
        val initialOccurrence = event.createInitialOccurrence()
        val matchingException = eventExceptions.firstOrNull {
            it.occurrenceId == initialOccurrence.occurrenceId ||
                (it.originalStartDateTime == initialOccurrence.originalStartDateTime &&
                    (it.originalZoneId == initialOccurrence.zoneId || it.originalZoneId == event.createInitialOccurrence().zoneId))
        }

        if (matchingException?.status == OccurrenceStatus.EXCLUDED) {
            return emptyList()
        }

        val occurrence = if (matchingException != null) {
            initialOccurrence.copy(
                zoneId = matchingException.originalZoneId,
                occurrenceId = matchingException.occurrenceId,
                rescheduledStartDateTime = matchingException.rescheduledStartDateTime,
                rescheduledEndDateTime = matchingException.rescheduledEndDateTime,
                rescheduledZoneId = matchingException.rescheduledZoneId,
                actualExecutionDateTime = matchingException.actualExecutionDateTime,
                status = matchingException.status,
                completedAt = matchingException.completedAt,
                isException = true,
                titleOverride = matchingException.titleOverride,
                notesOverride = matchingException.notesOverride
            )
        } else {
            initialOccurrence
        }

        if (!intersectsWindow(
                start = occurrence.effectiveStartDateTime,
                end = occurrence.effectiveEndDateTime,
                temporal = event.temporal,
                from = from,
                to = to
            )
        ) {
            return emptyList()
        }

        val finalOccurrence = applyOverdueIfNeeded(occurrence, evaluateOverdue, timeProvider)
        return listOf(finalOccurrence)
    }

    private fun projectRecurring(
        event: Event,
        rule: RecurrenceRule,
        from: LocalDateTime,
        to: LocalDateTime,
        eventExceptions: List<OccurrenceException>,
        evaluateOverdue: Boolean,
        timeProvider: TimeProvider?
    ): List<EventOccurrence> {
        val temporal = event.temporal
        val eventStartDate = temporal.startDate
        val eventStartTime = temporal.startLocalDateTime().toLocalTime()
        val eventZoneId = when (temporal) {
            is EventDateTime.DateTime -> temporal.zoneId
            is EventDateTime.DateTimeRange -> temporal.zoneId
            else -> ZoneId.systemDefault()
        }

        val interval = maxOf(1, rule.interval)
        val maxCount = if (rule.endCondition is RecurrenceEnd.Count) rule.endCondition.maxOccurrences else Int.MAX_VALUE
        val untilDate = if (rule.endCondition is RecurrenceEnd.UntilDate) rule.endCondition.date else null

        val nominalOccurrences = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> generateDaily(
                event = event,
                rule = rule,
                interval = interval,
                eventStartDate = eventStartDate,
                eventStartTime = eventStartTime,
                eventZoneId = eventZoneId,
                maxCount = maxCount,
                untilDate = untilDate,
                from = from,
                to = to
            )

            RecurrenceFrequency.WEEKLY -> generateWeekly(
                event = event,
                rule = rule,
                interval = interval,
                eventStartDate = eventStartDate,
                eventStartTime = eventStartTime,
                eventZoneId = eventZoneId,
                maxCount = maxCount,
                untilDate = untilDate,
                from = from,
                to = to
            )

            RecurrenceFrequency.MONTHLY -> generateMonthly(
                event = event,
                rule = rule,
                interval = interval,
                eventStartDate = eventStartDate,
                eventStartTime = eventStartTime,
                eventZoneId = eventZoneId,
                maxCount = maxCount,
                untilDate = untilDate,
                from = from,
                to = to
            )

            RecurrenceFrequency.YEARLY -> generateYearly(
                event = event,
                rule = rule,
                interval = interval,
                eventStartDate = eventStartDate,
                eventStartTime = eventStartTime,
                eventZoneId = eventZoneId,
                maxCount = maxCount,
                untilDate = untilDate,
                from = from,
                to = to
            )
        }

        val exceptionsByOccurrenceId = eventExceptions.associateBy { it.occurrenceId }
        val emittedOccurrenceIds = mutableSetOf<String>()
        val projectedList = mutableListOf<EventOccurrence>()

        // 1. Process nominal occurrences and apply matching exceptions
        for (nominal in nominalOccurrences) {
            val exception = exceptionsByOccurrenceId[nominal.occurrenceId]
                ?: eventExceptions.firstOrNull { it.originalStartDateTime == nominal.originalStartDateTime }

            emittedOccurrenceIds.add(nominal.occurrenceId)

            if (exception != null) {
                emittedOccurrenceIds.add(exception.occurrenceId)
                if (exception.status == OccurrenceStatus.EXCLUDED) {
                    continue
                }
                val modified = nominal.copy(
                    zoneId = exception.originalZoneId,
                    occurrenceId = exception.occurrenceId,
                    rescheduledStartDateTime = exception.rescheduledStartDateTime,
                    rescheduledEndDateTime = exception.rescheduledEndDateTime,
                    rescheduledZoneId = exception.rescheduledZoneId,
                    actualExecutionDateTime = exception.actualExecutionDateTime,
                    status = exception.status,
                    completedAt = exception.completedAt,
                    isException = true,
                    titleOverride = exception.titleOverride ?: nominal.titleOverride,
                    notesOverride = exception.notesOverride ?: nominal.notesOverride
                )
                if (intersectsWindow(modified.effectiveStartDateTime, modified.effectiveEndDateTime, temporal, from, to)) {
                    projectedList.add(applyOverdueIfNeeded(modified, evaluateOverdue, timeProvider))
                }
            } else {
                if (intersectsWindow(nominal.effectiveStartDateTime, nominal.effectiveEndDateTime, temporal, from, to)) {
                    projectedList.add(applyOverdueIfNeeded(nominal, evaluateOverdue, timeProvider))
                }
            }
        }

        // 2. Process any exceptions rescheduled from outside into the window
        for (exception in eventExceptions) {
            if (emittedOccurrenceIds.contains(exception.occurrenceId)) {
                continue
            }
            if (exception.status == OccurrenceStatus.EXCLUDED) {
                continue
            }

            val defaultEnd = calculateDefaultEnd(exception.originalStartDateTime, temporal)
            val occurrence = EventOccurrence(
                eventId = event.id,
                originalStartDateTime = exception.originalStartDateTime,
                zoneId = exception.originalZoneId,
                occurrenceId = exception.occurrenceId,
                rescheduledStartDateTime = exception.rescheduledStartDateTime,
                rescheduledEndDateTime = exception.rescheduledEndDateTime,
                rescheduledZoneId = exception.rescheduledZoneId,
                actualExecutionDateTime = exception.actualExecutionDateTime,
                defaultEndDateTime = defaultEnd,
                status = exception.status,
                completedAt = exception.completedAt,
                isException = true,
                titleOverride = exception.titleOverride,
                notesOverride = exception.notesOverride
            )

            if (intersectsWindow(occurrence.effectiveStartDateTime, occurrence.effectiveEndDateTime, temporal, from, to)) {
                emittedOccurrenceIds.add(exception.occurrenceId)
                projectedList.add(applyOverdueIfNeeded(occurrence, evaluateOverdue, timeProvider))
            }
        }

        return projectedList.sortedWith(
            compareBy<EventOccurrence> { it.effectiveStartDateTime }
                .thenBy { it.occurrenceId }
        )
    }

    private fun generateDaily(
        event: Event,
        rule: RecurrenceRule,
        interval: Int,
        eventStartDate: LocalDate,
        eventStartTime: java.time.LocalTime,
        eventZoneId: ZoneId,
        maxCount: Int,
        untilDate: LocalDate?,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<EventOccurrence> {
        val occurrences = mutableListOf<EventOccurrence>()
        var totalCount = 0
        var iterations = 0

        var currentDate = if (rule.endCondition !is RecurrenceEnd.Count) {
            val daysDiff = ChronoUnit.DAYS.between(eventStartDate, from.toLocalDate())
            val periods = maxOf(0L, (daysDiff / interval) - 1)
            eventStartDate.plusDays(periods * interval)
        } else {
            eventStartDate
        }

        while (iterations++ < MAX_ITERATIONS) {
            if (untilDate != null && currentDate.isAfter(untilDate)) break
            if (totalCount >= maxCount) break

            val candidateStart = currentDate.atTime(eventStartTime)
            if (candidateStart.isAfter(to)) break

            if (!currentDate.isBefore(eventStartDate)) {
                totalCount++
                val candidateEnd = calculateDefaultEnd(candidateStart, event.temporal)
                if (intersectsWindow(candidateStart, candidateEnd, event.temporal, from, to) && !rule.isExcluded(currentDate)) {
                    occurrences.add(
                        EventOccurrence(
                            eventId = event.id,
                            originalStartDateTime = candidateStart,
                            zoneId = eventZoneId,
                            defaultEndDateTime = candidateEnd,
                            status = OccurrenceStatus.PENDING,
                            isException = false
                        )
                    )
                }
            }

            currentDate = currentDate.plusDays(interval.toLong())
        }

        return occurrences
    }

    private fun generateWeekly(
        event: Event,
        rule: RecurrenceRule,
        interval: Int,
        eventStartDate: LocalDate,
        eventStartTime: java.time.LocalTime,
        eventZoneId: ZoneId,
        maxCount: Int,
        untilDate: LocalDate?,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<EventOccurrence> {
        val occurrences = mutableListOf<EventOccurrence>()
        val targetDays = if (rule.daysOfWeek.isNotEmpty()) rule.daysOfWeek else setOf(eventStartDate.dayOfWeek)
        val sortedDays = targetDays.sortedBy { it.value }
        val startMonday = eventStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        var currentMonday = if (rule.endCondition !is RecurrenceEnd.Count) {
            val fromMonday = from.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeksDiff = ChronoUnit.WEEKS.between(startMonday, fromMonday)
            val periods = maxOf(0L, (weeksDiff / interval) - 1)
            startMonday.plusWeeks(periods * interval)
        } else {
            startMonday
        }

        var totalCount = 0
        var iterations = 0

        while (iterations++ < MAX_ITERATIONS) {
            if (currentMonday.isAfter(to.toLocalDate().plusWeeks(1))) break

            var weekHasFutureInstances = false

            for (dayOfWeek in sortedDays) {
                val candidateDate = currentMonday.plusDays((dayOfWeek.value - 1).toLong())

                if (candidateDate.isBefore(eventStartDate)) continue
                if (untilDate != null && candidateDate.isAfter(untilDate)) return occurrences
                if (totalCount >= maxCount) return occurrences

                totalCount++
                val candidateStart = candidateDate.atTime(eventStartTime)

                if (candidateStart.isAfter(to)) {
                    weekHasFutureInstances = true
                    continue
                }

                val candidateEnd = calculateDefaultEnd(candidateStart, event.temporal)
                if (intersectsWindow(candidateStart, candidateEnd, event.temporal, from, to) && !rule.isExcluded(candidateDate)) {
                    occurrences.add(
                        EventOccurrence(
                            eventId = event.id,
                            originalStartDateTime = candidateStart,
                            zoneId = eventZoneId,
                            defaultEndDateTime = candidateEnd,
                            status = OccurrenceStatus.PENDING,
                            isException = false
                        )
                    )
                }
            }

            if (weekHasFutureInstances && currentMonday.isAfter(to.toLocalDate())) {
                break
            }

            currentMonday = currentMonday.plusWeeks(interval.toLong())
        }

        return occurrences
    }

    private fun generateMonthly(
        event: Event,
        rule: RecurrenceRule,
        interval: Int,
        eventStartDate: LocalDate,
        eventStartTime: java.time.LocalTime,
        eventZoneId: ZoneId,
        maxCount: Int,
        untilDate: LocalDate?,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<EventOccurrence> {
        val occurrences = mutableListOf<EventOccurrence>()
        val startYM = YearMonth.from(eventStartDate)
        val targetDay = rule.dayOfMonth ?: eventStartDate.dayOfMonth

        var currentYM = if (rule.endCondition !is RecurrenceEnd.Count) {
            val fromYM = YearMonth.from(from.toLocalDate())
            val monthsDiff = ChronoUnit.MONTHS.between(startYM, fromYM)
            val periods = maxOf(0L, (monthsDiff / interval) - 1)
            startYM.plusMonths(periods * interval)
        } else {
            startYM
        }

        var totalCount = 0
        var iterations = 0

        while (iterations++ < MAX_ITERATIONS) {
            if (currentYM.atDay(1).isAfter(to.toLocalDate().plusMonths(1))) break

            val candidateDate: LocalDate? = if (rule.setPosition != null && rule.daysOfWeek.isNotEmpty()) {
                val matchingDays = (1..currentYM.lengthOfMonth())
                    .map { currentYM.atDay(it) }
                    .filter { rule.daysOfWeek.contains(it.dayOfWeek) }

                if (rule.setPosition > 0) {
                    matchingDays.getOrNull(rule.setPosition - 1)
                } else {
                    matchingDays.getOrNull(matchingDays.size + rule.setPosition)
                }
            } else {
                DateTimeUtils.clampDayOfMonth(currentYM.year, currentYM.monthValue, targetDay)
            }

            if (candidateDate != null) {
                if (candidateDate.isBefore(eventStartDate)) {
                    currentYM = currentYM.plusMonths(interval.toLong())
                    continue
                }

                if (untilDate != null && candidateDate.isAfter(untilDate)) break
                if (totalCount >= maxCount) break

                totalCount++
                val candidateStart = candidateDate.atTime(eventStartTime)

                if (candidateStart.isAfter(to)) break

                val candidateEnd = calculateDefaultEnd(candidateStart, event.temporal)
                if (intersectsWindow(candidateStart, candidateEnd, event.temporal, from, to) && !rule.isExcluded(candidateDate)) {
                    occurrences.add(
                        EventOccurrence(
                            eventId = event.id,
                            originalStartDateTime = candidateStart,
                            zoneId = eventZoneId,
                            defaultEndDateTime = candidateEnd,
                            status = OccurrenceStatus.PENDING,
                            isException = false
                        )
                    )
                }
            }

            currentYM = currentYM.plusMonths(interval.toLong())
        }

        return occurrences
    }

    private fun generateYearly(
        event: Event,
        rule: RecurrenceRule,
        interval: Int,
        eventStartDate: LocalDate,
        eventStartTime: java.time.LocalTime,
        eventZoneId: ZoneId,
        maxCount: Int,
        untilDate: LocalDate?,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<EventOccurrence> {
        val occurrences = mutableListOf<EventOccurrence>()
        val targetMonth = rule.monthOfYear ?: eventStartDate.monthValue
        val targetDay = rule.dayOfMonth ?: eventStartDate.dayOfMonth

        var currentYear = if (rule.endCondition !is RecurrenceEnd.Count) {
            val yearsDiff = from.toLocalDate().year - eventStartDate.year
            val periods = maxOf(0, (yearsDiff / interval) - 1)
            eventStartDate.year + (periods * interval)
        } else {
            eventStartDate.year
        }

        var totalCount = 0
        var iterations = 0

        while (iterations++ < MAX_ITERATIONS) {
            if (currentYear > to.toLocalDate().year + 1) break

            val candidateDate: LocalDate = if (targetMonth == 2 && targetDay == 29) {
                DateTimeUtils.clampFeb29Yearly(currentYear)
            } else {
                DateTimeUtils.clampDayOfMonth(currentYear, targetMonth, targetDay)
            }

            if (candidateDate.isBefore(eventStartDate)) {
                currentYear += interval
                continue
            }

            if (untilDate != null && candidateDate.isAfter(untilDate)) break
            if (totalCount >= maxCount) break

            totalCount++
            val candidateStart = candidateDate.atTime(eventStartTime)

            if (candidateStart.isAfter(to)) break

            val candidateEnd = calculateDefaultEnd(candidateStart, event.temporal)
            if (intersectsWindow(candidateStart, candidateEnd, event.temporal, from, to) && !rule.isExcluded(candidateDate)) {
                occurrences.add(
                    EventOccurrence(
                        eventId = event.id,
                        originalStartDateTime = candidateStart,
                        zoneId = eventZoneId,
                        defaultEndDateTime = candidateEnd,
                        status = OccurrenceStatus.PENDING,
                        isException = false
                    )
                )
            }

            currentYear += interval
        }

        return occurrences
    }

    private fun intersectsWindow(
        start: LocalDateTime,
        end: LocalDateTime?,
        temporal: EventDateTime,
        from: LocalDateTime,
        to: LocalDateTime
    ): Boolean {
        val effectiveEnd = end ?: when {
            temporal.isAllDay -> start.toLocalDate().atTime(23, 59, 59)
            else -> null
        }

        return if (effectiveEnd != null) {
            !start.isAfter(to) && !effectiveEnd.isBefore(from)
        } else {
            !start.isBefore(from) && !start.isAfter(to)
        }
    }

    private fun calculateDefaultEnd(
        occurrenceStart: LocalDateTime,
        temporal: EventDateTime
    ): LocalDateTime? = when (temporal) {
        is EventDateTime.DateTimeRange -> {
            val duration = Duration.between(temporal.startDateTime, temporal.endDateTime)
            occurrenceStart.plus(duration)
        }
        is EventDateTime.DateRange -> {
            val days = ChronoUnit.DAYS.between(temporal.startDate, temporal.endDate)
            occurrenceStart.toLocalDate().plusDays(days).atTime(23, 59, 59)
        }
        else -> null
    }

    private fun applyOverdueIfNeeded(
        occurrence: EventOccurrence,
        evaluateOverdue: Boolean,
        timeProvider: TimeProvider?
    ): EventOccurrence {
        if (!evaluateOverdue || occurrence.status != OccurrenceStatus.PENDING) {
            return occurrence
        }
        val provider = timeProvider ?: defaultTimeProvider
        val now = provider.nowLocalDateTime(occurrence.effectiveZoneId)
        return if (occurrence.effectiveStartDateTime.isBefore(now)) {
            occurrence.copy(status = OccurrenceStatus.OVERDUE)
        } else {
            occurrence
        }
    }
}
