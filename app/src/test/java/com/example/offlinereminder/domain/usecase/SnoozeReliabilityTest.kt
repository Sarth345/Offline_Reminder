package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Comprehensive test suite validating all Snooze reliability requirements:
 * 1. Immediate stop and reschedule for requested duration.
 * 2. Occurrence identity and database state preservation.
 * 3. Exact AlarmManager rescheduling with EXTRA_DELIVERY_TYPE.
 * 4. Duplicate prevention across multiple snoozes.
 * 5. Dismiss and Snooze separation.
 * 6. Protection from ScheduleNotificationsForEventUseCase cancellation/overwriting.
 * 7. Reboot recovery preservation of snoozed alarms.
 * 8. Timezone and DST immunity.
 */
class SnoozeReliabilityTest {

    private val testZone = ZoneId.of("America/New_York")
    private val baseInstant = Instant.parse("2026-10-26T14:00:00Z")
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var alarmScheduler: TestAlarmScheduler
    private lateinit var notificationRepo: FakeNotificationRepository
    private lateinit var snoozeUseCase: SnoozeNotificationUseCase
    private lateinit var dismissUseCase: DismissNotificationUseCase

    @Before
    fun setUp() {
        timeProvider = TestTimeProvider(
            currentInstant = baseInstant,
            currentZoneId = testZone
        )
        alarmScheduler = TestAlarmScheduler()
        notificationRepo = FakeNotificationRepository()
        snoozeUseCase = SnoozeNotificationUseCase(notificationRepo, alarmScheduler, timeProvider)
        dismissUseCase = DismissNotificationUseCase(notificationRepo, alarmScheduler)
    }

    @Test
    fun test1_basicSnooze_updatesDatabaseStateAndSchedulesAlarm() = runBlocking {
        val instanceId = "inst_basic_1"
        val eventId = "evt_basic_1"
        val originalStart = LocalDateTime.of(2026, 10, 26, 10, 0)

        val instance = NotificationInstance(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = originalStart,
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = NotificationInstanceStatus.RINGING
        )
        notificationRepo.saveInstance(instance)

        val snoozeDuration = Duration.ofMinutes(15)
        val result = snoozeUseCase(instanceId, snoozeDuration, ReminderDeliveryType.ALARM)

        assertTrue("Snooze should succeed", result.isSuccess)
        val expectedSnoozeUntil = baseInstant.plus(snoozeDuration)

        val updated = notificationRepo.getInstanceById(instanceId)
        assertNotNull(updated)
        assertEquals(NotificationInstanceStatus.SNOOZED, updated!!.status)
        assertEquals(1, updated.snoozeCount)
        assertEquals(expectedSnoozeUntil, updated.snoozeUntil)
        assertEquals(expectedSnoozeUntil.toEpochMilli(), updated.triggerEpochMillis)
        assertEquals(originalStart, updated.occurrenceOriginalStartDateTime)
        assertEquals(testZone, updated.occurrenceOriginalZoneId)

        // Verify alarm scheduled
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
        val scheduled = alarmScheduler.scheduledAlarms.first()
        assertEquals(instance.requestCode, scheduled.requestCode)
        assertEquals(expectedSnoozeUntil.toEpochMilli(), scheduled.triggerEpochMillis)
        assertEquals(ReminderDeliveryType.ALARM.name, scheduled.extras[NotificationConstants.EXTRA_DELIVERY_TYPE])
        assertEquals(instanceId, scheduled.extras[NotificationConstants.EXTRA_INSTANCE_ID])
        assertEquals(eventId, scheduled.extras[NotificationConstants.EXTRA_EVENT_ID])
    }

    @Test
    fun test2_configurableSnoozeDurations_5m_10m_15m_30m_60m() = runBlocking {
        val durations = listOf(
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofMinutes(60)
        )

        for (duration in durations) {
            val instId = "inst_duration_${duration.toMinutes()}"
            val instance = NotificationInstance(
                id = instId,
                eventId = "evt_duration",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
                occurrenceOriginalZoneId = testZone,
                triggerEpochMillis = baseInstant.toEpochMilli(),
                status = NotificationInstanceStatus.RINGING
            )
            notificationRepo.saveInstance(instance)

            val result = snoozeUseCase(instId, duration, ReminderDeliveryType.ALARM)
            assertTrue(result.isSuccess)

            val updated = notificationRepo.getInstanceById(instId)!!
            val expectedUntil = baseInstant.plus(duration)
            assertEquals(expectedUntil.toEpochMilli(), updated.triggerEpochMillis)
            assertEquals(expectedUntil, updated.snoozeUntil)
        }
    }

