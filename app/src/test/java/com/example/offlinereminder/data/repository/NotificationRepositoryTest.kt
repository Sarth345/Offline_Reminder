package com.example.offlinereminder.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.NotificationTriggerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineNotificationRepository
    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = OfflineNotificationRepository(database.notificationInstanceDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedEventAndSchedule(eventId: String = "evt-1", scheduleId: String = "sched-1") {
        database.categoryDao().insert(
            CategoryEntity(id = "cat-work", name = "Work", sortOrder = 0)
        )
        database.eventTypeDao().insert(
            EventTypeEntity(
                id = "type-task",
                name = "Task",
                key = "work_task",
                categoryId = "cat-work",
                createdAt = testInstant
            )
        )
        database.eventDao().insert(
            EventEntity(
                id = eventId,
                title = "Quarterly Review",
                eventTypeId = "type-task",
                categoryId = "cat-work",
                temporalType = "DATE_TIME",
                startDate = LocalDate.of(2026, 10, 25),
                startTime = LocalTime.of(14, 0),
                zoneId = "America/New_York",
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )
        database.notificationScheduleDao().insert(
            NotificationScheduleEntity(
                id = scheduleId,
                eventId = eventId,
                offsetDurationMillis = 0,
                triggerType = NotificationTriggerType.AT_START
            )
        )
    }

    @Test
    fun `save and retrieve NotificationInstance preserves occurrence identity and zone`() = runTest {
        seedEventAndSchedule("evt-1", "sched-1")

        val occurrenceTime = LocalDateTime.of(2026, 10, 25, 14, 0)
        val zoneId = ZoneId.of("America/New_York")
        val instance = NotificationInstance(
            id = "evt-1_2026-10-25T14:00_America/New_York_sched-1",
            eventId = "evt-1",
            occurrenceOriginalStartDateTime = occurrenceTime,
            occurrenceOriginalZoneId = zoneId,
            scheduleId = "sched-1",
            triggerEpochMillis = 1792951200000L,
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 54321
        )

        val saveResult = repository.saveInstance(instance)
        assertTrue(saveResult.isSuccess)

        val retrieved = repository.getInstanceById(instance.id)
        assertNotNull(retrieved)
        assertEquals(instance.id, retrieved?.id)
        assertEquals(instance.eventId, retrieved?.eventId)
        assertEquals(occurrenceTime, retrieved?.occurrenceOriginalStartDateTime)
        assertEquals(zoneId, retrieved?.occurrenceOriginalZoneId)
        assertEquals("sched-1", retrieved?.scheduleId)
        assertEquals(NotificationInstanceStatus.SCHEDULED, retrieved?.status)
        assertEquals(54321, retrieved?.requestCode)
    }

    @Test
    fun `getInstancesForEventFlow emits active list of instances`() = runTest {
        seedEventAndSchedule("evt-1", "sched-1")

        val instance = NotificationInstance(
            id = "inst-1",
            eventId = "evt-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            occurrenceOriginalZoneId = ZoneId.of("UTC"),
            scheduleId = "sched-1",
            triggerEpochMillis = 1792951200000L,
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 101
        )

        repository.saveInstance(instance)

        val list = repository.getInstancesForEventFlow("evt-1").first()
        assertEquals(1, list.size)
        assertEquals("inst-1", list[0].id)
    }

    @Test
    fun `snooze updates status, snoozeCount, snoozeUntil, and triggerEpochMillis`() = runTest {
        seedEventAndSchedule("evt-1", "sched-1")

        val instance = NotificationInstance(
            id = "inst-snooze",
            eventId = "evt-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            occurrenceOriginalZoneId = ZoneId.of("UTC"),
            scheduleId = "sched-1",
            triggerEpochMillis = 1792951200000L,
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 202
        )
        repository.saveInstance(instance)

        val snoozeUntil = Instant.parse("2026-10-25T14:15:00Z")
        val newTriggerMillis = snoozeUntil.toEpochMilli()

        val snoozeResult = repository.snooze("inst-snooze", snoozeUntil, newTriggerMillis)
        assertTrue(snoozeResult.isSuccess)

        val updated = repository.getInstanceById("inst-snooze")
        assertNotNull(updated)
        assertEquals(NotificationInstanceStatus.SNOOZED, updated?.status)
        assertEquals(1, updated?.snoozeCount)
        assertEquals(snoozeUntil, updated?.snoozeUntil)
        assertEquals(newTriggerMillis, updated?.triggerEpochMillis)
    }

    @Test
    fun `updateStatus transitions status to DISMISSED or DELIVERED`() = runTest {
        seedEventAndSchedule("evt-1", "sched-1")

        val instance = NotificationInstance(
            id = "inst-status",
            eventId = "evt-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            occurrenceOriginalZoneId = ZoneId.of("UTC"),
            scheduleId = "sched-1",
            triggerEpochMillis = 1792951200000L,
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 303
        )
        repository.saveInstance(instance)

        repository.updateStatus("inst-status", NotificationInstanceStatus.DELIVERED)
        assertEquals(NotificationInstanceStatus.DELIVERED, repository.getInstanceById("inst-status")?.status)

        repository.updateStatus("inst-status", NotificationInstanceStatus.DISMISSED)
        assertEquals(NotificationInstanceStatus.DISMISSED, repository.getInstanceById("inst-status")?.status)
    }

    @Test
    fun `deleteByEventId removes all instances for that event`() = runTest {
        seedEventAndSchedule("evt-1", "sched-1")

        val instance = NotificationInstance(
            id = "inst-del",
            eventId = "evt-1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            occurrenceOriginalZoneId = ZoneId.of("UTC"),
            scheduleId = "sched-1",
            triggerEpochMillis = 1792951200000L,
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 404
        )
        repository.saveInstance(instance)

        repository.deleteByEventId("evt-1")
        assertNull(repository.getInstanceById("inst-del"))
    }
}
