package com.example.offlinereminder.presentation.upcoming

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.GetUpcomingOccurrencesUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class Phase13AgendaTest {

    private val utcZone = ZoneId.of("UTC")
    private val londonZone = ZoneId.of("Europe/London")
    private val nyZone = ZoneId.of("America/New_York")

    // Wednesday 2026-10-21 08:00 UTC
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T08:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        occurrenceExceptionRepository.clear()
        getUpcomingOccurrencesUseCase = GetUpcomingOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    private fun createEvent(
        id: String,
        title: String,
        temporal: EventDateTime,
        rule: RecurrenceRule? = null
    ): Event = Event(
        id = id,
        title = title,
        description = "Test Description",
        eventTypeId = "type_1",
        categoryId = "cat_1",
        priority = Priority.NORMAL,
        temporal = temporal,
        recurrenceRule = rule,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun agenda_ordersOccurrencesChronologically() = runTest {
        val eventLate = createEvent("e_late", "Late Event", EventDateTime.DateTime(LocalDateTime.of(2026, 10, 23, 16, 0), utcZone))
        val eventEarly = createEvent("e_early", "Early Event", EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 9, 0), utcZone))
        val eventMid = createEvent("e_mid", "Mid Event", EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 14, 0), utcZone))

        eventRepository.saveEvent(eventLate)
        eventRepository.saveEvent(eventEarly)
        eventRepository.saveEvent(eventMid)

        val agenda = getUpcomingOccurrencesUseCase().first()

        assertEquals(3, agenda.size)
        assertEquals("e_early", agenda[0].event.id)
        assertEquals("e_mid", agenda[1].event.id)
        assertEquals("e_late", agenda[2].event.id)
    }

    @Test
    fun agenda_handlesAllDayAndTimedEventsCorrectly() = runTest {
        val allDayEvent = createEvent("e_allday", "Conference", EventDateTime.DateOnly(LocalDate.of(2026, 10, 22)))
        val timedEvent = createEvent("e_timed", "Dinner", EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 19, 0), utcZone))

        eventRepository.saveEvent(allDayEvent)
        eventRepository.saveEvent(timedEvent)

        val agenda = getUpcomingOccurrencesUseCase().first()

        assertEquals(2, agenda.size)
        assertTrue(agenda[0].event.temporal.isAllDay)
        assertFalse(agenda[1].event.temporal.isAllDay)
    }

    @Test
    fun agenda_handlesMultiDayEvents() = runTest {
        val multiDay = createEvent(
            id = "e_multi",
            title = "Weekend Hackathon",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 23, 18, 0),
                endDateTime = LocalDateTime.of(2026, 10, 25, 18, 0),
                zoneId = utcZone
            )
        )
        eventRepository.saveEvent(multiDay)

        val agenda = getUpcomingOccurrencesUseCase().first()

        assertEquals(1, agenda.size)
        assertTrue("Hackathon should be flagged as multi-day", agenda.first().occurrence.isMultiDay)
    }

    @Test
    fun agenda_projectsRecurringOccurrencesWithoutInfinitePersistence() = runTest {
        val dailyRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1)
        val recurringEvent = createEvent(
            id = "e_daily",
            title = "Morning Standup",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 9, 0), utcZone),
            rule = dailyRule
        )
        eventRepository.saveEvent(recurringEvent)

        // Request a 5-day horizon
        val agenda = getUpcomingOccurrencesUseCase(horizonDays = 5).first()

        // Days 21, 22, 23, 24, 25, 26 -> 6 occurrences projected on-demand
        assertTrue(agenda.size in 5..6)
        assertEquals("Morning Standup", agenda.first().event.title)

        // Verify the database still contains only 1 master Event
        assertEquals(1, eventRepository.getAllEvents().first().size)
    }

    @Test
    fun agenda_filtersByOccurrenceStates() = runTest {
        val event = createEvent("e_state", "Task", EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0), utcZone))
        eventRepository.saveEvent(event)

        // Save a COMPLETED exception for the occurrence
        val completedException = OccurrenceException(
            eventId = "e_state",
            originalStartDateTime = LocalDateTime.of(2026, 10, 22, 10, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.COMPLETED
        )
        occurrenceExceptionRepository.saveException(completedException)

        // By default, completed is false
        val defaultAgenda = getUpcomingOccurrencesUseCase(includeCompleted = false).first()
        assertTrue(defaultAgenda.isEmpty())

        // With includeCompleted = true
        val withCompleted = getUpcomingOccurrencesUseCase(includeCompleted = true).first()
        assertEquals(1, withCompleted.size)
        assertEquals(OccurrenceStatus.COMPLETED, withCompleted.first().occurrence.status)
    }

    @Test
    fun agenda_handlesDifferentTimezonesAndDstTransitions() = runTest {
        // Europe/London DST transition: Sunday 2026-10-25 clocks fall back 1 hour (BST to GMT)
        val dstEvent = createEvent(
            id = "e_dst",
            title = "London Standup",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 24, 10, 0), londonZone),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1)
        )
        eventRepository.saveEvent(dstEvent)

        val agenda = getUpcomingOccurrencesUseCase(horizonDays = 4).first()

        // Wall clock time must remain 10:00 across the DST transition
        for (occWithEvent in agenda) {
            assertEquals(10, occWithEvent.occurrence.effectiveStartDateTime.hour)
            assertEquals(londonZone, occWithEvent.occurrence.effectiveZoneId)
        }
    }
}