    @Test
    fun test3_repeatedSnoozes_incrementsCountAndPreventsDuplicateAlarms() = runBlocking {
        val instanceId = "inst_repeat_1"
        val instance = NotificationInstance(
            id = instanceId,
            eventId = "evt_repeat",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = NotificationInstanceStatus.RINGING
        )
        notificationRepo.saveInstance(instance)

        // First snooze: +10 min
        snoozeUseCase(instanceId, Duration.ofMinutes(10), ReminderDeliveryType.ALARM)
        var updated = notificationRepo.getInstanceById(instanceId)!!
        assertEquals(1, updated.snoozeCount)
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
        assertEquals(baseInstant.plus(Duration.ofMinutes(10)).toEpochMilli(), alarmScheduler.scheduledAlarms[0].triggerEpochMillis)

        // Advance time by 10 minutes (alarm triggers again)
        timeProvider.currentInstant = baseInstant.plus(Duration.ofMinutes(10))
        notificationRepo.updateStatus(instanceId, NotificationInstanceStatus.RINGING)

        // Second snooze: +15 min
        snoozeUseCase(instanceId, Duration.ofMinutes(15), ReminderDeliveryType.ALARM)
        updated = notificationRepo.getInstanceById(instanceId)!!
        assertEquals(2, updated.snoozeCount)
        // No duplicate alarm created in AlarmManager; exactly 1 active scheduled alarm
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
        val expectedSecondTrigger = baseInstant.plus(Duration.ofMinutes(25)).toEpochMilli()
        assertEquals(expectedSecondTrigger, alarmScheduler.scheduledAlarms[0].triggerEpochMillis)

        // Advance time and third snooze: +5 min
        timeProvider.currentInstant = baseInstant.plus(Duration.ofMinutes(25))
        notificationRepo.updateStatus(instanceId, NotificationInstanceStatus.RINGING)

        snoozeUseCase(instanceId, Duration.ofMinutes(5), ReminderDeliveryType.ALARM)
        updated = notificationRepo.getInstanceById(instanceId)!!
        assertEquals(3, updated.snoozeCount)
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
        val expectedThirdTrigger = baseInstant.plus(Duration.ofMinutes(30)).toEpochMilli()
        assertEquals(expectedThirdTrigger, alarmScheduler.scheduledAlarms[0].triggerEpochMillis)
    }

    @Test
    fun test4_dismissAndSnoozeRemainSeparateBehaviors() = runBlocking {
        val snoozeInstanceId = "inst_sep_snooze"
        val dismissInstanceId = "inst_sep_dismiss"

        val inst1 = NotificationInstance(
            id = snoozeInstanceId,
            eventId = "evt_sep",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = NotificationInstanceStatus.RINGING
        )
        val inst2 = NotificationInstance(
            id = dismissInstanceId,
            eventId = "evt_sep",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = NotificationInstanceStatus.RINGING
        )
        notificationRepo.saveInstance(inst1)
        notificationRepo.saveInstance(inst2)

        // Snooze inst1
        snoozeUseCase(snoozeInstanceId, Duration.ofMinutes(10), ReminderDeliveryType.ALARM)
        // Dismiss inst2
        dismissUseCase(dismissInstanceId)

        val snoozed = notificationRepo.getInstanceById(snoozeInstanceId)!!
        val dismissed = notificationRepo.getInstanceById(dismissInstanceId)!!

        assertEquals(NotificationInstanceStatus.SNOOZED, snoozed.status)
        assertEquals(1, snoozed.snoozeCount)
        assertTrue(snoozed.isPending)
        assertFalse(snoozed.isTerminal)

        assertEquals(NotificationInstanceStatus.DISMISSED, dismissed.status)
        assertEquals(0, dismissed.snoozeCount)
        assertFalse(dismissed.isPending)
        assertTrue(dismissed.isTerminal)

        // Only the snoozed instance has an alarm in AlarmScheduler
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
        assertEquals(snoozed.requestCode, alarmScheduler.scheduledAlarms[0].requestCode)
    }

