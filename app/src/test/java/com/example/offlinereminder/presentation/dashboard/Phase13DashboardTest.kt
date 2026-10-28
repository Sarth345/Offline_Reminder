package com.example.offlinereminder.presentation.dashboard

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.GetDashboardOccurrencesUseCase
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

class Phase13DashboardTest {

    private val zone = ZoneId.of("UTC")
    // Wednesday 2026-10-21 at 12:00:00 UTC
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T12:00:00Z"),
        currentZoneId = zone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getDashboardOccurrencesUseCase: GetDashboardOccurrencesUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        occurrenceExceptionRepository.clear()
        getDashboardOccurrencesUseCase = GetDashboardOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    private fun createEvent(
        id: String,
        title: String,
        startDateTime: LocalDateTime,
        priority: Priority = Priority.NORMAL,
        isFavorite: Boolean = false,
        lifecycleStatus: EventLifecycleStatus = EventLifecycleStatus.ACTIVE
    ): Event = Event(
        id = id,
        title = title,
        description = "Test Description",
        eventTypeId = "type_1",
        categoryId = "cat_1",
        priority = priority,
        isFavorite = isFavorite,
        lifecycleStatus = lifecycleStatus,
        temporal = EventDateTime.DateTime(startDateTime, zone),
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun dashboard_classifiesTodayOverdueAndUpcomingCorrectly() = runTest {
        // Today is 2026-10-21 at 12:00
        val overdue = createEvent("e_overdue", "Overdue Task", LocalDateTime.of(2026, 10, 21, 10, 0))
        val today = createEvent("e_today", "Today Task", LocalDateTime.of(2026, 10, 21, 16, 0))
        val tomorrow = createEvent("e_tomorrow", "Tomorrow Task", LocalDateTime.of(2026, 10, 22, 10, 0))
        val upcoming = createEvent("e_upcoming", "Upcoming Task", LocalDateTime.of(2026, 10, 25, 9, 0))

        eventRepository.saveEvent(overdue)
        eventRepository.saveEvent(today)
        eventRepository.saveEvent(tomorrow)
        eventRepository.saveEvent(upcoming)

        val dashboard = getDashboardOccurrencesUseCase().first()

        assertEquals(1, dashboard.overdue.size)
        assertEquals("e_overdue", dashboard.overdue.first().event.id)

        assertEquals(1, dashboard.today.size)
        assertEquals("e_today", dashboard.today.first().event.id)

        assertTrue(dashboard.tomorrow.isEmpty())
        assertTrue(dashboard.upcoming.isEmpty())
    }

    @Test
    fun dashboard_classifiesCompletedTodayAndComputesProgress() = runTest {
        val completedEvent = createEvent("e_completed", "Completed Task", LocalDateTime.of(2026, 10, 21, 9, 0))
        val pendingEvent = createEvent("e_pending", "Pending Task", LocalDateTime.of(2026, 10, 21, 15, 0))

        eventRepository.saveEvent(completedEvent)
        eventRepository.saveEvent(pendingEvent)

        // Record completion exception for completedEvent
        val exception = OccurrenceException(
            eventId = "e_completed",
            originalStartDateTime = LocalDateTime.of(2026, 10, 21, 9, 0),
            originalZoneId = zone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = timeProvider.nowInstant()
        )
        occurrenceExceptionRepository.saveException(exception)

        val dashboard = getDashboardOccurrencesUseCase().first()

        assertEquals(1, dashboard.completedToday.size)
        assertEquals("e_completed", dashboard.completedToday.first().event.id)
        assertEquals(1, dashboard.today.size)
        assertEquals("e_pending", dashboard.today.first().event.id)

        assertEquals(1, dashboard.completedCountToday)
        assertEquals(2, dashboard.totalTodayCount)
        assertEquals(0.5f, dashboard.progress, 0.001f)
    }

    @Test
    fun dashboard_surfacesFavoritesAndHighPriority() = runTest {
        val favEvent = createEvent(
            id = "e_fav",
            title = "Starred Event",
            startDateTime = LocalDateTime.of(2026, 10, 21, 14, 0),
            isFavorite = true
        )
        val highPriorityEvent = createEvent(
            id = "e_high",
            title = "Critical Issue",
            startDateTime = LocalDateTime.of(2026, 10, 21, 15, 0),
            priority = Priority.CRITICAL
        )

        eventRepository.saveEvent(favEvent)
        eventRepository.saveEvent(highPriorityEvent)

        val dashboard = getDashboardOccurrencesUseCase().first()

        assertEquals(1, dashboard.favorites.size)
        assertEquals("e_fav", dashboard.favorites.first().event.id)

        assertEquals(1, dashboard.highPriority.size)
        assertEquals("e_high", dashboard.highPriority.first().event.id)
    }

    @Test
    fun dashboard_filtersOutArchivedAndTrashedEvents() = runTest {
        val active = createEvent("e_active", "Active", LocalDateTime.of(2026, 10, 21, 14, 0), lifecycleStatus = EventLifecycleStatus.ACTIVE)
        val archived = createEvent("e_archived", "Archived", LocalDateTime.of(2026, 10, 21, 14, 0), lifecycleStatus = EventLifecycleStatus.ARCHIVED)
        val trashed = createEvent("e_trashed", "Trashed", LocalDateTime.of(2026, 10, 21, 14, 0), lifecycleStatus = EventLifecycleStatus.TRASHED)

        eventRepository.saveEvent(active)
        eventRepository.saveEvent(archived)
        eventRepository.saveEvent(trashed)

        val dashboard = getDashboardOccurrencesUseCase().first()

        assertEquals(1, dashboard.today.size)
        assertEquals("e_active", dashboard.today.first().event.id)
    }

    @Test
    fun dashboard_respectsDateBoundaries() = runTest {
        // Far future event should not appear on Today Dashboard
        val farEvent = createEvent("e_far", "Far Future", LocalDateTime.of(2026, 11, 20, 10, 0))
        eventRepository.saveEvent(farEvent)

        val dashboard = getDashboardOccurrencesUseCase().first()
        assertTrue(dashboard.today.none { it.event.id == "e_far" })
        assertTrue(dashboard.upcoming.isEmpty())
    }

    @Test
    fun dashboard_recurringOccurrenceCompletion_doesNotCompleteMasterEvent() = runTest {
        val dailyRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1)
        val recurringEvent = Event(
            id = "e_recurring",
            title = "Daily Workout",
            description = "Stay healthy",
            eventTypeId = "type_1",
            categoryId = "cat_1",
            priority = Priority.NORMAL,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 8, 0), zone),
            recurrenceRule = dailyRule,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(recurringEvent)

