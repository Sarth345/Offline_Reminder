package com.example.offlinereminder.domain.statistics

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.usecase.GetProductivityInsightsUseCase
import com.example.offlinereminder.domain.usecase.GetStatisticsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class StatisticsTest {

    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var categoryRepository: InMemoryCategoryRepository
    private lateinit var occurrenceExceptionRepository: InMemoryOccurrenceExceptionRepository
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var getStatisticsUseCase: GetStatisticsUseCase
    private lateinit var getInsightsUseCase: GetProductivityInsightsUseCase

    @Before
    fun setUp() {
        eventRepository = InMemoryEventRepository()
        categoryRepository = InMemoryCategoryRepository()
        occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
        timeProvider = TestTimeProvider(Instant.parse("2026-10-21T14:00:00Z"))

        getStatisticsUseCase = GetStatisticsUseCase(
            eventRepository = eventRepository,
            categoryRepository = categoryRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            timeProvider = timeProvider
        )
        getInsightsUseCase = GetProductivityInsightsUseCase(getStatisticsUseCase)
    }

    @Test
    fun statistics_emptySchedule_defaultValues() = runTest {
        val stats = getStatisticsUseCase()
        assertEquals(0, stats.totalActiveEvents)
        assertEquals(0, stats.completedOccurrencesCount)
        assertEquals(0f, stats.completionRatePercentage, 0.01f)

        val insights = getInsightsUseCase.generateInsights(stats)
        assertEquals(1, insights.size)
        assertEquals(InsightType.INFO, insights.first().type)
        assertEquals("guardrail_starter", insights.first().id)
    }

    @Test
    fun statistics_activeEvents_andCompletions_computedCorrectly() = runTest {
        val workCat = Category(id = "cat_work", name = "Work", color = 0xFF4285F4.toInt())
        categoryRepository.saveCategory(workCat)

        val now = timeProvider.nowInstant()

        // 1. One active event scheduled today
        val event1 = Event(
            id = "evt_1",
            title = "Team Standup",
            categoryId = workCat.id,
            eventTypeId = "type_meeting",
            priority = Priority.HIGH,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 10, 0)),
            createdAt = now,
            updatedAt = now
        )
        eventRepository.saveEvent(event1)

        // Mark event1 completed
        occurrenceExceptionRepository.saveException(
            OccurrenceException(
                id = "exc_1",
                eventId = event1.id,
                originalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                status = OccurrenceStatus.COMPLETED,
                completedAt = now
            )
        )

        // 2. Overdue event yesterday
        val event2 = Event(
            id = "evt_2",
            title = "Overdue task",
            categoryId = workCat.id,
            eventTypeId = "type_task",
            priority = Priority.CRITICAL,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 9, 0)),
            createdAt = now,
            updatedAt = now
        )
        eventRepository.saveEvent(event2)

        // 3. Future event tomorrow
        val event3 = Event(
            id = "evt_3",
            title = "Design review",
            categoryId = workCat.id,
            eventTypeId = "type_task",
            priority = Priority.NORMAL,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 11, 0)),
            createdAt = now,
            updatedAt = now
        )
        eventRepository.saveEvent(event3)

        val stats = getStatisticsUseCase()
        assertEquals(3, stats.totalActiveEvents)
        assertEquals(1, stats.completedOccurrencesCount)
        assertEquals(1, stats.overdueOccurrencesCount)
        assertEquals(50f, stats.completionRatePercentage, 0.01f) // 1 completed / (1 completed + 1 overdue)
        assertEquals(1, stats.categoryBreakdown.size)
        assertEquals("Work", stats.categoryBreakdown.first().categoryName)
        assertEquals(3, stats.categoryBreakdown.first().count)

        // Insights generation with guardrails satisfied (3 events + 1 completion = 4 items >= 3)
        val insights = getInsightsUseCase.generateInsights(stats)
        assertTrue(insights.any { it.type == InsightType.WARNING }) // overdue warning
        assertTrue(insights.any { it.id == "top_category" })
    }
}