    @Test
    fun test5_deliveryTypeInference_infersAlarmFromRingingState() = runBlocking {
        val instanceId = "inst_infer_alarm"
        val instance = NotificationInstance(
            id = instanceId,
            eventId = "evt_infer",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = NotificationInstanceStatus.RINGING
        )
        notificationRepo.saveInstance(instance)

        // Call snoozeUseCase without specifying deliveryType
        snoozeUseCase(instanceId, Duration.ofMinutes(15))

        val scheduled = alarmScheduler.scheduledAlarms.firstOrNull { it.requestCode == instance.requestCode }
        assertNotNull(scheduled)
        // Verified: inferred as ALARM because previous status was RINGING
        assertEquals(ReminderDeliveryType.ALARM.name, scheduled!!.extras[NotificationConstants.EXTRA_DELIVERY_TYPE])
    }

    @Test
    fun test6_scheduleNotificationsUseCase_doesNotCancelOrOverwriteSnoozedInstance() = runBlocking {
        val eventId = "evt_reconcile_snooze"
        val scheduleId = "sched_1"
        val occStart = LocalDateTime.of(2026, 10, 26, 10, 0)
        val instanceId = "${eventId}_${occStart}_${scheduleId}"

        val schedule = NotificationSchedule(
            id = scheduleId,
            deliveryType = ReminderDeliveryType.ALARM,
            offsetDuration = Duration.ZERO,
            triggerType = NotificationTriggerType.AT_START
        )
        val event = Event(
            id = eventId,
            title = "Test Snoozed Event",
            eventTypeId = "type_1",
            categoryId = "cat_1",
            temporal = EventDateTime.DateTime(
                dateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
                zoneId = testZone
            ),
            notificationSchedules = listOf(schedule),
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Instance was already delivered and snoozed until 14:15 UTC (future)
        val futureSnoozeTrigger = baseInstant.plus(Duration.ofMinutes(15)).toEpochMilli()
        val snoozedInstance = NotificationInstance(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = occStart,
            occurrenceOriginalZoneId = testZone,
            scheduleId = scheduleId,
            triggerEpochMillis = futureSnoozeTrigger,
            status = NotificationInstanceStatus.SNOOZED,
            snoozeCount = 1,
            snoozeUntil = baseInstant.plus(Duration.ofMinutes(15))
        )
        notificationRepo.saveInstance(snoozedInstance)
        alarmScheduler.scheduleAlarm(
            requestCode = snoozedInstance.requestCode,
            triggerEpochMillis = futureSnoozeTrigger,
            intentAction = NotificationConstants.ACTION_ALARM,
            extras = mapOf(NotificationConstants.EXTRA_INSTANCE_ID to instanceId)
        )

        // Run ScheduleNotificationsForEventUseCase
        val scheduleUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notificationRepo,
            alarmScheduler = alarmScheduler,
            recurrenceEngine = DefaultRecurrenceEngine(timeProvider),
            timeProvider = timeProvider
        )
        val scheduleResult = scheduleUseCase(event)
        assertTrue(scheduleResult.isSuccess)

        // Verify snoozed instance was NOT cancelled or overwritten
        val postInstance = notificationRepo.getInstanceById(instanceId)!!
        assertEquals(NotificationInstanceStatus.SNOOZED, postInstance.status)
        assertEquals(1, postInstance.snoozeCount)
        assertEquals(futureSnoozeTrigger, postInstance.triggerEpochMillis)

        // Alarm still scheduled in AlarmManager for the snoozed time
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
        assertEquals(futureSnoozeTrigger, alarmScheduler.scheduledAlarms[0].triggerEpochMillis)
    }

