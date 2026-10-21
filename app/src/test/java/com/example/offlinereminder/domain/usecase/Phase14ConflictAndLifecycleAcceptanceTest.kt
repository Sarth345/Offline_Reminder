package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.ConflictSeverity
import com.example.offlinereminder.domain.model.ConflictType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.InMemoryNotificationRepository
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class Phase14ConflictAndLifecycleAcceptanceTest {

    private val testDispatcher = StandardTestDispatcher()

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val notificationRepository = InMemoryNotificationRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)
    private val testAlarmScheduler = TestAlarmScheduler()

    private lateinit var detectEventConflictsUseCase: DetectEventConflictsUseCase
    private lateinit var trashEventUseCase: TrashEventUseCase
    private lateinit var restoreTrashedEventUseCase: RestoreTrashedEventUseCase
    private lateinit var archiveEventUseCase: ArchiveEventUseCase
    private lateinit var restoreArchivedEventUseCase: RestoreArchivedEventUseCase
    private lateinit var permanentlyDeleteEventUseCase: PermanentlyDeleteEventUseCase
    private lateinit var emptyTrashUseCase: EmptyTrashUseCase
    private lateinit var purgeExpiredTrashUseCase: PurgeExpiredTrashUseCase
    private lateinit var scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase
    private lateinit var cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase

    @Before
    fun setUp() = runTest(testDispatcher) {
        eventRepository.clear()
        notificationRepository.clear()
        testAlarmScheduler.clear()

        detectEventConflictsUseCase = DetectEventConflictsUseCase(
            eventRepository = eventRepository,
            recurrenceEngine = recurrenceEngine,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            timeProvider = timeProvider
        )

        trashEventUseCase = TrashEventUseCase(timeProvider, eventRepository)
        restoreTrashedEventUseCase = RestoreTrashedEventUseCase(eventRepository)
        archiveEventUseCase = ArchiveEventUseCase(timeProvider, eventRepository)
        restoreArchivedEventUseCase = RestoreArchivedEventUseCase(eventRepository)
        permanentlyDeleteEventUseCase = PermanentlyDeleteEventUseCase(eventRepository)
        emptyTrashUseCase = EmptyTrashUseCase(eventRepository)
        purgeExpiredTrashUseCase = PurgeExpiredTrashUseCase(eventRepository, timeProvider)

        scheduleNotificationsForEventUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = testAlarmScheduler,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
        cancelNotificationsForEventUseCase = CancelNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = testAlarmScheduler
        )
    }

    // ==========================================
    // 1. Conflict Acceptance Tests
    // ==========================================

    @Test
    fun conflicts_overlappingEvents_detectedAcrossTimezonesAndDst() = runTest(testDispatcher) {
        // Event A: in America/New_York (EDT, UTC-4 in Oct 2026)
        // 14:00 - 15:00 EDT = 18:00 - 19:00 UTC
        val eventA = Event(
            id = "event-ny",
            title = "New York Client Sync",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
                endDateTime = LocalDateTime.of(2026, 10, 25, 15, 0),
                zoneId = ZoneId.of("America/New_York")
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(eventA)

        // Event B: in UTC, 18:30 - 19:30 UTC (overlaps 18:30-19:00 UTC with Event A)
        val eventB = Event(
            id = "event-london",
            title = "Global Engineering All-Hands",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 25, 18, 30),
                endDateTime = LocalDateTime.of(2026, 10, 25, 19, 30),
                zoneId = ZoneId.of("UTC")
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        val conflicts = detectEventConflictsUseCase(eventB)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals("event-ny", conflict.existingEventId)
        assertEquals(ConflictType.DIRECT_OVERLAP, conflict.type)
        assertEquals(ConflictSeverity.HARD, conflict.severity)

        // Event C: 16:00 - 17:00 UTC (no overlap with Event A 18:00-19:00 UTC)
        val eventC = Event(
            id = "event-early",
            title = "Morning Standup",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 25, 16, 0),
                endDateTime = LocalDateTime.of(2026, 10, 25, 17, 0),
                zoneId = ZoneId.of("UTC")
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        val noConflicts = detectEventConflictsUseCase(eventC)
        assertTrue(noConflicts.isEmpty())
    }

    @Test
    fun conflicts_allDayCoexistence_remainsAdvisorySeverity() = runTest(testDispatcher) {
        val allDay1 = Event(
            id = "allday-1",
            title = "Company Holiday",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(allDay1)

        val allDay2 = Event(
            id = "allday-2",
            title = "Team Hackathon",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        val conflicts = detectEventConflictsUseCase(allDay2)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals(ConflictType.ALL_DAY_COEXISTENCE, conflict.type)
        assertEquals(ConflictSeverity.ADVISORY, conflict.severity)
    }

    @Test
    fun conflicts_horizonRespectsDefaultWindow_andNeverPersistsConflicts() = runTest(testDispatcher) {
        // Event outside horizon (45 days in future)
        val farFutureEvent = Event(
            id = "event-far",
            title = "Next Quarter Strategy",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 12, 10, 10, 0),
                endDateTime = LocalDateTime.of(2026, 12, 10, 11, 0),
                zoneId = ZoneId.of("UTC")
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(farFutureEvent)

        val conflictingFarEvent = farFutureEvent.copy(id = "event-far-2")
        val conflicts = detectEventConflictsUseCase(conflictingFarEvent)
        // Outside default 30-day window, so not evaluated
        assertTrue(conflicts.isEmpty())
    }

    // ==========================================
    // 2. Lifecycle Acceptance Tests
    // ==========================================

    @Test
    fun lifecycle_activeArchivedAndTrashedTransitions_areIdempotentAndCorrect() = runTest(testDispatcher) {
        val event = Event(
            id = "life-1",
            title = "Project Launch",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        // 1. ACTIVE -> ARCHIVED
        archiveEventUseCase("life-1")
        val archived = eventRepository.getEvent("life-1")
        assertEquals(EventLifecycleStatus.ARCHIVED, archived?.lifecycleStatus)
        assertNull(archived?.deletedAt)

        // 2. ARCHIVED -> ACTIVE
        restoreArchivedEventUseCase("life-1")
        val unarchived = eventRepository.getEvent("life-1")
        assertEquals(EventLifecycleStatus.ACTIVE, unarchived?.lifecycleStatus)

        // 3. ACTIVE -> TRASHED
        trashEventUseCase("life-1")
        val trashed = eventRepository.getEvent("life-1")
        assertEquals(EventLifecycleStatus.TRASHED, trashed?.lifecycleStatus)
        assertNotNull(trashed?.deletedAt)

        // 4. TRASHED -> ACTIVE
        restoreTrashedEventUseCase("life-1")
        val restored = eventRepository.getEvent("life-1")
        assertEquals(EventLifecycleStatus.ACTIVE, restored?.lifecycleStatus)
        assertNull(restored?.deletedAt)

        // 5. TRASHED -> PERMANENT DELETE
        trashEventUseCase("life-1")
        permanentlyDeleteEventUseCase("life-1")
        val deleted = eventRepository.getEvent("life-1")
        assertNull(deleted)
    }

    @Test
    fun lifecycle_emptyTrash_removesOnlyTrashedItems() = runTest(testDispatcher) {
        val activeEvent = Event(
            id = "active-keep",
            title = "Daily Medication",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        val trashedEvent1 = activeEvent.copy(
            id = "trash-del-1",
            title = "Old Reminder 1",
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = timeProvider.nowInstant()
        )
        val trashedEvent2 = activeEvent.copy(
            id = "trash-del-2",
            title = "Old Reminder 2",
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = timeProvider.nowInstant()
        )

        eventRepository.saveEvent(activeEvent)
        eventRepository.saveEvent(trashedEvent1)
        eventRepository.saveEvent(trashedEvent2)

        emptyTrashUseCase()

        assertNotNull(eventRepository.getEvent("active-keep"))
        assertNull(eventRepository.getEvent("trash-del-1"))
        assertNull(eventRepository.getEvent("trash-del-2"))
    }

    @Test
    fun lifecycle_retentionBoundary_purgesOnlyExpiredItemsAt30Days() = runTest(testDispatcher) {
        val now = timeProvider.nowInstant()

        // 31 days old (expired)
        val expiredEvent = Event(
            id = "expired-31",
            title = "31 Days in Trash",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = now.minus(Duration.ofDays(31)),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 9, 20)),
            createdAt = now,
            updatedAt = now
        )

        // Exactly 30 days old (at boundary - expired)
        val boundaryEvent = Event(
            id = "boundary-30",
            title = "30 Days in Trash",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = now.minus(Duration.ofDays(30)),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 9, 21)),
            createdAt = now,
            updatedAt = now
        )

        // 29 days old (retained)
        val retainedEvent = Event(
            id = "retained-29",
            title = "29 Days in Trash",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = now.minus(Duration.ofDays(29)),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 9, 22)),
            createdAt = now,
            updatedAt = now
        )

        eventRepository.saveEvent(expiredEvent)
        eventRepository.saveEvent(boundaryEvent)
        eventRepository.saveEvent(retainedEvent)

        val purgeResult = purgeExpiredTrashUseCase(Duration.ofDays(30))
        assertEquals(2, (purgeResult as com.example.offlinereminder.core.result.Result.Success).data)

        assertNull(eventRepository.getEvent("expired-31"))
        assertNull(eventRepository.getEvent("boundary-30"))
        assertNotNull(eventRepository.getEvent("retained-29"))
    }

    @Test
    fun lifecycle_trashedEvents_cannotCreateOrResurrectActiveNotifications() = runTest(testDispatcher) {
        // Active event with schedule
        val event = Event(
            id = "event-sched",
            title = "Important Flight",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(
                dateTime = timeProvider.nowLocalDateTime().plusDays(2),
                zoneId = ZoneId.of("UTC")
            ),
            notificationSchedules = listOf(
                NotificationSchedule(
                    id = "sched-flight",
                    offsetDuration = Duration.ofHours(2),
                    triggerType = NotificationTriggerType.BEFORE_START,
                    deliveryType = ReminderDeliveryType.ALARM
                )
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        // Schedule alarms while active
        val scheduledCount = scheduleNotificationsForEventUseCase(event)
        assertTrue(scheduledCount is com.example.offlinereminder.core.result.Result.Success)
        assertTrue((scheduledCount as com.example.offlinereminder.core.result.Result.Success).data > 0)
        assertTrue(testAlarmScheduler.scheduledAlarms.isNotEmpty())

        // Move to trash
        val trashedEvent = event.copy(lifecycleStatus = EventLifecycleStatus.TRASHED, deletedAt = timeProvider.nowInstant())
        eventRepository.saveEvent(trashedEvent)

        // Attempting to schedule notifications on a trashed event cancels existing and creates 0 new ones
        val trashedScheduleResult = scheduleNotificationsForEventUseCase(trashedEvent)
        assertEquals(0, (trashedScheduleResult as com.example.offlinereminder.core.result.Result.Success).data)
        assertTrue(testAlarmScheduler.scheduledAlarms.isEmpty())
        assertTrue(testAlarmScheduler.cancelledAlarms.isNotEmpty())
    }
}
