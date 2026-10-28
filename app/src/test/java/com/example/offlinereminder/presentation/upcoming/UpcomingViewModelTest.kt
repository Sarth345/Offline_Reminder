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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        timeProvider = timeProvider,
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

    @Test
    fun multiDayEvent_spansAcrossDatesInGroupedView_withoutDuplicateOccurrences() = runTest(testDispatcher) {
        // Multi-day event: Start 2026-10-21 22:00, End 2026-10-23 10:00
        val multiDay = Event(
            id = "e_multi",
            title = "Weekend Conference",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 21, 22, 0),
                endDateTime = LocalDateTime.of(2026, 10, 23, 10, 0),
                zoneId = utcZone
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(multiDay)

        val viewModel = createViewModel()
        viewModel.onAction(UpcomingAction.SelectDate(LocalDate.of(2026, 10, 21)))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // 1. Exactly ONE occurrence object exists in the main occurrences list
        assertEquals(1, state.occurrences.size)
        assertTrue(state.occurrences.first().occurrence.isMultiDay)

        // 2. The event appears under Oct 21, Oct 22, and Oct 23 in the grouped map
        val day21 = state.occurrencesByDate[LocalDate.of(2026, 10, 21)]
        val day22 = state.occurrencesByDate[LocalDate.of(2026, 10, 22)]
        val day23 = state.occurrencesByDate[LocalDate.of(2026, 10, 23)]
        val day24 = state.occurrencesByDate[LocalDate.of(2026, 10, 24)]

        assertNotNull(day21)
        assertEquals("e_multi", day21?.first()?.event?.id)

        assertNotNull(day22)
        assertEquals("e_multi", day22?.first()?.event?.id)

        assertNotNull(day23)
        assertEquals("e_multi", day23?.first()?.event?.id)

        assertNull("Event ended on Oct 23, should not appear on Oct 24", day24)
    }

    @Test
    fun dateNavigation_andControls_updateAgendaProjectionAndState() = runTest(testDispatcher) {
        val today = LocalDate.of(2026, 10, 20) // from timeProvider
        val day21 = today.plusDays(1)
        val day22 = today.plusDays(2)

        val eToday = Event(
            id = "e_today",
            title = "Today Event",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 14, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        val e21 = Event(
            id = "e_21",
            title = "Tomorrow Event",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 10, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        val e22 = Event(
            id = "e_22",
            title = "Day After Tomorrow Event",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 16, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        eventRepository.saveEvent(eToday)
        eventRepository.saveEvent(e21)
        eventRepository.saveEvent(e22)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Initially in Next mode: strictly after today (day21 and day22), eToday is excluded
        assertEquals(2, viewModel.uiState.value.occurrences.size)
        assertTrue(viewModel.uiState.value.occurrences.none { it.event.id == "e_today" })

        // 1. SelectDate: switches to DATE mode on day 21 (all occurrences on day 21)
        viewModel.onAction(UpcomingAction.SelectDate(day21))
        testScheduler.advanceUntilIdle()
        assertEquals(day21, viewModel.uiState.value.selectedDate)
        assertEquals(UpcomingMode.DATE, viewModel.uiState.value.mode)
        assertEquals(1, viewModel.uiState.value.occurrences.size)
        assertEquals("e_21", viewModel.uiState.value.occurrences.first().event.id)

        // 2. NavigateDays: +1 day moves date to day 22
        viewModel.onAction(UpcomingAction.NavigateDays(1))
        testScheduler.advanceUntilIdle()
        assertEquals(day22, viewModel.uiState.value.selectedDate)
        assertEquals(UpcomingMode.DATE, viewModel.uiState.value.mode)
        assertEquals(1, viewModel.uiState.value.occurrences.size)
        assertEquals("e_22", viewModel.uiState.value.occurrences.first().event.id)

        // 3. JumpToToday: resets date back to today (2026-10-20) in DATE mode
        viewModel.onAction(UpcomingAction.JumpToToday)
        testScheduler.advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertEquals(UpcomingMode.DATE, viewModel.uiState.value.mode)
        assertEquals(1, viewModel.uiState.value.occurrences.size)
        assertEquals("e_today", viewModel.uiState.value.occurrences.first().event.id)
    }
}