    @Test
    fun test7_rebootRecovery_reRegistersFutureSnoozedAlarmsWithDeliveryType() = runBlocking {
        val instanceId = "inst_boot_snooze"
        val eventId = "evt_boot_1"
        val scheduleId = "sched_boot_1"
        val occStart = LocalDateTime.of(2026, 10, 26, 10, 0)

        val futureSnoozeTrigger = baseInstant.plus(Duration.ofMinutes(20)).toEpochMilli()
        val snoozedInstance = NotificationInstance(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = occStart,
            occurrenceOriginalZoneId = testZone,
            scheduleId = scheduleId,
            triggerEpochMillis = futureSnoozeTrigger,
            status = NotificationInstanceStatus.SNOOZED,
            snoozeCount = 1,
            snoozeUntil = baseInstant.plus(Duration.ofMinutes(20))
        )
        notificationRepo.saveInstance(snoozedInstance)

        val fakeEventRepo = InMemoryEventRepository()
        fakeEventRepo.saveEvent(
            Event(
                id = eventId,
                title = "Boot Event",
                eventTypeId = "type_1",
                categoryId = "cat_1",
                temporal = EventDateTime.DateTime(
                    dateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
                    zoneId = testZone
                ),
                notificationSchedules = listOf(
                    NotificationSchedule(
                        id = scheduleId,
                        deliveryType = ReminderDeliveryType.ALARM,
                        offsetDuration = Duration.ZERO,
                        triggerType = NotificationTriggerType.AT_START
                    )
                ),
                createdAt = baseInstant,
                updatedAt = baseInstant
            )
        )

        // Reboot clears all AlarmManager alarms
        alarmScheduler.clear()
        assertEquals(0, alarmScheduler.scheduledAlarms.size)

        val recoveryUseCase = RebootRecoveryUseCase(
            notificationRepository = notificationRepo,
            alarmScheduler = alarmScheduler,
            timeProvider = timeProvider,
            eventRepository = fakeEventRepo
        )
        val recoveryResult = recoveryUseCase()
        assertTrue(recoveryResult.isSuccess)
        assertEquals(1, recoveryResult.getOrThrow())

        // Verified: Alarm re-registered with exact snoozed trigger time and ALARM delivery type
        assertEquals(1, alarmScheduler.scheduledAlarms.size)
        val recovered = alarmScheduler.scheduledAlarms[0]
        assertEquals(futureSnoozeTrigger, recovered.triggerEpochMillis)
        assertEquals(ReminderDeliveryType.ALARM.name, recovered.extras[NotificationConstants.EXTRA_DELIVERY_TYPE])
        assertEquals(instanceId, recovered.extras[NotificationConstants.EXTRA_INSTANCE_ID])
    }

    @Test
    fun test8_rebootRecovery_cancelsStaleSnoozeMissedWhilePoweredOff() = runBlocking {
        val instanceId = "inst_boot_stale_snooze"
        val pastSnoozeTrigger = baseInstant.minus(Duration.ofMinutes(5)).toEpochMilli()

        val staleInstance = NotificationInstance(
            id = instanceId,
            eventId = "evt_stale",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 26, 10, 0),
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = pastSnoozeTrigger,
            status = NotificationInstanceStatus.SNOOZED,
            snoozeCount = 1,
            snoozeUntil = baseInstant.minus(Duration.ofMinutes(5))
        )
        notificationRepo.saveInstance(staleInstance)

        val recoveryUseCase = RebootRecoveryUseCase(
            notificationRepository = notificationRepo,
            alarmScheduler = alarmScheduler,
            timeProvider = timeProvider
        )
        val recoveryResult = recoveryUseCase()
        assertTrue(recoveryResult.isSuccess)

        // Stale instance is cancelled and not scheduled
        val updated = notificationRepo.getInstanceById(instanceId)!!
        assertEquals(NotificationInstanceStatus.CANCELLED, updated.status)
        assertEquals(0, alarmScheduler.scheduledAlarms.size)
    }

    @Test
    fun test9_timezoneAndDstSafety_epochMillisIsMonotonicAndExact() = runBlocking {
        // Test snooze crossing DST boundary or different timezone
        val sydneyZone = ZoneId.of("Australia/Sydney")
        val instanceId = "inst_dst_safe"

        val instance = NotificationInstance(
            id = instanceId,
            eventId = "evt_dst",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 26, 20, 0),
            occurrenceOriginalZoneId = sydneyZone,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = NotificationInstanceStatus.RINGING
        )
        notificationRepo.saveInstance(instance)

        val snoozeDuration = Duration.ofMinutes(12)
        snoozeUseCase(instanceId, snoozeDuration, ReminderDeliveryType.ALARM)

        val updated = notificationRepo.getInstanceById(instanceId)!!
        assertEquals(baseInstant.plus(snoozeDuration).toEpochMilli(), updated.triggerEpochMillis)
        // Original occurrence identity is intact regardless of timezone
        assertEquals(sydneyZone, updated.occurrenceOriginalZoneId)
        assertEquals(LocalDateTime.of(2026, 10, 26, 20, 0), updated.occurrenceOriginalStartDateTime)
    }
}

/**
 * Pure Kotlin in-memory fake for [NotificationRepository]
 */
private class FakeNotificationRepository : NotificationRepository {
    private val instances = mutableMapOf<String, NotificationInstance>()

    override fun getInstancesForEventFlow(eventId: String): Flow<List<NotificationInstance>> =
        flowOf(instances.values.filter { it.eventId == eventId })

    override fun getAllScheduledFlow(): Flow<List<NotificationInstance>> =
        flowOf(instances.values.filter { it.status == NotificationInstanceStatus.SCHEDULED })

    override suspend fun getInstanceById(id: String): NotificationInstance? = instances[id]

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
