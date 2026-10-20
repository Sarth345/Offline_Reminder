package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.notification.InMemoryNotificationRepository
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class EventCrudUseCasesTest {

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
    private lateinit var cancelNotificationsUseCase: CancelNotificationsForEventUseCase
    private lateinit var validateEventUseCase: ValidateEventUseCase
    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var updateEventUseCase: UpdateEventUseCase
    private lateinit var deleteEventUseCase: DeleteEventUseCase
    private lateinit var getEventByIdUseCase: GetEventByIdUseCase
    private lateinit var getEventsUseCase: GetEventsUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        notificationRepository.clear()
        alarmScheduler.clear()

        validateEventUseCase = ValidateEventUseCase()
        scheduleNotificationsUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
        cancelNotificationsUseCase = CancelNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler
        )

        createEventUseCase = CreateEventUseCase(
            eventRepository = eventRepository,
            validateEventUseCase = validateEventUseCase,
            scheduleNotificationsForEventUseCase = scheduleNotificationsUseCase,
            timeProvider = timeProvider
        )

        updateEventUseCase = UpdateEventUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            validateEventUseCase = validateEventUseCase,
            scheduleNotificationsForEventUseCase = scheduleNotificationsUseCase,
            cancelNotificationsForEventUseCase = cancelNotificationsUseCase,
            timeProvider = timeProvider
        )

        deleteEventUseCase = DeleteEventUseCase(
            eventRepository = eventRepository,
            cancelNotificationsForEventUseCase = cancelNotificationsUseCase
        )

        getEventByIdUseCase = GetEventByIdUseCase(eventRepository)
        getEventsUseCase = GetEventsUseCase(eventRepository)
    }

    private fun sampleEvent(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Event",
        status: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
        schedules: List<NotificationSchedule> = emptyList()
    ): Event = Event(
        id = id,
        title = title,
        description = "Description",
        eventTypeId = "type_1",
        categoryId = "cat_1",
        priority = Priority.HIGH,
        lifecycleStatus = status,
        temporal = EventDateTime.DateTime(
            dateTime = LocalDateTime.of(2026, 10, 22, 14, 0),
            zoneId = utcZone
        ),
        notificationSchedules = schedules,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun createEvent_validActiveEvent_persistsAndSchedulesNotifications() = runTest {
        val event = sampleEvent(
            schedules = listOf(NotificationSchedule.atStart())
        )

        val result = createEventUseCase(event)
        assertTrue(result.isSuccess)

        val saved = eventRepository.getEvent(event.id)
        assertNotNull(saved)
        assertEquals("Test Event", saved?.title)
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun createEvent_blankTitle_failsValidationWithoutSaving() = runTest {
        val event = sampleEvent(title = "   ")

        val result = createEventUseCase(event)
        assertTrue(result.isFailure)

        val saved = eventRepository.getEvent(event.id)
        assertNull(saved)
        assertEquals(0, alarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun updateEvent_validActiveEvent_updatesAndReconcilesAlarms() = runTest {
        val event = sampleEvent(
            schedules = listOf(NotificationSchedule.atStart())
        )
        createEventUseCase(event)
        assertEquals(1, alarmScheduler.scheduledAlarms.size)

        // Update with an extra notification schedule
        val updated = event.copy(
            title = "Updated Title",
            notificationSchedules = listOf(
                NotificationSchedule.atStart(),
                NotificationSchedule.beforeStart(Duration.ofHours(1))
            )
        )

        val updateResult = updateEventUseCase(updated)
        assertTrue(updateResult.isSuccess)

        val saved = eventRepository.getEvent(event.id)
        assertEquals("Updated Title", saved?.title)
        assertEquals(2, alarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun updateEvent_trashingEvent_cancelsAllAlarms() = runTest {
        val event = sampleEvent(
            schedules = listOf(NotificationSchedule.atStart())
        )
        createEventUseCase(event)
        assertEquals(1, alarmScheduler.scheduledAlarms.size)

        val trashed = event.copy(lifecycleStatus = EventLifecycleStatus.TRASHED)
        updateEventUseCase(trashed)

        assertEquals(1, alarmScheduler.cancelledAlarms.size)
    }

    @Test
    fun deleteEvent_deletesFromRepositoryAndCancelsAlarms() = runTest {
        val event = sampleEvent(
            schedules = listOf(NotificationSchedule.atStart())
        )
        createEventUseCase(event)
        assertEquals(1, alarmScheduler.scheduledAlarms.size)

        val deleteResult = deleteEventUseCase(event.id)
        assertTrue(deleteResult.isSuccess)

        assertNull(eventRepository.getEvent(event.id))
        assertEquals(1, alarmScheduler.cancelledAlarms.size)
    }

    @Test
    fun getEventById_and_getEvents_observeCorrectData() = runTest {
        val event1 = sampleEvent(id = "e1", title = "Event 1")
        val event2 = sampleEvent(id = "e2", title = "Event 2", status = EventLifecycleStatus.TRASHED)

        createEventUseCase(event1)
        createEventUseCase(event2)

        val observedSingle = getEventByIdUseCase("e1").first()
        assertEquals("Event 1", observedSingle?.title)

        val activeEvents = getEventsUseCase.getAllActive().first()
        assertEquals(1, activeEvents.size)
        assertEquals("e1", activeEvents.first().id)

        val trashedEvents = getEventsUseCase.getByStatus(EventLifecycleStatus.TRASHED).first()
        assertEquals(1, trashedEvents.size)
        assertEquals("e2", trashedEvents.first().id)
    }
}
