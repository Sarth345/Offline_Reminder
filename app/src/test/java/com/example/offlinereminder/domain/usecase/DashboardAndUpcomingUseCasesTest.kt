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
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DashboardAndUpcomingUseCasesTest {

    private val utcZone = ZoneId.of("UTC")
    // Base time: Tuesday 2026-10-20 at 12:00 UTC
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getDashboardOccurrencesUseCase: GetDashboardOccurrencesUseCase
    private lateinit var getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        getDashboardOccurrencesUseCase = GetDashboardOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
        getUpcomingOccurrencesUseCase = GetUpcomingOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    private fun createEvent(id: String, title: String, dateTime: LocalDateTime): Event = Event(
        id = id,
        title = title,
        description = "Notes",
        eventTypeId = "type_1",
        categoryId = "cat_1",
        priority = Priority.NORMAL,
        lifecycleStatus = EventLifecycleStatus.ACTIVE,
        temporal = EventDateTime.DateTime(dateTime, utcZone),
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun getDashboardOccurrences_partitionsOverdueTodayTomorrowUpcomingCorrectly() = runTest {
        // Today is 2026-10-20 12:00
        val overdueEvent = createEvent("e_overdue", "Overdue", LocalDateTime.of(2026, 10, 19, 10, 0))
        val todayEvent = createEvent("e_today", "Today", LocalDateTime.of(2026, 10, 20, 16, 0))
        val tomorrowEvent = createEvent("e_tomorrow", "Tomorrow", LocalDateTime.of(2026, 10, 21, 9, 0))
        val upcomingEvent = createEvent("e_upcoming", "Upcoming", LocalDateTime.of(2026, 10, 24, 11, 0))

        eventRepository.saveEvent(overdueEvent)
        eventRepository.saveEvent(todayEvent)
        eventRepository.saveEvent(tomorrowEvent)
        eventRepository.saveEvent(upcomingEvent)

        val dashboard = getDashboardOccurrencesUseCase().first()

        assertEquals(1, dashboard.overdue.size)
        assertEquals("e_overdue", dashboard.overdue.first().event.id)
        assertEquals(OccurrenceStatus.OVERDUE, dashboard.overdue.first().occurrence.status)

        assertEquals(1, dashboard.today.size)
        assertEquals("e_today", dashboard.today.first().event.id)

        assertEquals(1, dashboard.tomorrow.size)
        assertEquals("e_tomorrow", dashboard.tomorrow.first().event.id)

        assertEquals(1, dashboard.upcoming.size)
        assertEquals("e_upcoming", dashboard.upcoming.first().event.id)
    }

    @Test
    fun getDashboardOccurrences_excludedException_isNotPresented() = runTest {
        val event = createEvent("e_excluded", "Excluded Event", LocalDateTime.of(2026, 10, 20, 15, 0))
        eventRepository.saveEvent(event)

        // Save an exception excluding this occurrence
        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 20, 15, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.EXCLUDED
        )
        occurrenceExceptionRepository.saveException(exception)

        val dashboard = getDashboardOccurrencesUseCase().first()
        assertTrue(dashboard.today.isEmpty())
    }

    @Test
    fun getUpcomingOccurrences_returnsFuturePendingOccurrencesSorted() = runTest {
        val event1 = createEvent("e1", "Event Later", LocalDateTime.of(2026, 10, 25, 10, 0))
        val event2 = createEvent("e2", "Event Sooner", LocalDateTime.of(2026, 10, 22, 10, 0))

        eventRepository.saveEvent(event1)
        eventRepository.saveEvent(event2)

        val upcoming = getUpcomingOccurrencesUseCase(30).first()
        assertEquals(2, upcoming.size)
        assertEquals("e2", upcoming[0].event.id)
        assertEquals("e1", upcoming[1].event.id)
    }
}
