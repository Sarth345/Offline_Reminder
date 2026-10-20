package com.example.offlinereminder.presentation.calendar

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.notification.InMemoryNotificationRepository
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.GetCalendarOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val utcZone = ZoneId.of("UTC")
    // Base time: Tuesday 2026-10-20 at 12:00 UTC
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val notificationRepository = InMemoryNotificationRepository()
    private val alarmScheduler = TestAlarmScheduler()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getCalendarOccurrencesUseCase: GetCalendarOccurrencesUseCase
    private lateinit var completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    private lateinit var skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase
    private lateinit var rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        occurrenceExceptionRepository.clear()

        getCalendarOccurrencesUseCase = GetCalendarOccurrencesUseCase(
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

    private fun createViewModel(): CalendarViewModel = CalendarViewModel(
        getCalendarOccurrencesUseCase = getCalendarOccurrencesUseCase,
        completeOccurrenceActionUseCase = completeOccurrenceActionUseCase,
        skipOccurrenceActionUseCase = skipOccurrenceActionUseCase,
        rescheduleOccurrenceActionUseCase = rescheduleOccurrenceActionUseCase,
        timeProvider = timeProvider,
        dispatcherProvider = dispatcherProvider
    )

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
    fun init_loadsCalendarState_forCurrentMonthAndSelectedToday() = runTest(testDispatcher) {
        val todayEvent = createEvent("e_today", "Dentist Today", LocalDateTime.of(2026, 10, 20, 14, 0))
        val otherDayEvent = createEvent("e_other", "Team Lunch", LocalDateTime.of(2026, 10, 25, 12, 0))

        eventRepository.saveEvent(todayEvent)
        eventRepository.saveEvent(otherDayEvent)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(YearMonth.of(2026, 10), state.displayedYearMonth)
        assertEquals(LocalDate.of(2026, 10, 20), state.selectedDate)
        assertEquals(2, state.monthOccurrences.size)
        assertEquals(1, state.selectedDateOccurrences.size)
        assertEquals("e_today", state.selectedDateOccurrences[0].event.id)
        assertTrue(state.datesWithOccurrences.contains(LocalDate.of(2026, 10, 20)))
        assertTrue(state.datesWithOccurrences.contains(LocalDate.of(2026, 10, 25)))
    }

    @Test
    fun selectDate_updatesSelectedDateAndOccurrences() = runTest(testDispatcher) {
        val todayEvent = createEvent("e_today", "Dentist Today", LocalDateTime.of(2026, 10, 20, 14, 0))
        val otherDayEvent = createEvent("e_other", "Team Lunch", LocalDateTime.of(2026, 10, 25, 12, 0))

        eventRepository.saveEvent(todayEvent)
        eventRepository.saveEvent(otherDayEvent)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Select Oct 25
        viewModel.onAction(CalendarAction.SelectDate(LocalDate.of(2026, 10, 25)))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 10, 25), state.selectedDate)
        assertEquals(1, state.selectedDateOccurrences.size)
        assertEquals("e_other", state.selectedDateOccurrences[0].event.id)
    }

    @Test
    fun monthNavigation_previousAndNextMonth() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.displayedYearMonth)

        // Previous month -> September 2026
        viewModel.onAction(CalendarAction.PreviousMonth)
        testScheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 9), viewModel.uiState.value.displayedYearMonth)

        // Next month -> October 2026
        viewModel.onAction(CalendarAction.NextMonth)
        testScheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.displayedYearMonth)

        // Next month again -> November 2026
        viewModel.onAction(CalendarAction.NextMonth)
        testScheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), viewModel.uiState.value.displayedYearMonth)
    }

    @Test
    fun jumpToToday_resetsToToday() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Navigate far away
        viewModel.onAction(CalendarAction.NextMonth)
        viewModel.onAction(CalendarAction.SelectDate(LocalDate.of(2026, 11, 15)))
        testScheduler.advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 11), viewModel.uiState.value.displayedYearMonth)
        assertEquals(LocalDate.of(2026, 11, 15), viewModel.uiState.value.selectedDate)

        // Jump to today
        viewModel.onAction(CalendarAction.JumpToToday)
        testScheduler.advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.displayedYearMonth)
        assertEquals(LocalDate.of(2026, 10, 20), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun occurrenceActions_completeSkipReschedule() = runTest(testDispatcher) {
        val event = createEvent("e_action", "Action Event", LocalDateTime.of(2026, 10, 20, 10, 0))
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val occ = viewModel.uiState.value.selectedDateOccurrences.first().occurrence

        // Complete
        viewModel.onAction(CalendarAction.CompleteOccurrence(occ))
        testScheduler.advanceUntilIdle()

        val exceptions = occurrenceExceptionRepository.getExceptionsForEvent("e_action").first()
        assertEquals(1, exceptions.size)
        assertEquals(OccurrenceStatus.COMPLETED, exceptions[0].status)

        // Reschedule
        val newDateTime = LocalDateTime.of(2026, 10, 22, 16, 0)
        viewModel.onAction(CalendarAction.RescheduleOccurrence(occ, newDateTime))
        testScheduler.advanceUntilIdle()

        val updatedExceptions = occurrenceExceptionRepository.getExceptionsForEvent("e_action").first()
        val rescheduled = updatedExceptions.find { it.rescheduledStartDateTime != null }
        assertEquals(newDateTime, rescheduled?.rescheduledStartDateTime)
    }

    @Test
    fun clearError_resetsErrorMessage() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(CalendarAction.ClearError)
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
