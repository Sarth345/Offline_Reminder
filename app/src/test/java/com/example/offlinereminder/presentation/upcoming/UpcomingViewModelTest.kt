package com.example.offlinereminder.presentation.upcoming

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
import com.example.offlinereminder.domain.usecase.GetUpcomingOccurrencesUseCase
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModelTest {

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

    private lateinit var getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase
    private lateinit var completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    private lateinit var skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase

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
    }

    private fun createViewModel(): UpcomingViewModel = UpcomingViewModel(
        getUpcomingOccurrencesUseCase = getUpcomingOccurrencesUseCase,
        completeOccurrenceActionUseCase = completeOccurrenceActionUseCase,
        skipOccurrenceActionUseCase = skipOccurrenceActionUseCase,
        dispatcherProvider = dispatcherProvider
    )

    @Test
    fun init_loadsUpcomingOccurrencesGroupedByDate() = runTest(testDispatcher) {
        val date1 = LocalDate.of(2026, 10, 22)
        val date2 = LocalDate.of(2026, 10, 23)

        val e1 = Event(
            id = "e1",
            title = "Dentist",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        val e2 = Event(
            id = "e2",
            title = "Doctor",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 23, 14, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(2, state.occurrences.size)
        assertEquals(2, state.occurrencesByDate.keys.size)
        assertEquals(1, state.occurrencesByDate[date1]?.size)
        assertEquals(1, state.occurrencesByDate[date2]?.size)
    }

    @Test
    fun actions_completeOccurrence() = runTest(testDispatcher) {
        val e1 = Event(
            id = "e1",
            title = "Upcoming Task",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 9, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(e1)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val occ = viewModel.uiState.value.occurrences.first().occurrence
        viewModel.onAction(UpcomingAction.CompleteOccurrence(occ))
        testScheduler.advanceUntilIdle()

        val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(occ.occurrenceId)
        assertEquals(OccurrenceStatus.COMPLETED, exception?.status)
    }
}
