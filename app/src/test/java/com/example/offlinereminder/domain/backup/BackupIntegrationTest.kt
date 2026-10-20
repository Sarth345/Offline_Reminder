package com.example.offlinereminder.domain.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.repository.OccurrenceExceptionRepositoryImpl
import com.example.offlinereminder.data.repository.OfflineEventRepository
import com.example.offlinereminder.data.repository.OfflineNotificationRepository
import com.example.offlinereminder.data.repository.RoomBackupRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.ExportBackupUseCase
import com.example.offlinereminder.domain.usecase.RestoreBackupUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class BackupIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var eventRepo: OfflineEventRepository
    private lateinit var exceptionRepo: OccurrenceExceptionRepositoryImpl
    private lateinit var notifRepo: OfflineNotificationRepository
    private lateinit var alarmScheduler: TestAlarmScheduler
    private lateinit var backupRepo: RoomBackupRepository
    private lateinit var exportBackupUseCase: ExportBackupUseCase
    private lateinit var restoreBackupUseCase: RestoreBackupUseCase
    private lateinit var scheduleNotificationsUseCase: ScheduleNotificationsForEventUseCase

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")
    private val testZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = testInstant,
        currentZoneId = testZone
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        eventRepo = OfflineEventRepository(db, timeProvider)
        exceptionRepo = OccurrenceExceptionRepositoryImpl(db.occurrenceExceptionDao())
        notifRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        alarmScheduler = TestAlarmScheduler()

        scheduleNotificationsUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notifRepo,
            alarmScheduler = alarmScheduler,
            recurrenceEngine = DefaultRecurrenceEngine(timeProvider),
            timeProvider = timeProvider
        )

        backupRepo = RoomBackupRepository(db, timeProvider)
        exportBackupUseCase = ExportBackupUseCase(backupRepo)
        restoreBackupUseCase = RestoreBackupUseCase(
            backupRepository = backupRepo,
            eventRepository = eventRepo,
            occurrenceExceptionRepository = exceptionRepo,
            scheduleNotificationsForEventUseCase = scheduleNotificationsUseCase
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun endToEnd_exportAndRestore_reconcilesNotificationsAndPreservesDomainState() = runTest {
        // 1. Seed Categories and EventTypes
        db.categoryDao().insert(CategoryEntity("cat-health", "Health"))
        db.eventTypeDao().insert(EventTypeEntity("type-doc", "Doctor Appointment", "doc", "cat-health", createdAt = testInstant))

        // 2. Create an active event with notification schedule (in 2 days)
        val activeEvent = Event(
            id = "evt-doc-1",
            title = "Annual Health Checkup",
            eventTypeId = "type-doc",
            categoryId = "cat-health",
            priority = Priority.HIGH,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 11, 0), testZone),
            notificationSchedules = listOf(
                NotificationSchedule(
                    id = "sched-1",
                    offsetDuration = Duration.ofMinutes(30),
                    triggerType = NotificationTriggerType.BEFORE_START
                )
            ),
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        eventRepo.saveEvent(activeEvent)

        // 3. Export archive
        val exportStream = ByteArrayOutputStream()
        val exportResult = exportBackupUseCase(exportStream)
        assertTrue(exportResult.isSuccess)
        val exportMeta = exportResult.getOrThrow()
        assertEquals(1, exportMeta.totalEvents)

        // 4. Wipe DB completely and clear alarms
        db.eventDao().deleteAll()
        db.eventTypeDao().deleteAll()
        db.categoryDao().deleteAll()
        alarmScheduler.clear()
        assertEquals(0, eventRepo.getAllActiveEvents().first().size)
        assertEquals(0, alarmScheduler.scheduledAlarms.size)

        // 5. Restore from the exported stream
        val importStream = ByteArrayInputStream(exportStream.toByteArray())
        val restoreResult = restoreBackupUseCase(importStream, RestoreStrategy.REPLACE_ALL)
        assertTrue(restoreResult.isSuccess)

        // 6. Verify restored domain events
        val activeAfterRestore = eventRepo.getAllActiveEvents().first()
        assertEquals(1, activeAfterRestore.size)
        val restored = activeAfterRestore[0]
        assertEquals("evt-doc-1", restored.id)
        assertEquals("Annual Health Checkup", restored.title)
        assertEquals(1, restored.notificationSchedules.size)

        // 7. Verify notifications were automatically reconciled with AlarmScheduler
        assertTrue("Expected reconciled alarms after restore", alarmScheduler.scheduledAlarms.isNotEmpty())
        val alarmRecord = alarmScheduler.scheduledAlarms.first()
        val expectedTrigger = LocalDateTime.of(2026, 10, 22, 11, 0)
            .atZone(testZone)
            .toInstant()
            .minus(Duration.ofMinutes(30))
            .toEpochMilli()
        assertEquals(expectedTrigger, alarmRecord.triggerEpochMillis)
    }

    @Test
    fun endToEnd_restore_trashedEventsDoNotScheduleAlarms() = runTest {
        db.categoryDao().insert(CategoryEntity("cat-1", "General"))
        db.eventTypeDao().insert(EventTypeEntity("type-1", "Task", "task", "cat-1", createdAt = testInstant))

        val trashedEvent = Event(
            id = "evt-trashed",
            title = "Cancelled Task",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 9, 0), testZone),
            notificationSchedules = listOf(
                NotificationSchedule(id = "sched-trashed", offsetDuration = Duration.ofMinutes(15))
            ),
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        eventRepo.saveEvent(trashedEvent)

        val exportStream = ByteArrayOutputStream()
        exportBackupUseCase(exportStream).getOrThrow()

        // Clear
        db.eventDao().deleteAll()
        alarmScheduler.clear()

        // Restore
        val importStream = ByteArrayInputStream(exportStream.toByteArray())
        restoreBackupUseCase(importStream, RestoreStrategy.REPLACE_ALL).getOrThrow()

        // No active alarms should be scheduled for trashed events
        assertEquals(0, alarmScheduler.scheduledAlarms.size)
    }
}
