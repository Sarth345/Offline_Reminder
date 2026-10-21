package com.example.offlinereminder.domain.notification

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.repository.NotificationRepository
import com.example.offlinereminder.domain.usecase.CancelNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.DismissNotificationUseCase
import com.example.offlinereminder.domain.usecase.RebootRecoveryUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.SnoozeNotificationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationSchedulingTest {

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T10:00:00Z"),
        currentZoneId = utcZone
    )

    private val testAlarmScheduler = TestAlarmScheduler()
    private val inMemoryRepository = InMemoryNotificationRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var scheduleNotificationsUseCase: ScheduleNotificationsForEventUseCase
    private lateinit var cancelNotificationsUseCase: CancelNotificationsForEventUseCase
    private lateinit var snoozeNotificationUseCase: SnoozeNotificationUseCase
    private lateinit var dismissNotificationUseCase: DismissNotificationUseCase
    private lateinit var rebootRecoveryUseCase: RebootRecoveryUseCase

    @Before
    fun setUp() {
        inMemoryRepository.clear()
        testAlarmScheduler.clear()

        scheduleNotificationsUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = inMemoryRepository,
            alarmScheduler = testAlarmScheduler,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )

        cancelNotificationsUseCase = CancelNotificationsForEventUseCase(
            notificationRepository = inMemoryRepository,
            alarmScheduler = testAlarmScheduler
        )

        snoozeNotificationUseCase = SnoozeNotificationUseCase(
            notificationRepository = inMemoryRepository,
            alarmScheduler = testAlarmScheduler,
            timeProvider = timeProvider
        )

        dismissNotificationUseCase = DismissNotificationUseCase(
            notificationRepository = inMemoryRepository,
            alarmScheduler = testAlarmScheduler
        )

        rebootRecoveryUseCase = RebootRecoveryUseCase(
            notificationRepository = inMemoryRepository,
            alarmScheduler = testAlarmScheduler,
            timeProvider = timeProvider
        )
    }

    private fun createEvent(
        id: String = "event-1",
        startLdt: LocalDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
        recurrenceRule: RecurrenceRule? = null,
        schedules: List<NotificationSchedule> = listOf(NotificationSchedule.atStart()),
        lifecycleStatus: EventLifecycleStatus = EventLifecycleStatus.ACTIVE
    ): Event = Event(
        id = id,
        title = "Doctor Appointment",
        eventTypeId = "type-med",
        categoryId = "cat-health",
        priority = Priority.NORMAL,
        temporal = EventDateTime.DateTime(startLdt, utcZone),
        recurrenceRule = recurrenceRule,
        notificationSchedules = schedules,
        lifecycleStatus = lifecycleStatus,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun `1 one-time event schedules single alarm for future occurrence`() = runBlocking {
        val event = createEvent(startLdt = LocalDateTime.of(2026, 10, 25, 14, 0))

        val result = scheduleNotificationsUseCase(event)
        assertTrue(result.isSuccess)
        assertEquals(1, (result as Result.Success).data)

        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
        val alarm = testAlarmScheduler.scheduledAlarms[0]
        assertEquals(NotificationConstants.ACTION_ALARM, alarm.intentAction)
        assertEquals(event.id, alarm.extras[NotificationConstants.EXTRA_EVENT_ID])

        val savedInstances = inMemoryRepository.getInstancesForEvent(event.id)
        assertEquals(1, savedInstances.size)
        assertEquals(NotificationInstanceStatus.SCHEDULED, savedInstances[0].status)
    }

    @Test
    fun `2 recurring event schedules alarms only within bounded horizon (14 days)`() = runBlocking {
        // Daily event starting today (Oct 20) at 15:00
        val event = createEvent(
            startLdt = LocalDateTime.of(2026, 10, 20, 15, 0),
            recurrenceRule = RecurrenceRule.daily()
        )

        val result = scheduleNotificationsUseCase(event, horizonDays = 14)
        assertTrue(result.isSuccess)

        // Occurrences from Oct 20 to Nov 3 (15 days: Oct 20..Nov 3 inclusive)
        val scheduledCount = (result as Result.Success).data
        assertTrue(scheduledCount in 14..16)
        assertEquals(scheduledCount, testAlarmScheduler.scheduledAlarms.size)
        assertEquals(scheduledCount, inMemoryRepository.getInstancesForEvent(event.id).size)
    }

    @Test
    fun `3 scheduling is idempotent and prevents duplicate alarms`() = runBlocking {
        val event = createEvent(startLdt = LocalDateTime.of(2026, 10, 25, 14, 0))

        scheduleNotificationsUseCase(event)
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)

        // Run second time
        val secondResult = scheduleNotificationsUseCase(event)
        assertTrue(secondResult.isSuccess)
        assertEquals(0, (secondResult as Result.Success).data) // 0 new alarms

        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
        assertEquals(1, inMemoryRepository.getInstancesForEvent(event.id).size)
    }

    @Test
    fun `4 rescheduling an occurrence cancels old alarm and registers new alarm`() = runBlocking {
        val originalDate = LocalDateTime.of(2026, 10, 25, 14, 0)
        val event = createEvent(startLdt = originalDate)
        scheduleNotificationsUseCase(event)
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)

        // Reschedule to Oct 26 10:00
        val newDate = LocalDateTime.of(2026, 10, 26, 10, 0)
        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = originalDate,
            originalZoneId = utcZone,
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = newDate
        )

        scheduleNotificationsUseCase(event, exceptions = listOf(exception))

        // Old alarm cancelled or updated to new trigger time
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
        val updatedAlarm = testAlarmScheduler.scheduledAlarms[0]
        val expectedEpoch = newDate.atZone(utcZone).toInstant().toEpochMilli()
        assertEquals(expectedEpoch, updatedAlarm.triggerEpochMillis)
    }

    @Test
    fun `5 trashing an event cancels all active alarms`() = runBlocking {
        val event = createEvent(startLdt = LocalDateTime.of(2026, 10, 25, 14, 0))
        scheduleNotificationsUseCase(event)
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)

        val trashedEvent = event.copy(lifecycleStatus = EventLifecycleStatus.TRASHED)
        scheduleNotificationsUseCase(trashedEvent)

        assertEquals(0, testAlarmScheduler.scheduledAlarms.size)
        assertEquals(1, testAlarmScheduler.cancelledAlarms.size)

        val instances = inMemoryRepository.getInstancesForEvent(event.id)
        assertEquals(NotificationInstanceStatus.CANCELLED, instances[0].status)
    }

    @Test
    fun `6 skipping an occurrence reconciles and cancels its alarm`() = runBlocking {
        val originalDate = LocalDateTime.of(2026, 10, 22, 14, 0)
        val event = createEvent(
            startLdt = originalDate,
            recurrenceRule = RecurrenceRule.daily()
        )
        scheduleNotificationsUseCase(event, horizonDays = 3)

        val initialAlarms = testAlarmScheduler.scheduledAlarms.size
        assertTrue(initialAlarms > 0)

        // Skip Oct 22 occurrence
        val skipException = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = originalDate,
            originalZoneId = utcZone,
            status = OccurrenceStatus.SKIPPED
        )

        scheduleNotificationsUseCase(event, exceptions = listOf(skipException), horizonDays = 3)

        // Oct 22 alarm should be cancelled
        assertFalse(testAlarmScheduler.scheduledAlarms.any {
            it.extras[NotificationConstants.EXTRA_OCCURRENCE_START] == originalDate.toString()
        })
    }

    @Test
    fun `7 snooze updates instance state and schedules new alarm`() = runBlocking {
        val event = createEvent(startLdt = LocalDateTime.of(2026, 10, 25, 14, 0))
        scheduleNotificationsUseCase(event)

        val instance = inMemoryRepository.getInstancesForEvent(event.id).first()
        val snoozeDuration = Duration.ofMinutes(15)

        val snoozeResult = snoozeNotificationUseCase(instance.id, snoozeDuration)
        assertTrue(snoozeResult.isSuccess)

        val expectedSnoozeUntil = timeProvider.nowInstant().plus(snoozeDuration)
        val updatedInstance = inMemoryRepository.getInstanceById(instance.id)

        assertNotNull(updatedInstance)
        assertEquals(NotificationInstanceStatus.SNOOZED, updatedInstance?.status)
        assertEquals(1, updatedInstance?.snoozeCount)
        assertEquals(expectedSnoozeUntil, updatedInstance?.snoozeUntil)
        assertEquals(expectedSnoozeUntil.toEpochMilli(), updatedInstance?.triggerEpochMillis)

        // AlarmScheduler has the snoozed alarm
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
        assertEquals(expectedSnoozeUntil.toEpochMilli(), testAlarmScheduler.scheduledAlarms[0].triggerEpochMillis)
    }

    @Test
    fun `8 stale instances in the past are not scheduled`() = runBlocking {
        // Event at 08:00 AM today (current test time is 10:00 AM today)
        val pastEvent = createEvent(startLdt = LocalDateTime.of(2026, 10, 20, 8, 0))

        val result = scheduleNotificationsUseCase(pastEvent)
        assertTrue(result.isSuccess)
        assertEquals(0, (result as Result.Success).data)
        assertEquals(0, testAlarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun `9 reboot recovery re-registers future alarms and cancels expired stale ones without false delivery`() = runBlocking {
        val futureInstance = NotificationInstance(
            id = "inst-future",
            eventId = "event-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            occurrenceOriginalZoneId = utcZone,
            triggerEpochMillis = timeProvider.nowInstant().plus(Duration.ofDays(5)).toEpochMilli(),
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 1001
        )

        val staleInstance = NotificationInstance(
            id = "inst-stale",
            eventId = "event-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 19, 14, 0),
            occurrenceOriginalZoneId = utcZone,
            triggerEpochMillis = timeProvider.nowInstant().minus(Duration.ofHours(2)).toEpochMilli(),
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 1002
        )

        inMemoryRepository.saveInstance(futureInstance)
        inMemoryRepository.saveInstance(staleInstance)

        // Simulate reboot: AlarmManager is empty
        testAlarmScheduler.clear()

        val recoveryResult = rebootRecoveryUseCase()
        assertTrue(recoveryResult.isSuccess)
        assertEquals(1, (recoveryResult as Result.Success).data)

        // 1. Reboot before trigger -> rescheduled
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
        assertEquals(futureInstance.requestCode, testAlarmScheduler.scheduledAlarms[0].requestCode)

        // 2. Reboot after trigger -> correct missed/expired behavior (marked CANCELLED)
        val updatedStale = inMemoryRepository.getInstanceById("inst-stale")
        assertEquals(NotificationInstanceStatus.CANCELLED, updatedStale?.status)

        // 3. No false DELIVERED state
        assertFalse(updatedStale?.status == NotificationInstanceStatus.DELIVERED)

        // 4. No duplicate alarm on repeated reboot recovery
        val secondReboot = rebootRecoveryUseCase()
        assertTrue(secondReboot.isSuccess)
        assertEquals(1, (secondReboot as Result.Success).data)
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun `13 reboot recovery with snoozed future instance re-registers alarm and retains snooze state`() = runBlocking {
        val snoozeUntil = timeProvider.nowInstant().plus(Duration.ofMinutes(20))
        val snoozedInstance = NotificationInstance(
            id = "inst-snoozed",
            eventId = "event-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 20, 9, 0),
            occurrenceOriginalZoneId = utcZone,
            triggerEpochMillis = snoozeUntil.toEpochMilli(),
            status = NotificationInstanceStatus.SNOOZED,
            snoozeCount = 1,
            snoozeUntil = snoozeUntil,
            requestCode = 2001
        )
        inMemoryRepository.saveInstance(snoozedInstance)
        testAlarmScheduler.clear()

        val recovery = rebootRecoveryUseCase()
        assertTrue(recovery.isSuccess)
        assertEquals(1, (recovery as Result.Success).data)

        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
        assertEquals(snoozedInstance.requestCode, testAlarmScheduler.scheduledAlarms[0].requestCode)

        val retrieved = inMemoryRepository.getInstanceById("inst-snoozed")
        assertEquals(NotificationInstanceStatus.SNOOZED, retrieved?.status)
    }

    @Test
    fun `14 reboot after trigger for snoozed instance cancels it without false delivery`() = runBlocking {
        val pastSnooze = timeProvider.nowInstant().minus(Duration.ofMinutes(10))
        val expiredSnooze = NotificationInstance(
            id = "inst-expired-snooze",
            eventId = "event-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 20, 8, 0),
            occurrenceOriginalZoneId = utcZone,
            triggerEpochMillis = pastSnooze.toEpochMilli(),
            status = NotificationInstanceStatus.SNOOZED,
            snoozeCount = 1,
            snoozeUntil = pastSnooze,
            requestCode = 2002
        )
        inMemoryRepository.saveInstance(expiredSnooze)
        testAlarmScheduler.clear()

        val recovery = rebootRecoveryUseCase()
        assertTrue(recovery.isSuccess)
        assertEquals(0, (recovery as Result.Success).data)

        assertEquals(0, testAlarmScheduler.scheduledAlarms.size)
        val retrieved = inMemoryRepository.getInstanceById("inst-expired-snooze")
        assertEquals(NotificationInstanceStatus.CANCELLED, retrieved?.status)
        assertFalse(retrieved?.status == NotificationInstanceStatus.DELIVERED)
    }

    @Test
    fun `10 exact alarm capability handling when exact alarms disallowed`() = runBlocking {
        testAlarmScheduler.canScheduleExact = false

        val event = createEvent(startLdt = LocalDateTime.of(2026, 10, 25, 14, 0))
        val result = scheduleNotificationsUseCase(event)

        assertTrue(result.isSuccess)
        assertEquals(1, (result as Result.Success).data)
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun `11 dismiss notification cancels alarm and updates status to DISMISSED`() = runBlocking {
        val event = createEvent(startLdt = LocalDateTime.of(2026, 10, 25, 14, 0))
        scheduleNotificationsUseCase(event)

        val instance = inMemoryRepository.getInstancesForEvent(event.id).first()
        val dismissResult = dismissNotificationUseCase(instance.id)

        assertTrue(dismissResult.isSuccess)
        val updated = inMemoryRepository.getInstanceById(instance.id)
        assertEquals(NotificationInstanceStatus.DISMISSED, updated?.status)
        assertEquals(0, testAlarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun `12 multiple notification schedules per event schedule multiple distinct alarms`() = runBlocking {
        val event = createEvent(
            startLdt = LocalDateTime.of(2026, 10, 25, 14, 0),
            schedules = listOf(
                NotificationSchedule.atStart(),
                NotificationSchedule.beforeStart(Duration.ofHours(1)),
                NotificationSchedule.beforeStart(Duration.ofDays(1))
            )
        )

        val result = scheduleNotificationsUseCase(event)
        assertTrue(result.isSuccess)
        assertEquals(3, (result as Result.Success).data)

        assertEquals(3, testAlarmScheduler.scheduledAlarms.size)
        val instances = inMemoryRepository.getInstancesForEvent(event.id)
        assertEquals(3, instances.size)
    }

    @Test
    fun `13 quiet hours and silent flag are propagated via settings repository`() = runBlocking {
        val fakeSettingsRepo = object : com.example.offlinereminder.domain.repository.SettingsRepository {
            override fun getSettingsFlow(): Flow<com.example.offlinereminder.domain.model.AppSettings> =
                kotlinx.coroutines.flow.flow { emit(getSettings()) }
            override suspend fun getSettings(): com.example.offlinereminder.domain.model.AppSettings =
                com.example.offlinereminder.domain.model.AppSettings(
                    quietHours = com.example.offlinereminder.domain.model.QuietHours(
                        enabled = true,
                        startLocalTime = java.time.LocalTime.of(22, 0),
                        endLocalTime = java.time.LocalTime.of(7, 0),
                        policy = com.example.offlinereminder.domain.model.QuietHoursPolicy.SILENT,
                        allowCritical = false
                    )
                )
            override suspend fun updateSettings(transform: (com.example.offlinereminder.domain.model.AppSettings) -> com.example.offlinereminder.domain.model.AppSettings) =
                Result.Success(getSettings())
            override suspend fun resetSettings() = Result.Success(getSettings())
        }

        val useCaseWithSettings = ScheduleNotificationsForEventUseCase(
            notificationRepository = inMemoryRepository,
            alarmScheduler = testAlarmScheduler,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider,
            settingsRepository = fakeSettingsRepo
        )

        // Event scheduled at 23:00 (during quiet hours)
        val event = createEvent(startLdt = LocalDateTime.of(2026, 10, 25, 23, 0))
        val result = useCaseWithSettings(event)

        assertTrue(result.isSuccess)
        assertEquals(1, testAlarmScheduler.scheduledAlarms.size)
        val alarm = testAlarmScheduler.scheduledAlarms.first()
        assertEquals("true", alarm.extras[NotificationConstants.EXTRA_IS_SILENT])
    }
}

/**
 * In-memory test implementation of [NotificationRepository].
 */
class InMemoryNotificationRepository : NotificationRepository {

    private val instances = mutableMapOf<String, NotificationInstance>()

    fun clear() {
        instances.clear()
    }

    override fun getInstancesForEventFlow(eventId: String): Flow<List<NotificationInstance>> =
        flowOf(instances.values.filter { it.eventId == eventId })

    override fun getAllScheduledFlow(): Flow<List<NotificationInstance>> =
        flowOf(instances.values.filter { it.status == NotificationInstanceStatus.SCHEDULED })

    override suspend fun getInstanceById(id: String): NotificationInstance? =
        instances[id]

    override suspend fun getInstancesForEvent(eventId: String): List<NotificationInstance> =
        instances.values.filter { it.eventId == eventId }

    override suspend fun getAllActiveScheduled(): List<NotificationInstance> =
        instances.values.filter {
            it.status == NotificationInstanceStatus.SCHEDULED ||
            it.status == NotificationInstanceStatus.SNOOZED ||
            it.status == NotificationInstanceStatus.RINGING
        }

    override suspend fun saveInstance(instance: NotificationInstance): Result<Unit> = Result.runCatching {
        instances[instance.id] = instance
    }

    override suspend fun saveInstances(instances: List<NotificationInstance>): Result<Unit> = Result.runCatching {
        instances.forEach { this.instances[it.id] = it }
    }

    override suspend fun updateStatus(id: String, status: NotificationInstanceStatus): Result<Unit> = Result.runCatching {
        instances[id]?.let {
            instances[id] = it.copy(status = status)
        }
    }

    override suspend fun snooze(id: String, snoozeUntil: Instant, newTriggerMillis: Long): Result<Unit> = Result.runCatching {
        instances[id]?.let {
            instances[id] = it.copy(
                status = NotificationInstanceStatus.SNOOZED,
                snoozeCount = it.snoozeCount + 1,
                snoozeUntil = snoozeUntil,
                triggerEpochMillis = newTriggerMillis
            )
        }
    }

    override suspend fun deleteById(id: String): Result<Unit> = Result.runCatching {
        instances.remove(id)
    }

    override suspend fun deleteByEventId(eventId: String): Result<Unit> = Result.runCatching {
        instances.entries.removeIf { it.value.eventId == eventId }
    }
}
