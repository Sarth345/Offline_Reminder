package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventFilterParams
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SearchEventsUseCaseTest {

    private val utcZone = ZoneId.of("UTC")
    // Tuesday 2026-10-20 at 12:00 UTC
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var searchEventsUseCase: SearchEventsUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        occurrenceExceptionRepository.clear()
        searchEventsUseCase = SearchEventsUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    private fun createEvent(
        id: String,
        title: String,
        description: String = "",
        categoryId: String = "cat_general",
        eventTypeId: String = "type_reminder",
        priority: Priority = Priority.NORMAL,
        temporal: EventDateTime,
        isFavorite: Boolean = false,
        groupIds: Set<String> = emptySet(),
        tagIds: Set<String> = emptySet(),
        lifecycleStatus: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
        recurrenceRule: RecurrenceRule? = null
    ): Event = Event(
        id = id,
        title = title,
        description = description,
        eventTypeId = eventTypeId,
        categoryId = categoryId,
        priority = priority,
        temporal = temporal,
        isFavorite = isFavorite,
        groupIds = groupIds,
        tagIds = tagIds,
        lifecycleStatus = lifecycleStatus,
        recurrenceRule = recurrenceRule,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    // ==========================================
    // 1. Text Search Tests
    // ==========================================

    @Test
    fun search_byTitle_findsExactAndPartialMatches() = runTest {
        val e1 = createEvent("e1", "Dentist Appointment", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Doctor Checkup", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(query = "dent")).first()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun search_byDescription_matchesText() = runTest {
        val e1 = createEvent("e1", "Meeting", description = "Quarterly financial review", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Lunch", description = "With team", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(query = "financial")).first()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun search_isCaseInsensitive() = runTest {
        val e1 = createEvent("e1", "IMPORTANT PROJECT DEADLINE", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        eventRepository.saveEvent(e1)

        val results = searchEventsUseCase(EventFilterParams(query = "project deadline")).first()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun search_noResults_returnsEmptyList() = runTest {
        val e1 = createEvent("e1", "Gym Workout", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        eventRepository.saveEvent(e1)

        val results = searchEventsUseCase(EventFilterParams(query = "NonExistent")).first()
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_emptyQuery_returnsAllActiveEvents() = runTest {
        val e1 = createEvent("e1", "Event 1", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Event 2", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(query = "")).first()
        assertEquals(2, results.size)
    }

    // ==========================================
    // 2. Filter Dimensions Tests
    // ==========================================

    @Test
    fun filter_byCategory() = runTest {
        val e1 = createEvent("e1", "Work Task", categoryId = "cat_work", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Health Task", categoryId = "cat_health", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(categoryIds = setOf("cat_work"))).first()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun filter_byEventType() = runTest {
        val e1 = createEvent("e1", "Reminder", eventTypeId = "type_reminder", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Bill", eventTypeId = "type_bill", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(eventTypeIds = setOf("type_bill"))).first()
        assertEquals(1, results.size)
        assertEquals("e2", results[0].id)
    }

    @Test
    fun filter_byGroup() = runTest {
        val e1 = createEvent("e1", "Team Meeting", groupIds = setOf("grp_work"), temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Dinner", groupIds = setOf("grp_family"), temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(groupIds = setOf("grp_work"))).first()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun filter_byTag() = runTest {
        val e1 = createEvent("e1", "Task 1", tagIds = setOf("tag_urgent", "tag_bug"), temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Task 2", tagIds = setOf("tag_feature"), temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(tagIds = setOf("tag_urgent"))).first()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun filter_byPriority() = runTest {
        val e1 = createEvent("e1", "Low Priority", priority = Priority.LOW, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "High Priority", priority = Priority.HIGH, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        val e3 = createEvent("e3", "Critical Priority", priority = Priority.CRITICAL, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 27)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)
        eventRepository.saveEvent(e3)

        val results = searchEventsUseCase(EventFilterParams(priorities = setOf(Priority.HIGH, Priority.CRITICAL))).first()
        assertEquals(2, results.size)
        val ids = results.map { it.id }.toSet()
        assertEquals(setOf("e2", "e3"), ids)
    }

    @Test
    fun filter_byLifecycleStatus() = runTest {
        val eActive = createEvent("eActive", "Active", lifecycleStatus = EventLifecycleStatus.ACTIVE, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val eArchived = createEvent("eArchived", "Archived", lifecycleStatus = EventLifecycleStatus.ARCHIVED, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val eTrashed = createEvent("eTrashed", "Trashed", lifecycleStatus = EventLifecycleStatus.TRASHED, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        eventRepository.saveEvent(eActive)
        eventRepository.saveEvent(eArchived)
        eventRepository.saveEvent(eTrashed)

        val archivedResults = searchEventsUseCase(EventFilterParams(lifecycleStatuses = setOf(EventLifecycleStatus.ARCHIVED))).first()
        assertEquals(1, archivedResults.size)
        assertEquals("eArchived", archivedResults[0].id)

        val trashedResults = searchEventsUseCase(EventFilterParams(lifecycleStatuses = setOf(EventLifecycleStatus.TRASHED))).first()
        assertEquals(1, trashedResults.size)
        assertEquals("eTrashed", trashedResults[0].id)
    }

    @Test
    fun filter_byFavoritesOnly() = runTest {
        val e1 = createEvent("e1", "Favorite Event", isFavorite = true, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        val e2 = createEvent("e2", "Normal Event", isFavorite = false, temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 26)))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val results = searchEventsUseCase(EventFilterParams(onlyFavorites = true)).first()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun filter_byOverdueOnly() = runTest {
        // Current time is 2026-10-20 12:00
        val pastEvent = createEvent("past", "Overdue", temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 18, 10, 0), utcZone))
        val completedPastEvent = createEvent("completedPast", "Completed", temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 18, 11, 0), utcZone))
        val futureEvent = createEvent("future", "Future", temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 10, 0), utcZone))

        eventRepository.saveEvent(pastEvent)
        eventRepository.saveEvent(completedPastEvent)
        eventRepository.saveEvent(futureEvent)

        // Mark completedPastEvent as completed via exception
        val exc = OccurrenceException(
            eventId = "completedPast",
            originalStartDateTime = LocalDateTime.of(2026, 10, 18, 11, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = timeProvider.nowInstant()
        )
        occurrenceExceptionRepository.saveException(exc)

        val results = searchEventsUseCase(EventFilterParams(onlyOverdue = true)).first()
        assertEquals(1, results.size)
        assertEquals("past", results[0].id)
    }

    // ==========================================
    // 3. Composable AND Filters Tests
    // ==========================================

    @Test
    fun filter_multipleConstraints_combinedWithAndSemantics() = runTest {
        val eMatch = createEvent(
            id = "eMatch",
            title = "Final Exam Preparation",
            categoryId = "cat_academic",
            priority = Priority.HIGH,
            groupIds = setOf("grp_university"),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25))
        )
        val eWrongCategory = createEvent(
            id = "eWrongCat",
            title = "Final Exam Preparation",
            categoryId = "cat_personal",
            priority = Priority.HIGH,
            groupIds = setOf("grp_university"),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25))
        )
        val eWrongPriority = createEvent(
            id = "eWrongPri",
            title = "Final Exam Preparation",
            categoryId = "cat_academic",
            priority = Priority.LOW,
            groupIds = setOf("grp_university"),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25))
        )
        val eWrongGroup = createEvent(
            id = "eWrongGrp",
            title = "Final Exam Preparation",
            categoryId = "cat_academic",
            priority = Priority.HIGH,
            groupIds = setOf("grp_other"),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25))
        )

        eventRepository.saveEvent(eMatch)
        eventRepository.saveEvent(eWrongCategory)
        eventRepository.saveEvent(eWrongPriority)
        eventRepository.saveEvent(eWrongGroup)

        val params = EventFilterParams(
            query = "exam",
            categoryIds = setOf("cat_academic"),
            priorities = setOf(Priority.HIGH),
            groupIds = setOf("grp_university")
        )

        val results = searchEventsUseCase(params).first()
        assertEquals(1, results.size)
        assertEquals("eMatch", results[0].id)
    }

    // ==========================================
    // 4. Date Semantics Tests
    // ==========================================

    @Test
    fun filter_dateOnly_intersectsDateRange() = runTest {
        val event = createEvent("e1", "DateOnly", temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)))
        eventRepository.saveEvent(event)

        // Range covering event
        val inRange = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 20),
                endDate = LocalDate.of(2026, 10, 30)
            )
        ).first()
        assertEquals(1, inRange.size)

        // Range before event
        val outOfRange = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 1),
                endDate = LocalDate.of(2026, 10, 10)
            )
        ).first()
        assertTrue(outOfRange.isEmpty())
    }

    @Test
    fun filter_dateTime_intersectsDateRange() = runTest {
        val event = createEvent("e1", "DateTime", temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 14, 0), utcZone))
        eventRepository.saveEvent(event)

        val results = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 25),
                endDate = LocalDate.of(2026, 10, 25)
            )
        ).first()
        assertEquals(1, results.size)
    }

    @Test
    fun filter_dateRange_intersectsFilterRange() = runTest {
        // Multi-day trip: Oct 22 to Oct 28
        val event = createEvent(
            "eTrip",
            "Vacation",
            temporal = EventDateTime.DateRange(
                startDate = LocalDate.of(2026, 10, 22),
                endDate = LocalDate.of(2026, 10, 28)
            )
        )
        eventRepository.saveEvent(event)

        // Overlapping filter range: Oct 27 to Nov 2
        val results = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 27),
                endDate = LocalDate.of(2026, 11, 2)
            )
        ).first()
        assertEquals(1, results.size)

        // Non-overlapping filter range: Oct 10 to Oct 20
        val nonOverlapping = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 10),
                endDate = LocalDate.of(2026, 10, 20)
            )
        ).first()
        assertTrue(nonOverlapping.isEmpty())
    }

    @Test
    fun filter_dateTimeRange_intersectsFilterRange() = runTest {
        val event = createEvent(
            "eConf",
            "Conference",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 22, 9, 0),
                endDateTime = LocalDateTime.of(2026, 10, 24, 18, 0),
                zoneId = utcZone
            )
        )
        eventRepository.saveEvent(event)

        val results = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 23),
                endDate = LocalDate.of(2026, 10, 23)
            )
        ).first()
        assertEquals(1, results.size)
    }

    @Test
    fun filter_recurringEvent_matchesWhenOccurrenceIntersectsDateRange() = runTest {
        val weeklyEvent = createEvent(
            id = "recWeekly",
            title = "Weekly Team Sync",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 5, 10, 0), utcZone), // Mondays: Oct 5, 12, 19, 26
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 1,
                endCondition = RecurrenceEnd.Never
            )
        )
        eventRepository.saveEvent(weeklyEvent)

        // Filter range: Oct 18 to Oct 20 (includes Monday Oct 19)
        val results = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 18),
                endDate = LocalDate.of(2026, 10, 20)
            )
        ).first()
        assertEquals(1, results.size)
        assertEquals("recWeekly", results[0].id)

        // Filter range: Oct 13 to Oct 15 (no occurrences on Tuesday-Thursday)
        val noResults = searchEventsUseCase(
            EventFilterParams(
                startDate = LocalDate.of(2026, 10, 13),
                endDate = LocalDate.of(2026, 10, 15)
            )
        ).first()
        assertTrue(noResults.isEmpty())
    }
}
