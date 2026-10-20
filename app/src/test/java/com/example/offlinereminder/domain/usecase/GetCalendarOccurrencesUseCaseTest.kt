package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class GetCalendarOccurrencesUseCaseTest {

    private val utcZone = ZoneId.of("UTC")
    // Tuesday 2026-10-20 at 12:00 UTC
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getCalendarOccurrencesUseCase: GetCalendarOccurrencesUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        getCalendarOccurrencesUseCase = GetCalendarOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    private fun createEvent(
        id: String,
        title: String,
        dateTime: LocalDateTime,
        status: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
        recurrenceRule: RecurrenceRule? = null
    ): Event = Event(
        id = id,
        title = title,
        description = "Description",
        eventTypeId = "type_1",
        categoryId = "cat_1",
        priority = Priority.NORMAL,
        lifecycleStatus = status,
        temporal = EventDateTime.DateTime(dateTime, utcZone),
        recurrenceRule = recurrenceRule,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun getCalendarOccurrences_boundedProjection_includesOccurrencesWithinRange() = runTest {
        val eventInMonth = createEvent("e1", "October Event", LocalDateTime.of(2026, 10, 15, 10, 0))
        val eventNextMonth = createEvent("e2", "November Event", LocalDateTime.of(2026, 11, 5, 10, 0))

        eventRepository.saveEvent(eventInMonth)
        eventRepository.saveEvent(eventNextMonth)

        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 31, 23, 59, 59)

        val occurrences = getCalendarOccurrencesUseCase(from, to).first()

        assertEquals(1, occurrences.size)
        assertEquals("e1", occurrences[0].event.id)
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 0), occurrences[0].occurrence.effectiveStartDateTime)
    }

    @Test
    fun getCalendarOccurrences_recurringEvent_projectsAllOccurrencesInMonth() = runTest {
        val recurringEvent = createEvent(
            id = "rec1",
            title = "Weekly Team Sync",
            dateTime = LocalDateTime.of(2026, 10, 5, 9, 0), // Mondays: Oct 5, 12, 19, 26
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 1,
                endCondition = RecurrenceEnd.Never
            )
        )
        eventRepository.saveEvent(recurringEvent)

        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 31, 23, 59, 59)

        val occurrences = getCalendarOccurrencesUseCase(from, to).first()

        assertEquals(4, occurrences.size)
        val days = occurrences.map { it.occurrence.effectiveStartDateTime.dayOfMonth }
        assertEquals(listOf(5, 12, 19, 26), days)
    }

    @Test
    fun getCalendarOccurrences_excludedOccurrenceException_isOmitted() = runTest {
        val recurringEvent = createEvent(
            id = "rec2",
            title = "Weekly Standup",
            dateTime = LocalDateTime.of(2026, 10, 5, 9, 0), // Oct 5, 12, 19, 26
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 1,
                endCondition = RecurrenceEnd.Never
            )
        )
        eventRepository.saveEvent(recurringEvent)

        // Cancel occurrence on Oct 12
        val exception = OccurrenceException(
            eventId = "rec2",
            originalStartDateTime = LocalDateTime.of(2026, 10, 12, 9, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.EXCLUDED
        )
        occurrenceExceptionRepository.saveException(exception)

        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 31, 23, 59, 59)

        val occurrences = getCalendarOccurrencesUseCase(from, to).first()

        assertEquals(3, occurrences.size)
        val days = occurrences.map { it.occurrence.effectiveStartDateTime.dayOfMonth }
        assertEquals(listOf(5, 19, 26), days)
        assertFalse(days.contains(12))
    }

    @Test
    fun getCalendarOccurrences_rescheduledOccurrence_reflectsNewDateTime() = runTest {
        val recurringEvent = createEvent(
            id = "rec3",
            title = "Weekly Standup",
            dateTime = LocalDateTime.of(2026, 10, 5, 9, 0), // Oct 5, 12, 19, 26
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 1,
                endCondition = RecurrenceEnd.Never
            )
        )
        eventRepository.saveEvent(recurringEvent)

        // Reschedule Oct 12 09:00 to Oct 13 14:00
        val exception = OccurrenceException(
            eventId = "rec3",
            originalStartDateTime = LocalDateTime.of(2026, 10, 12, 9, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = LocalDateTime.of(2026, 10, 13, 14, 0)
        )
        occurrenceExceptionRepository.saveException(exception)

        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 31, 23, 59, 59)

        val occurrences = getCalendarOccurrencesUseCase(from, to).first()

        assertEquals(4, occurrences.size)
        val oct13 = occurrences.find { it.occurrence.originalStartDateTime == LocalDateTime.of(2026, 10, 12, 9, 0) }
        assertEquals(LocalDateTime.of(2026, 10, 13, 14, 0), oct13?.occurrence?.effectiveStartDateTime)
    }

    @Test
    fun getCalendarOccurrences_completedAndOverdueStates() = runTest {
        // Today is 2026-10-20 12:00
        val pastEvent = createEvent("past", "Past Event", LocalDateTime.of(2026, 10, 15, 10, 0))
        val completedEvent = createEvent("comp", "Completed Event", LocalDateTime.of(2026, 10, 16, 10, 0))
        val futureEvent = createEvent("future", "Future Event", LocalDateTime.of(2026, 10, 25, 10, 0))

        eventRepository.saveEvent(pastEvent)
        eventRepository.saveEvent(completedEvent)
        eventRepository.saveEvent(futureEvent)

        val completedException = OccurrenceException(
            eventId = "comp",
            originalStartDateTime = LocalDateTime.of(2026, 10, 16, 10, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = timeProvider.nowInstant()
        )
        occurrenceExceptionRepository.saveException(completedException)

        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 31, 23, 59, 59)

        val occurrences = getCalendarOccurrencesUseCase(from, to).first()

        val pastOcc = occurrences.find { it.event.id == "past" }
        val compOcc = occurrences.find { it.event.id == "comp" }
        val futureOcc = occurrences.find { it.event.id == "future" }

        assertEquals(OccurrenceStatus.OVERDUE, pastOcc?.occurrence?.status)
        assertEquals(OccurrenceStatus.COMPLETED, compOcc?.occurrence?.status)
        assertEquals(OccurrenceStatus.PENDING, futureOcc?.occurrence?.status)
    }

    @Test
    fun getCalendarOccurrences_nonActiveEvents_areExcluded() = runTest {
        val activeEvent = createEvent("active", "Active", LocalDateTime.of(2026, 10, 15, 10, 0), EventLifecycleStatus.ACTIVE)
        val trashedEvent = createEvent("trashed", "Trashed", LocalDateTime.of(2026, 10, 15, 10, 0), EventLifecycleStatus.TRASHED)
        val archivedEvent = createEvent("archived", "Archived", LocalDateTime.of(2026, 10, 15, 10, 0), EventLifecycleStatus.ARCHIVED)

        eventRepository.saveEvent(activeEvent)
        eventRepository.saveEvent(trashedEvent)
        eventRepository.saveEvent(archivedEvent)

        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 31, 23, 59, 59)

        val occurrences = getCalendarOccurrencesUseCase(from, to).first()

        assertEquals(1, occurrences.size)
        assertEquals("active", occurrences[0].event.id)
    }
}
