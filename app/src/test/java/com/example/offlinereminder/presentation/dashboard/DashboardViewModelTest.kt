package com.example.offlinereminder.presentation.dashboard

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.notification.InMemoryNotificationRepository
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.GetDashboardOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val notificationRepository = InMemoryNotificationRepository()
    private val alarmScheduler = TestAlarmScheduler()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getDashboardOccurrencesUseCase: GetDashboardOccurrencesUseCase
    private lateinit var completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    private lateinit var skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase
    private lateinit var rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase

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

        val completeUseCase = CompleteOccurrenceUseCase(timeProvider)
        completeOccurrenceActionUseCase = CompleteOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            completeOccurrenceUseCase = completeUseCase,
            timeProvider = timeProvider
        )

        skipOccurrenceActionUseCase = SkipOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler
        )

        rescheduleOccurrenceActionUseCase = RescheduleOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            timeProvider = timeProvider
        )
    }

    private fun createViewModel(): DashboardViewModel = DashboardViewModel(
        getDashboardOccurrencesUseCase = getDashboardOccurrencesUseCase,
        completeOccurrenceActionUseCase = completeOccurrenceActionUseCase,
        skipOccurrenceActionUseCase = skipOccurrenceActionUseCase,
        rescheduleOccurrenceActionUseCase = rescheduleOccurrenceActionUseCase,
        dispatcherProvider = dispatcherProvider
    )

    @Test
    fun init_loadsDashboardOccurrencesSuccessfully() = runTest(testDispatcher) {
        val event = Event(
            id = "e1",
            title = "Meeting Today",
            description = "Quarterly review",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 15, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(1, state.todayOccurrences.size)
        assertEquals("Meeting Today", state.todayOccurrences.first().event.title)
    }

    @Test
    fun onAction_completeOccurrence_savesException() = runTest(testDispatcher) {
        val event = Event(
            id = "e1",
            title = "Task Today",
            description = "",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 15, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val occurrence = viewModel.uiState.value.todayOccurrences.first().occurrence
        viewModel.onAction(DashboardAction.CompleteOccurrence(occurrence))
        testScheduler.advanceUntilIdle()

        val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(occurrence.occurrenceId)
        assertEquals(OccurrenceStatus.COMPLETED, exception?.status)
    }

    @Test
    fun onAction_skipOccurrence_savesException() = runTest(testDispatcher) {
        val event = Event(
            id = "e1",
            title = "Task Today",
            description = "",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 15, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val occurrence = viewModel.uiState.value.todayOccurrences.first().occurrence
        viewModel.onAction(DashboardAction.SkipOccurrence(occurrence))
        testScheduler.advanceUntilIdle()

        val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(occurrence.occurrenceId)
        assertEquals(OccurrenceStatus.SKIPPED, exception?.status)
    }
}