        // Complete today's occurrence (Oct 21 at 08:00)
        val todayException = OccurrenceException(
            eventId = "e_recurring",
            originalStartDateTime = LocalDateTime.of(2026, 10, 21, 8, 0),
            originalZoneId = zone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = timeProvider.nowInstant()
        )
        occurrenceExceptionRepository.saveException(todayException)

        val dashboard = getDashboardOccurrencesUseCase().first()

        // 1. Verify master event remains ACTIVE
        val master = eventRepository.getEvent("e_recurring")
        assertEquals(EventLifecycleStatus.ACTIVE, master?.lifecycleStatus)

        // 2. Today's occurrence is in completedToday, not in today (pending)
        assertEquals(1, dashboard.completedToday.size)
        assertEquals("e_recurring", dashboard.completedToday.first().event.id)
        assertTrue(dashboard.today.none { it.event.id == "e_recurring" })

        // 3. Tomorrow's occurrence must NOT appear on Today Dashboard
        assertTrue(dashboard.tomorrow.isEmpty())
    }

    @Test
    fun dashboard_completedToday_respectsCompletionTimestampAcrossMidnight() = runTest {
        // Event from yesterday completed TODAY
        val yesterdayEvent = createEvent("e_yest", "Yesterday task", LocalDateTime.of(2026, 10, 20, 15, 0))
        eventRepository.saveEvent(yesterdayEvent)
        val completedTodayException = OccurrenceException(
            eventId = "e_yest",
            originalStartDateTime = LocalDateTime.of(2026, 10, 20, 15, 0),
            originalZoneId = zone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = Instant.parse("2026-10-21T09:00:00Z") // Completed today!
        )
        occurrenceExceptionRepository.saveException(completedTodayException)

        // Event from today completed YESTERDAY (e.g. advance completion)
        val todayEvent = createEvent("e_adv", "Advance task", LocalDateTime.of(2026, 10, 21, 16, 0))
        eventRepository.saveEvent(todayEvent)
        val completedYesterdayException = OccurrenceException(
            eventId = "e_adv",
            originalStartDateTime = LocalDateTime.of(2026, 10, 21, 16, 0),
            originalZoneId = zone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = Instant.parse("2026-10-20T22:00:00Z") // Completed yesterday!
        )
        occurrenceExceptionRepository.saveException(completedYesterdayException)

        val dashboard = getDashboardOccurrencesUseCase().first()

        // yesterdayEvent was completed TODAY, so it IS in completedToday
        assertTrue(dashboard.completedToday.any { it.event.id == "e_yest" })

        // todayEvent was completed YESTERDAY, so it is NOT in completedToday
        assertFalse(dashboard.completedToday.any { it.event.id == "e_adv" })
    }
}
