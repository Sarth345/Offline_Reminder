package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.notification.InMemoryNotificationRepository
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class OccurrenceActionUseCasesTest {

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T10:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val notificationRepository = InMemoryNotificationRepository()
    private val alarmScheduler = TestAlarmScheduler()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var scheduleNotificationsUseCase: ScheduleNotificationsForEventUseCase
    private lateinit var completeOccurrenceUseCase: CompleteOccurrenceUseCase
    private lateinit var completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    private lateinit var skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase
    private lateinit var rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        notificationRepository.clear()
        alarmScheduler.clear()

        scheduleNotificationsUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )

        completeOccurrenceUseCase = CompleteOccurrenceUseCase(timeProvider)

        completeOccurrenceActionUseCase = CompleteOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            completeOccurrenceUseCase = completeOccurrenceUseCase,
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
            scheduleNotificationsForEventUseCase = scheduleNotificationsUseCase,
            timeProvider = timeProvider
        )
    }

    @Test
    fun completeOccurrence_recurringEvent_savesExceptionAndCancelsPendingAlarm() = runTest {
        val startLdt = LocalDateTime.of(2026, 10, 20, 14, 0)
        val event = Event(
            id = "recurring_1",
            title = "Daily Standup",
            description = "",
            eventTypeId = "t1",
            categoryId = "c1",
            priority = Priority.NORMAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(startLdt, utcZone),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY),
            notificationSchedules = listOf(NotificationSchedule.atStart()),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        scheduleNotificationsUseCase(event)
        assertEquals(14, alarmScheduler.scheduledAlarms.size) // 14-day horizon

        val occurrence = recurrenceEngine.projectOccurrences(
            event = event,
            from = startLdt,
            to = startLdt.plusDays(1)
        ).first()

        val result = completeOccurrenceActionUseCase(occurrence)
        assertTrue(result.isSuccess)

        // Verifies exception was saved in repository
        val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(occurrence.occurrenceId)
        assertNotNull(exception)
        assertEquals(OccurrenceStatus.COMPLETED, exception?.status)

        // Verifies the alarm for this specific occurrence was cancelled
        assertEquals(1, alarmScheduler.cancelledAlarms.size)
    }

    @Test
    fun skipOccurrence_savesSkippedException() = runTest {
        val startLdt = LocalDateTime.of(2026, 10, 21, 10, 0)
        val event = Event(
            id = "one_time_1",
            title = "Doctor Appointment",
            description = "",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(startLdt, utcZone),
            notificationSchedules = listOf(NotificationSchedule.atStart()),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        scheduleNotificationsUseCase(event)
        assertEquals(1, alarmScheduler.scheduledAlarms.size)

        val occurrence = event.createInitialOccurrence()
        val result = skipOccurrenceActionUseCase(occurrence)
        assertTrue(result.isSuccess)

        val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(occurrence.occurrenceId)
        assertNotNull(exception)
        assertEquals(OccurrenceStatus.SKIPPED, exception?.status)
        assertEquals(1, alarmScheduler.cancelledAlarms.size)
    }

    @Test
    fun rescheduleOccurrence_savesRescheduledExceptionAndReconcilesAlarms() = runTest {
        val startLdt = LocalDateTime.of(2026, 10, 22, 10, 0)
        val event = Event(
            id = "event_resched",
            title = "Client Call",
            description = "",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(startLdt, utcZone),
            notificationSchedules = listOf(NotificationSchedule.atStart()),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        scheduleNotificationsUseCase(event)

        val occurrence = event.createInitialOccurrence()
        val newStart = LocalDateTime.of(2026, 10, 22, 16, 0)

        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = newStart
        )
        assertTrue(result.isSuccess)

        val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(occurrence.occurrenceId)
        assertNotNull(exception)
        assertEquals(OccurrenceStatus.RESCHEDULED, exception?.status)
        assertEquals(newStart, exception?.rescheduledStartDateTime)
    }
}
