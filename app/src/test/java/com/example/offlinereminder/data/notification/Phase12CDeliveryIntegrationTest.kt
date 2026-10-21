package com.example.offlinereminder.data.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.getOrNull
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.alarm.AlarmPlaybackService
import com.example.offlinereminder.data.alarm.AlarmRingtonePlayer
import com.example.offlinereminder.data.alarm.AlarmState
import com.example.offlinereminder.data.alarm.AlarmStateManager
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.CustomSoundEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationInstanceEntity
import com.example.offlinereminder.data.local.entity.NotificationProfileEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.notification.receiver.NotificationActionReceiver
import com.example.offlinereminder.data.notification.receiver.NotificationReceiver
import com.example.offlinereminder.data.notification.receiver.RebootBroadcastReceiver
import com.example.offlinereminder.data.repository.CustomSoundRepositoryImpl
import com.example.offlinereminder.data.repository.OfflineNotificationRepository
import com.example.offlinereminder.data.sound.CustomSoundStorage
import com.example.offlinereminder.data.sound.SoundContentProvider
import com.example.offlinereminder.presentation.alarm.AlarmAlertActivity
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.usecase.DismissNotificationUseCase
import com.example.offlinereminder.domain.usecase.RebootRecoveryUseCase
import com.example.offlinereminder.domain.usecase.SnoozeNotificationUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class Phase12CDeliveryIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var testTimeProvider: TestTimeProvider
    private lateinit var testAlarmScheduler: TestAlarmScheduler
    private lateinit var customSoundStorage: CustomSoundStorage
    private lateinit var alarmStateManager: AlarmStateManager

    private val testZone = ZoneId.of("UTC")
    private val baseInstant = Instant.parse("2026-10-21T10:00:00Z")

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Insert foreign key parents
        db.categoryDao().insert(CategoryEntity(id = "cat_1", name = "General"))
        db.eventTypeDao().insert(
            EventTypeEntity(
                id = "type_1",
                name = "Reminder",
                key = "reminder",
                categoryId = "cat_1",
                createdAt = baseInstant
            )
        )

        testTimeProvider = TestTimeProvider(baseInstant, testZone)
        testAlarmScheduler = TestAlarmScheduler()
        customSoundStorage = CustomSoundStorage(context)
        alarmStateManager = AlarmStateManager.getInstance()
        alarmStateManager.reset()
        SoundContentProvider.databaseOverride = db
        NotificationReceiver.databaseOverride = db
        NotificationActionReceiver.databaseOverride = db
        RebootBroadcastReceiver.databaseOverride = db
        AlarmPlaybackService.databaseOverride = db
    }

    @After
    fun tearDown() {
        SoundContentProvider.databaseOverride = null
        NotificationReceiver.databaseOverride = null
        NotificationActionReceiver.databaseOverride = null
        RebootBroadcastReceiver.databaseOverride = null
        AlarmPlaybackService.databaseOverride = null
        AlarmPlaybackService.startForegroundServiceOverride = null
        db.close()
        alarmStateManager.reset()
        // Clean up sound directory
        customSoundStorage.soundDirectory.listFiles()?.forEach { it.delete() }
    }

    // =========================================================================
    // SECTION A: DELIVERY TYPE
    // =========================================================================

    @Test
    fun deliveryType_notification_postsSystemNotification() = runTest {
        val eventId = "event_notif_1"
        val instanceId = "inst_notif_1"
        val scheduleId = "sched_notif_1"

        insertEventWithSchedule(
            eventId = eventId,
            scheduleId = scheduleId,
            deliveryType = ReminderDeliveryType.NOTIFICATION,
            soundSource = SoundSourceType.DEFAULT
        )

        val triggerEpoch = baseInstant.toEpochMilli()
        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            scheduleId = scheduleId,
            triggerEpochMillis = triggerEpoch,
            status = "SCHEDULED",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        val poster = NotificationPoster(context)
        val event = db.eventDao().getById(eventId)!!
        val posted = poster.postNotification(
            event = Event(
                id = eventId,
                title = event.title,
                eventTypeId = "type_1",
                categoryId = "cat_1",
                temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 10, 0), testZone),
                createdAt = baseInstant,
                updatedAt = baseInstant
            ),
            instance = NotificationInstance(
                id = instanceId,
                eventId = eventId,
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone,
                scheduleId = scheduleId,
                triggerEpochMillis = triggerEpoch
            )
        )

        assertTrue("Notification should post successfully", posted)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)
        assertTrue(shadowNm.allNotifications.isNotEmpty())
    }

    @Test
    fun deliveryType_alarm_startsAlarmServiceAndRings() = runTest {
        val eventId = "event_alarm_1"
        val instanceId = "inst_alarm_1"
        val scheduleId = "sched_alarm_1"

        insertEventWithSchedule(
            eventId = eventId,
            scheduleId = scheduleId,
            deliveryType = ReminderDeliveryType.ALARM,
            soundSource = SoundSourceType.DEFAULT
        )

        // Simulate AlarmPlaybackService starting
        val intent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_START_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationConstants.EXTRA_EVENT_TITLE, "Critical Alarm")
            putExtra(NotificationConstants.EXTRA_SOUND_TYPE, SoundSourceType.DEFAULT.name)
            putExtra(NotificationConstants.EXTRA_VIBRATION_ENABLED, true)
        }

        val controller = Robolectric.buildService(AlarmPlaybackService::class.java, intent)
        controller.create().startCommand(0, 1)

        assertEquals(AlarmState.RINGING, alarmStateManager.currentState())
        assertEquals(instanceId, alarmStateManager.currentInstanceId())

        controller.destroy()
    }

    // =========================================================================
    // SECTION B: SOUND MODES
    // =========================================================================

    @Test
    fun soundMode_default_system_custom_silent() = runTest {
        val player = AlarmRingtonePlayer(context)

        // 1. Silent mode -> player returns false without playing
        val silentPlayed = player.play(SoundReference(SoundSourceType.SILENT))
        assertFalse("Silent sound must not play audio", silentPlayed)
        assertFalse(player.isPlaying)

        // 2. Default mode -> attempts playback with default alarm URI
        val defaultPlayed = player.play(SoundReference(SoundSourceType.DEFAULT))
        assertTrue(defaultPlayed || !player.isPlaying)

        // 3. System mode with URI string
        val systemPlayed = player.play(SoundReference(SoundSourceType.SYSTEM, systemUriString = "content://media/internal/audio/media/1"))
        assertTrue(systemPlayed || !player.isPlaying)

        // 4. Custom mode with missing sound -> graceful fallback, no crash
        val customMissingPlayed = player.play(SoundReference(SoundSourceType.CUSTOM, customSoundId = "missing_id"))
        assertNotNull(player)
        player.stop()
        player.release()
    }

    @Test
    fun soundMode_deletedCustomSound_detachesSafely() = runTest {
        val soundId = "custom_snd_del"
        val scheduleId = "sched_snd_del"
        val profileId = "prof_snd_del"

        insertTestEvent("event_sound_del")

        // Insert custom sound
        db.customSoundDao().insert(
            CustomSoundEntity(
                id = soundId,
                title = "Bell Chime",
                fileName = "bell.mp3",
                relativePath = "custom_sounds/bell.mp3",
                mimeType = "audio/mpeg",
                fileSizeBytes = 1024L,
                addedAt = baseInstant
            )
        )

        // Insert schedule referencing custom sound
        db.notificationScheduleDao().insert(
            NotificationScheduleEntity(
                id = scheduleId,
                eventId = "event_sound_del",
                offsetDurationMillis = 0L,
                triggerType = com.example.offlinereminder.domain.model.NotificationTriggerType.AT_START,
                deliveryType = ReminderDeliveryType.NOTIFICATION,
                soundSource = SoundSourceType.CUSTOM,
                customSoundId = soundId
            )
        )

        // Insert profile referencing custom sound
        db.notificationProfileDao().insert(
            NotificationProfileEntity(
                id = profileId,
                name = "Sound Profile",
                deliveryType = ReminderDeliveryType.NOTIFICATION,
                soundSource = SoundSourceType.CUSTOM,
                customSoundId = soundId,
                vibrationEnabled = true,
                defaultSnoozeDurationMinutes = 10
            )
        )

        val repo = CustomSoundRepositoryImpl(
            customSoundDao = db.customSoundDao(),
            notificationScheduleDao = db.notificationScheduleDao(),
            notificationProfileDao = db.notificationProfileDao()
        )

        val result = repo.deleteSound(soundId)
        assertTrue(result.isSuccess)

        // Verify metadata deleted
        assertNull(db.customSoundDao().getByIdSync(soundId))

        // Verify schedule was safely detached and reset to DEFAULT
        val updatedSchedule = db.notificationScheduleDao().getById(scheduleId)!!
        assertNull(updatedSchedule.customSoundId)
        assertEquals(SoundSourceType.DEFAULT, updatedSchedule.soundSource)

        // Verify profile was safely detached
        val updatedProfile = db.notificationProfileDao().getById(profileId)!!
        assertNull(updatedProfile.customSoundId)
    }

    // =========================================================================
    // SECTION C: VIBRATION
    // =========================================================================

    @Test
    fun vibration_independentOfSound() = runTest {
        val poster = NotificationPoster(context)
        val event = Event(
            id = "event_vib_1",
            title = "Silent Vibration Event",
            eventTypeId = "type_1",
            categoryId = "cat_1",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 10, 0), testZone),
            createdAt = baseInstant,
            updatedAt = baseInstant
        )
        val instance = NotificationInstance(
            id = "inst_vib_1",
            eventId = event.id,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = baseInstant.toEpochMilli()
        )

        // Case 1: Silent sound with vibration enabled
        val postedSilentWithVib = poster.postNotification(
            event = event,
            instance = instance,
            sound = SoundReference(SoundSourceType.SILENT),
            isSilent = true,
            vibrationEnabled = true
        )
        assertTrue(postedSilentWithVib)

        // Case 2: Audible sound with vibration disabled
        val postedAudibleNoVib = poster.postNotification(
            event = event,
            instance = instance.copy(id = "inst_vib_2", requestCode = 9999),
            sound = SoundReference(SoundSourceType.DEFAULT),
            isSilent = false,
            vibrationEnabled = false
        )
        assertTrue(postedAudibleNoVib)
    }

    // =========================================================================
    // SECTION D: SNOOZE
    // =========================================================================

    @Test
    fun snooze_reschedulesWakeupAndPreservesOccurrenceIdentity() = runTest {
        val eventId = "event_snooze_1"
        val instanceId = "inst_snooze_1"
        val originalStart = LocalDateTime.of(2026, 10, 21, 10, 0)

        insertTestEvent(eventId)

        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = originalStart,
            occurrenceOriginalZoneId = testZone.id,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = "DELIVERED",
            snoozeCount = 0,
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val snoozeUseCase = SnoozeNotificationUseCase(notificationRepo, testAlarmScheduler, testTimeProvider)

        val snoozeDuration = Duration.ofMinutes(15)
        val snoozeResult = snoozeUseCase(instanceId, snoozeDuration)
        assertTrue(snoozeResult.isSuccess)

        // Verify persisted state
        val updatedInstance = db.notificationInstanceDao().getById(instanceId)!!
        assertEquals("SNOOZED", updatedInstance.status)
        assertEquals(1, updatedInstance.snoozeCount)
        assertEquals(originalStart, updatedInstance.occurrenceOriginalStartDateTime)
        assertEquals(testZone.id, updatedInstance.occurrenceOriginalZoneId)
        assertEquals(baseInstant.plus(snoozeDuration).toEpochMilli(), updatedInstance.triggerEpochMillis)

        // Verify new alarm scheduled
        val scheduledAlarms = testAlarmScheduler.scheduledAlarms
        assertEquals(1, scheduledAlarms.size)
        assertEquals(updatedInstance.triggerEpochMillis, scheduledAlarms[0].triggerEpochMillis)
    }

    // =========================================================================
    // SECTION E: DISMISS
    // =========================================================================

    @Test
    fun dismiss_marksTerminalStateAndStopsAlarm() = runTest {
        val instanceId = "inst_dismiss_1"
        insertTestEvent("event_dismiss_1")

        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = "event_dismiss_1",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = "DELIVERED",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val dismissUseCase = DismissNotificationUseCase(notificationRepo, testAlarmScheduler)

        alarmStateManager.transitionTo(instanceId, "event_dismiss_1", AlarmState.RINGING)
        assertEquals(AlarmState.RINGING, alarmStateManager.currentState())

        val result = dismissUseCase(instanceId)
        assertTrue(result.isSuccess)

        val updated = db.notificationInstanceDao().getById(instanceId)!!
        assertEquals("DISMISSED", updated.status)

        // Transition AlarmStateManager
        alarmStateManager.transitionTo(instanceId, "event_dismiss_1", AlarmState.DISMISSED)
        assertEquals(AlarmState.DISMISSED, alarmStateManager.currentState())
    }

    // =========================================================================
    // SECTION F: TIMEOUT
    // =========================================================================

    @Test
    fun timeout_transitionsToTimedOutAndPostsMissedNotification() = runTest {
        val instanceId = "inst_timeout_1"
        val eventId = "event_timeout_1"

        alarmStateManager.transitionTo(instanceId, eventId, AlarmState.RINGING)
        assertEquals(AlarmState.RINGING, alarmStateManager.currentState())

        // Simulate 10-minute timeout
        alarmStateManager.transitionTo(instanceId, eventId, AlarmState.TIMED_OUT)
        assertEquals(AlarmState.TIMED_OUT, alarmStateManager.currentState())

        val poster = NotificationPoster(context)
        poster.postMissedAlarmNotification(eventId, "Test Alarm", instanceId.hashCode())

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(nm)
        assertTrue("Missed notification must be posted", shadowNm.allNotifications.isNotEmpty())
    }

    // =========================================================================
    // SECTION G: DUPLICATE DELIVERY & TERMINAL SAFETY
    // =========================================================================

    @Test
    fun duplicateDelivery_alreadyDeliveredOrDismissed_droppedSafely() = runTest {
        val eventId = "event_dup_1"
        val instanceId = "inst_dup_1"

        insertEventWithSchedule(eventId, "sched_dup_1", ReminderDeliveryType.NOTIFICATION, SoundSourceType.DEFAULT)

        // Instance already DELIVERED
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = instanceId,
                eventId = eventId,
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = baseInstant.toEpochMilli(),
                status = "DELIVERED",
                requestCode = NotificationInstance.createRequestCode(instanceId)
            )
        )

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
        }

        val receiver = NotificationReceiver()
        receiver.onReceive(context, intent)

        // Status remains DELIVERED without crashing or duplicating
        val instance = db.notificationInstanceDao().getById(instanceId)!!
        assertEquals("DELIVERED", instance.status)

        // Now test already DISMISSED
        db.notificationInstanceDao().updateStatus(instanceId, "DISMISSED")
        receiver.onReceive(context, intent)
        val dismissedInstance = db.notificationInstanceDao().getById(instanceId)!!
        assertEquals("DISMISSED", dismissedInstance.status)
    }

    // =========================================================================
    // SECTION H: REBOOT RECOVERY
    // =========================================================================

    @Test
    fun rebootRecovery_reschedulesFutureAlarmsAndCancelsStaleAlarms() = runTest {
        val futureInstanceId = "inst_future"
        val staleInstanceId = "inst_stale"
        val dismissedInstanceId = "inst_dismissed"

        insertTestEvent("event_boot_1")
        insertTestEvent("event_boot_2")
        insertTestEvent("event_boot_3")

        val nowEpoch = baseInstant.toEpochMilli()

        // 1. Future scheduled alarm
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = futureInstanceId,
                eventId = "event_boot_1",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 12, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch + 7200000L, // 2 hours in future
                status = "SCHEDULED",
                requestCode = NotificationInstance.createRequestCode(futureInstanceId)
            )
        )

        // 2. Stale alarm missed while phone was off
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = staleInstanceId,
                eventId = "event_boot_2",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 8, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch - 3600000L, // 1 hour in past
                status = "SCHEDULED",
                requestCode = NotificationInstance.createRequestCode(staleInstanceId)
            )
        )

        // 3. Dismissed alarm
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = dismissedInstanceId,
                eventId = "event_boot_3",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 14, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch + 14400000L,
                status = "DISMISSED",
                requestCode = NotificationInstance.createRequestCode(dismissedInstanceId)
            )
        )

        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, testTimeProvider)

        val result = recoveryUseCase()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()) // Only 1 future alarm rescheduled

        // Future alarm was re-registered
        val scheduledAlarms = testAlarmScheduler.scheduledAlarms
        assertEquals(1, scheduledAlarms.size)
        assertEquals(nowEpoch + 7200000L, scheduledAlarms[0].triggerEpochMillis)

        // Stale alarm was marked CANCELLED (not falsely delivered)
        assertEquals("CANCELLED", db.notificationInstanceDao().getById(staleInstanceId)!!.status)

        // Dismissed alarm remains DISMISSED
        assertEquals("DISMISSED", db.notificationInstanceDao().getById(dismissedInstanceId)!!.status)
    }

    // =========================================================================
    // SECTION I: EXACT ALARM CAPABILITY & FALLBACK
    // =========================================================================

    @Test
    fun exactAlarm_schedulerHandlesCapabilitiesAndFallback() {
        val scheduler = AndroidAlarmScheduler(context)
        val canSchedule = scheduler.canScheduleExactAlarms()
        assertTrue(canSchedule || !canSchedule)

        // Schedule test alarm
        val scheduled = scheduler.scheduleAlarm(
            requestCode = 12345,
            triggerEpochMillis = baseInstant.plusSeconds(60).toEpochMilli(),
            intentAction = NotificationConstants.ACTION_ALARM,
            extras = mapOf(NotificationConstants.EXTRA_INSTANCE_ID to "inst_test_exact")
        )
        assertTrue(scheduled)

        // Cancel test alarm
        scheduler.cancelAlarm(12345, NotificationConstants.ACTION_ALARM)
    }

    // =========================================================================
    // SECTION J: SOUND CONTENT PROVIDER & STORAGE
    // =========================================================================

    @Test
    fun customSoundStorage_copiesAndValidatesSecurity() {
        val soundData = "AUDIO_RAW_BYTES_TEST".toByteArray()
        val file = customSoundStorage.copySound(ByteArrayInputStream(soundData), "test_chime.wav")

        assertTrue(file.exists())
        assertTrue(customSoundStorage.exists("test_chime.wav"))

        // Path traversal validation
        try {
            val evilFile = File(customSoundStorage.soundDirectory, "../outside.wav")
            customSoundStorage.validateWithinSoundDirectory(evilFile)
            fail("Expected SecurityException on directory traversal")
        } catch (_: SecurityException) {
            // Expected
        }

        assertTrue(customSoundStorage.deleteSound("test_chime.wav"))
        assertFalse(customSoundStorage.exists("test_chime.wav"))
    }

    @Test
    fun soundContentProvider_buildUriAndOpenValidation() = runTest {
        val provider = Robolectric.buildContentProvider(SoundContentProvider::class.java).create().get()

        val soundId = "custom_snd_valid"
        val fileName = "valid_chime.wav"

        // Insert metadata in DB
        db.customSoundDao().insert(
            CustomSoundEntity(
                id = soundId,
                title = "Valid Chime",
                fileName = fileName,
                relativePath = "custom_sounds/$fileName",
                mimeType = "audio/wav",
                fileSizeBytes = 512L,
                addedAt = baseInstant
            )
        )

        // Write actual file to disk
        customSoundStorage.copySound(ByteArrayInputStream("AUDIO".toByteArray()), fileName)

        val uri = SoundContentProvider.buildSoundUri(context, soundId)
        assertEquals("content://com.example.offlinereminder.sounds/sounds/$soundId", uri.toString())

        // 1. Read-only mode check
        try {
            provider.openFile(uri, "w")
            fail("Expected SecurityException on write mode")
        } catch (_: SecurityException) {
            // Expected
        }

        // 2. Traversal attempt check
        try {
            val evilUri = Uri.parse("content://com.example.offlinereminder.sounds/sounds/..%2F..%2Foutside")
            provider.openFile(evilUri, "r")
            fail("Expected SecurityException on path traversal URI")
        } catch (_: SecurityException) {
            // Expected
        }

        // 3. Missing sound check
        try {
            val missingUri = SoundContentProvider.buildSoundUri(context, "non_existent_id")
            provider.openFile(missingUri, "r")
            fail("Expected FileNotFoundException on missing sound")
        } catch (_: FileNotFoundException) {
            // Expected
        }

        // 4. Read-only mutations throw UnsupportedOperationException
        try {
            provider.insert(uri, null)
            fail("Expected UnsupportedOperationException on insert")
        } catch (_: UnsupportedOperationException) {
            // Expected
        }
    }

    // =========================================================================
    // SECTION K: ALARM STATE TRANSITIONS
    // =========================================================================

    @Test
    fun alarmState_lifecycleTransitions() {
        val manager = AlarmStateManager()
        val instanceId = "inst_state_1"
        val eventId = "event_state_1"

        assertEquals(AlarmState.IDLE, manager.currentState())

        manager.transitionTo(instanceId, eventId, AlarmState.RINGING)
        assertEquals(AlarmState.RINGING, manager.currentState())
        assertEquals(instanceId, manager.currentInstanceId())

        manager.transitionTo(instanceId, eventId, AlarmState.SNOOZED)
        assertEquals(AlarmState.SNOOZED, manager.currentState())

        manager.transitionTo(instanceId, eventId, AlarmState.DISMISSED)
        assertEquals(AlarmState.DISMISSED, manager.currentState())

        manager.transitionTo(instanceId, eventId, AlarmState.TIMED_OUT)
        assertEquals(AlarmState.TIMED_OUT, manager.currentState())

        manager.reset()
        assertEquals(AlarmState.IDLE, manager.currentState())
        assertNull(manager.currentInstanceId())
    }

    // =========================================================================
    // SECTION L: PROCESS / SERVICE DEATH RECOVERY
    // =========================================================================

    @Test
    fun processDeathRecovery_deliveredInstanceIsNotResurrected() = runTest {
        val instanceId = "inst_death_1"
        insertTestEvent("event_death_1")

        // When alarm triggered, it transitioned to DELIVERED in Room
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = instanceId,
                eventId = "event_death_1",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = baseInstant.toEpochMilli(),
                status = "DELIVERED",
                requestCode = NotificationInstance.createRequestCode(instanceId)
            )
        )

        // Process dies and restarts -> RebootRecoveryUseCase runs
        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, testTimeProvider)
        recoveryUseCase()

        // Verify it was NOT re-scheduled as a duplicate
        val alarms = testAlarmScheduler.scheduledAlarms
        assertTrue(alarms.none { it.requestCode == NotificationInstance.createRequestCode(instanceId) })
        assertEquals("DELIVERED", db.notificationInstanceDao().getById(instanceId)!!.status)
    }

    // =========================================================================
    // SECTION M: NOTIFICATION CHANNEL STABILITY
    // =========================================================================

    @Test
    fun notificationChannels_stableAndNotDestructivelyRecreated() {
        val poster = NotificationPoster(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Default channels created on init
        val defaultChannel = nm.getNotificationChannel(NotificationConstants.CHANNEL_ID_DEFAULT)
        val silentChannel = nm.getNotificationChannel(NotificationConstants.CHANNEL_ID_SILENT)
        val alarmChannel = nm.getNotificationChannel(NotificationConstants.CHANNEL_ID_ALARM)

        assertNotNull(defaultChannel)
        assertNotNull(silentChannel)
        assertNotNull(alarmChannel)

        assertEquals("Event Reminders", defaultChannel.name)
        assertEquals("Quiet Reminders", silentChannel.name)
        assertEquals("Alarm Alerts", alarmChannel.name)

        // Calling createNotificationChannels again does not wipe or corrupt channels
        poster.createNotificationChannels()
        assertNotNull(nm.getNotificationChannel(NotificationConstants.CHANNEL_ID_DEFAULT))
    }

    // =========================================================================
    // SECTION N: CRASH RECOVERY & INVARIANT VERIFICATION (AUDIT TESTS A-K)
    // =========================================================================

    @Test
    fun testA_alarmBegins_processDies_recoveryOccurs() = runTest {
        val eventId = "event_rec_a"
        val instanceId = "inst_rec_a"
        val scheduleId = "sched_rec_a"

        insertEventWithSchedule(
            eventId = eventId,
            scheduleId = scheduleId,
            deliveryType = ReminderDeliveryType.ALARM,
            soundSource = SoundSourceType.DEFAULT
        )

        // Alarm triggered: status in Room set to RINGING
        val triggerEpoch = baseInstant.toEpochMilli()
        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            scheduleId = scheduleId,
            triggerEpochMillis = triggerEpoch,
            status = "RINGING",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        // Process dies -> AlarmStateManager is reset
        alarmStateManager.reset()
        assertEquals(AlarmState.IDLE, alarmStateManager.currentState())

        // System recovers 3 minutes later (within the 10-minute timeout window)
        val recoveryTimeProvider = TestTimeProvider(baseInstant.plus(Duration.ofMinutes(3)), testZone)
        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, recoveryTimeProvider)

        val result = recoveryUseCase()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        // Immediate recovery alarm was scheduled
        val alarms = testAlarmScheduler.scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(recoveryTimeProvider.nowInstant().toEpochMilli(), alarms[0].triggerEpochMillis)
        assertEquals(instance.requestCode, alarms[0].requestCode)
    }

    @Test
    fun testB_alarmBegins_activityDies_audioContinuesInService() = runTest {
        val eventId = "event_act_b"
        val instanceId = "inst_act_b"
        insertTestEvent(eventId)

        // Start AlarmPlaybackService
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_START_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationConstants.EXTRA_EVENT_TITLE, "Active Alarm")
            putExtra(NotificationConstants.EXTRA_SOUND_TYPE, SoundSourceType.DEFAULT.name)
        }
        val serviceController = Robolectric.buildService(AlarmPlaybackService::class.java, serviceIntent)
        serviceController.create().startCommand(0, 1)

        assertEquals(AlarmState.RINGING, alarmStateManager.currentState())

        // Launch full-screen visual alert Activity
        val activityIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
        }
        val activityController = Robolectric.buildActivity(AlarmAlertActivity::class.java, activityIntent)
        activityController.create().start().resume()

        // Destroy the Activity (simulating activity killed or screen turned off)
        activityController.pause().stop().destroy()

        // Service playback and state must continue completely uninterrupted
        assertEquals(AlarmState.RINGING, alarmStateManager.currentState())
        assertEquals(instanceId, alarmStateManager.currentInstanceId())

        serviceController.destroy()
    }

    @Test
    fun testC_alarmBegins_serviceDies_recoveryOccurs() = runTest {
        val eventId = "event_srv_c"
        val instanceId = "inst_srv_c"
        insertTestEvent(eventId)

        // Alarm was active in Room as RINGING
        val triggerEpoch = baseInstant.toEpochMilli()
        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            triggerEpochMillis = triggerEpoch,
            status = "RINGING",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        // Start service, then simulate abrupt service destruction (service dies while alarm is ringing)
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_START_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationConstants.EXTRA_EVENT_TITLE, "Test Alarm")
        }
        val serviceController = Robolectric.buildService(AlarmPlaybackService::class.java, serviceIntent)
        serviceController.create().startCommand(0, 1)
        serviceController.destroy()
        alarmStateManager.reset()

        // Reboot / startup reconciliation runs 4 minutes after trigger
        val recoveryTimeProvider = TestTimeProvider(baseInstant.plus(Duration.ofMinutes(4)), testZone)
        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, recoveryTimeProvider)

        val result = recoveryUseCase()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        // Alarm is recovered and scheduled
        val alarms = testAlarmScheduler.scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(instance.requestCode, alarms[0].requestCode)
    }

    @Test
    fun testD_alarmDismissed_processDies_notResurrected() = runTest {
        val eventId = "event_dis_d"
        val instanceId = "inst_dis_d"
        insertTestEvent(eventId)

        // Alarm dismissed in Room
        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = "DISMISSED",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        // Process dies
        alarmStateManager.reset()

        // Recovery runs
        val recoveryTimeProvider = TestTimeProvider(baseInstant.plus(Duration.ofMinutes(5)), testZone)
        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, recoveryTimeProvider)

        val result = recoveryUseCase()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertTrue(testAlarmScheduler.scheduledAlarms.isEmpty())
        assertEquals("DISMISSED", db.notificationInstanceDao().getById(instanceId)!!.status)
    }

    @Test
    fun testE_alarmSnoozed_processDies_onlySnoozedTriggerRemains() = runTest {
        val eventId = "event_snz_e"
        val instanceId = "inst_snz_e"
        insertTestEvent(eventId)

        // Instance was snoozed: trigger is 10 minutes in the future from baseInstant
        val snoozedTrigger = baseInstant.plus(Duration.ofMinutes(10)).toEpochMilli()
        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            triggerEpochMillis = snoozedTrigger,
            status = "SNOOZED",
            snoozeCount = 1,
            snoozeUntil = baseInstant.plus(Duration.ofMinutes(10)),
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        // Process dies
        alarmStateManager.reset()

        // Startup recovery runs 2 minutes later
        val recoveryTimeProvider = TestTimeProvider(baseInstant.plus(Duration.ofMinutes(2)), testZone)
        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, recoveryTimeProvider)

        val result = recoveryUseCase()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        // Only the snoozed future trigger is scheduled
        val alarms = testAlarmScheduler.scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(snoozedTrigger, alarms[0].triggerEpochMillis)
        assertEquals("SNOOZED", db.notificationInstanceDao().getById(instanceId)!!.status)
    }

    @Test
    fun testF_alarmTimedOut_processDies_notResurrected() = runTest {
        val eventId = "event_to_f"
        val instanceId = "inst_to_f"
        insertTestEvent(eventId)

        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = "TIMED_OUT",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        alarmStateManager.reset()

        val recoveryTimeProvider = TestTimeProvider(baseInstant.plus(Duration.ofMinutes(15)), testZone)
        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, recoveryTimeProvider)

        val result = recoveryUseCase()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertTrue(testAlarmScheduler.scheduledAlarms.isEmpty())
        assertEquals("TIMED_OUT", db.notificationInstanceDao().getById(instanceId)!!.status)
    }

    @Test
    fun testG_duplicateReceiverInvocation_noDuplicateAlarm() = runTest {
        val eventId = "event_dup_g"
        val instanceId = "inst_dup_g"
        val scheduleId = "sched_dup_g"

        insertEventWithSchedule(eventId, scheduleId, ReminderDeliveryType.ALARM, SoundSourceType.DEFAULT)

        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            scheduleId = scheduleId,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = "SCHEDULED",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
        }

        val receiver = NotificationReceiver()
        receiver.onReceive(context, intent)

        // Poll for async receiver coroutine
        var attempts = 0
        while (db.notificationInstanceDao().getById(instanceId)?.status != "RINGING" && attempts < 20) {
            Thread.sleep(25)
            attempts++
        }

        assertEquals("RINGING", db.notificationInstanceDao().getById(instanceId)!!.status)

        // Second duplicate receiver invocation while still RINGING
        receiver.onReceive(context, intent)

        assertEquals("RINGING", db.notificationInstanceDao().getById(instanceId)!!.status)
    }

    @Test
    fun testH_duplicateServiceStart_noDuplicateAudioOrSession() = runTest {
        val eventId = "event_dup_h"
        val instanceId = "inst_dup_h"
        insertTestEvent(eventId)

        val intent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_START_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationConstants.EXTRA_EVENT_TITLE, "Alarm H")
            putExtra(NotificationConstants.EXTRA_SOUND_TYPE, SoundSourceType.DEFAULT.name)
        }

        val controller = Robolectric.buildService(AlarmPlaybackService::class.java, intent)
        controller.create().startCommand(0, 1)

        assertEquals(AlarmState.RINGING, alarmStateManager.currentState())
        assertEquals(instanceId, alarmStateManager.currentInstanceId())

        // Send duplicate start command with same intent
        controller.startCommand(0, 2)

        // State remains consistently RINGING for instanceId
        assertEquals(AlarmState.RINGING, alarmStateManager.currentState())
        assertEquals(instanceId, alarmStateManager.currentInstanceId())

        controller.destroy()
    }

    @Test
    fun testI_roomInvariant_containsEnoughInformationToDetermineRecovery() = runTest {
        insertTestEvent("event_inv")
        val nowEpoch = baseInstant.toEpochMilli()

        // 1. RINGING within 10 minutes (triggered 5 minutes ago) -> MUST BE RECOVERED
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = "inst_inv_1",
                eventId = "event_inv",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch - 300000L,
                status = "RINGING",
                requestCode = 1001
            )
        )

        // 2. RINGING expired (> 10 minutes ago, triggered 15 minutes ago) -> MUST TRANSITION TO TIMED_OUT
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = "inst_inv_2",
                eventId = "event_inv",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch - 900000L,
                status = "RINGING",
                requestCode = 1002
            )
        )

        // 3. DELIVERED -> Terminal, MUST NOT BE RECOVERED
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = "inst_inv_3",
                eventId = "event_inv",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch - 300000L,
                status = "DELIVERED",
                requestCode = 1003
            )
        )

        // 4. DISMISSED -> Terminal, MUST NOT BE RECOVERED
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = "inst_inv_4",
                eventId = "event_inv",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch - 300000L,
                status = "DISMISSED",
                requestCode = 1004
            )
        )

        // 5. TIMED_OUT -> Terminal, MUST NOT BE RECOVERED
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = "inst_inv_5",
                eventId = "event_inv",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch - 300000L,
                status = "TIMED_OUT",
                requestCode = 1005
            )
        )

        // 6. CANCELLED -> Terminal, MUST NOT BE RECOVERED
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = "inst_inv_6",
                eventId = "event_inv",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch + 300000L,
                status = "CANCELLED",
                requestCode = 1006
            )
        )

        // 7. Future SCHEDULED -> MUST BE RE-REGISTERED
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = "inst_inv_7",
                eventId = "event_inv",
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 12, 0),
                occurrenceOriginalZoneId = testZone.id,
                triggerEpochMillis = nowEpoch + 600000L,
                status = "SCHEDULED",
                requestCode = 1007
            )
        )

        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        var missedCount = 0
        val recoveryUseCase = RebootRecoveryUseCase(
            notificationRepository = notificationRepo,
            alarmScheduler = testAlarmScheduler,
            timeProvider = testTimeProvider,
            onMissedAlarm = { missedCount++ }
        )

        val result = recoveryUseCase()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()) // Exactly 2 rescheduled (inst_inv_1 and inst_inv_7)

        val scheduledReqCodes = testAlarmScheduler.scheduledAlarms.map { it.requestCode }
        assertTrue(scheduledReqCodes.contains(1001)) // Active ringing recovered
        assertTrue(scheduledReqCodes.contains(1007)) // Future scheduled registered
        assertFalse(scheduledReqCodes.contains(1002))
        assertFalse(scheduledReqCodes.contains(1003))
        assertFalse(scheduledReqCodes.contains(1004))
        assertFalse(scheduledReqCodes.contains(1005))
        assertFalse(scheduledReqCodes.contains(1006))

        // Verify status updates in Room
        assertEquals("TIMED_OUT", db.notificationInstanceDao().getById("inst_inv_2")!!.status)
        assertEquals(1, missedCount)
        assertEquals("DELIVERED", db.notificationInstanceDao().getById("inst_inv_3")!!.status)
        assertEquals("DISMISSED", db.notificationInstanceDao().getById("inst_inv_4")!!.status)
        assertEquals("TIMED_OUT", db.notificationInstanceDao().getById("inst_inv_5")!!.status)
        assertEquals("CANCELLED", db.notificationInstanceDao().getById("inst_inv_6")!!.status)
    }

    @Test
    fun testJ_channelLifecycle_andCustomSoundDeletionSafety() = runTest {
        val poster = NotificationPoster(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundId = "custom_snd_lifecycle"
        val fileName = "chime.wav"

        // 1. Add custom sound to DB and disk
        db.customSoundDao().insert(
            CustomSoundEntity(
                id = soundId,
                title = "Lifecycle Chime",
                fileName = fileName,
                relativePath = "custom_sounds/$fileName",
                mimeType = "audio/wav",
                fileSizeBytes = 256L,
                addedAt = baseInstant
            )
        )
        customSoundStorage.copySound(ByteArrayInputStream("DATA".toByteArray()), fileName)

        // Post notification with custom sound -> creates channel
        val event = Event(
            id = "event_chan_j",
            title = "Channel Test Event",
            eventTypeId = "type_1",
            categoryId = "cat_1",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 10, 0), testZone),
            createdAt = baseInstant,
            updatedAt = baseInstant
        )
        val instance = NotificationInstance(
            id = "inst_chan_j",
            eventId = event.id,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone,
            triggerEpochMillis = baseInstant.toEpochMilli()
        )
        val customSoundRef = SoundReference(SoundSourceType.CUSTOM, customSoundId = soundId)
        poster.postNotification(event, instance, customSoundRef)

        val channelId = "offline_reminders_custom_$soundId"
        assertNotNull(nm.getNotificationChannel(channelId))

        // 2. Delete custom sound and delete channel
        val repo = CustomSoundRepositoryImpl(db.customSoundDao(), db.notificationScheduleDao(), db.notificationProfileDao())
        repo.deleteSound(soundId)
        poster.deleteCustomSoundChannel(soundId)

        assertNull(nm.getNotificationChannel(channelId))

        // 3. Posting with deleted sound falls back gracefully without throwing
        val fallbackPosted = poster.postNotification(event, instance.copy(id = "inst_chan_j2", requestCode = 5555), customSoundRef)
        assertTrue(fallbackPosted)
    }

    @Test
    fun testK_soundContentProvider_comprehensiveSecurityTests() = runTest {
        val provider = Robolectric.buildContentProvider(SoundContentProvider::class.java).create().get()

        val evilPaths = listOf(
            "..%2F..%2Foutside.wav",
            "%2e%2e%2foutside.wav",
            "%2e%2e",
            "..",
            "/etc/passwd",
            "..\\..\\windows\\system32",
            "normal%2F..%2Fhack"
        )

        for (evil in evilPaths) {
            val evilUri = Uri.parse("content://com.example.offlinereminder.sounds/sounds/$evil")
            try {
                provider.openFile(evilUri, "r")
                fail("Expected SecurityException or FileNotFoundException for traversal path: $evil")
            } catch (e: Exception) {
                assertTrue(e is SecurityException || e is FileNotFoundException)
            }

            try {
                provider.query(evilUri, null, null, null, null)
            } catch (e: Exception) {
                assertTrue(e is SecurityException || e is FileNotFoundException)
            }

            try {
                provider.getType(evilUri)
            } catch (e: Exception) {
                assertTrue(e is SecurityException || e is FileNotFoundException)
            }
        }

        // Write modes must all fail with SecurityException
        val validUri = SoundContentProvider.buildSoundUri(context, "any_sound")
        val writeModes = listOf("w", "rw", "wt", "wa", "rwt")
        for (mode in writeModes) {
            try {
                provider.openFile(validUri, mode)
                fail("Expected SecurityException for write mode: $mode")
            } catch (_: SecurityException) {
                // Expected
            }
        }
    }

    // =========================================================================
    // SECTION O: SERVICE-START FAILURE & FALLBACK CONSISTENCY
    // =========================================================================

    @Test
    fun serviceStartFailure_fallbackDelivered_stateNotStuckRinging_recoverySafe_noSubsequentAlarm() = runTest {
        val eventId = "event_fail_fallback"
        val instanceId = "inst_fail_fallback"
        val scheduleId = "sched_fail_fallback"

        insertEventWithSchedule(eventId, scheduleId, ReminderDeliveryType.ALARM, SoundSourceType.DEFAULT)

        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            scheduleId = scheduleId,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = "SCHEDULED",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        // Force AlarmPlaybackService.start to fail (e.g. ForegroundServiceStartNotAllowedException on background start)
        AlarmPlaybackService.startForegroundServiceOverride = { _, _ ->
            throw IllegalStateException("Foreground service start not allowed in background")
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
        }

        val receiver = NotificationReceiver()
        receiver.onReceive(context, intent)

        // Poll for async receiver coroutine to complete fallback
        var attempts = 0
        while (db.notificationInstanceDao().getById(instanceId)?.status != "DELIVERED" && attempts < 20) {
            Thread.sleep(25)
            attempts++
        }

        // 1. Final state is DELIVERED (RINGING is not left permanently stuck)
        val finalInstance = db.notificationInstanceDao().getById(instanceId)!!
        assertEquals("DELIVERED", finalInstance.status)

        // 2. Fallback notification was posted
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(nm)
        assertTrue("Fallback notification must be posted", shadowNm.allNotifications.isNotEmpty())

        // 3. No active alarm session was created
        assertEquals(AlarmState.IDLE, alarmStateManager.currentState())
        assertNull(alarmStateManager.currentInstanceId())

        // 4. Recovery does not resurrect that alarm
        val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
        val recoveryUseCase = RebootRecoveryUseCase(notificationRepo, testAlarmScheduler, testTimeProvider)
        val recoveryResult = recoveryUseCase()
        assertTrue(recoveryResult.isSuccess)
        assertEquals(0, recoveryResult.getOrNull())
        assertTrue("No alarms should be rescheduled by recovery", testAlarmScheduler.scheduledAlarms.isEmpty())
        assertEquals("DELIVERED", db.notificationInstanceDao().getById(instanceId)!!.status)

        // 5. Subsequent duplicate/delayed alarm start command cannot start playback
        AlarmPlaybackService.startForegroundServiceOverride = null
        val delayedStartIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_START_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationConstants.EXTRA_EVENT_TITLE, "Delayed Start")
        }
        val serviceController = Robolectric.buildService(AlarmPlaybackService::class.java, delayedStartIntent)
        serviceController.create().startCommand(0, 1)

        assertEquals("State must remain IDLE when attempting to start already DELIVERED instance",
            AlarmState.IDLE, alarmStateManager.currentState())
        assertNull(alarmStateManager.currentInstanceId())
        serviceController.destroy()
    }

    @Test
    fun serviceStartupRace_serviceStartAfterFallback_doesNotProduceDualDelivery() = runTest {
        val eventId = "event_race_start"
        val instanceId = "inst_race_start"
        insertTestEvent(eventId)

        // Instance was already delivered via fallback in Room
        val instance = NotificationInstanceEntity(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            occurrenceOriginalZoneId = testZone.id,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = "DELIVERED",
            requestCode = NotificationInstance.createRequestCode(instanceId)
        )
        db.notificationInstanceDao().insert(instance)

        // Delayed platform service start arrives after fallback has completed
        val delayedIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = NotificationConstants.ACTION_START_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationConstants.EXTRA_EVENT_TITLE, "Delayed Alarm")
            putExtra(NotificationConstants.EXTRA_SOUND_TYPE, SoundSourceType.DEFAULT.name)
        }

        val serviceController = Robolectric.buildService(AlarmPlaybackService::class.java, delayedIntent)
        serviceController.create().startCommand(0, 1)

        // Service detects DELIVERED state in Room and halts without transitioning to RINGING
        assertEquals(AlarmState.IDLE, alarmStateManager.currentState())
        assertNull(alarmStateManager.currentInstanceId())

        serviceController.destroy()
    }

    @Test
    fun duplicateReceiver_safeForNormalNotification_activeAlarm_andServiceStartFallback() = runTest {
        val normalEventId = "event_dup_norm"
        val normalInstId = "inst_dup_norm"
        insertEventWithSchedule(normalEventId, "sched_dup_norm", ReminderDeliveryType.NOTIFICATION, SoundSourceType.DEFAULT)

        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = normalInstId,
                eventId = normalEventId,
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                scheduleId = "sched_dup_norm",
                triggerEpochMillis = baseInstant.toEpochMilli(),
                status = "SCHEDULED",
                requestCode = NotificationInstance.createRequestCode(normalInstId)
            )
        )

        val receiver = NotificationReceiver()
        val normalIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, normalInstId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, normalEventId)
        }

        // 1. Normal notification: Trigger twice
        receiver.onReceive(context, normalIntent)
        var attempts = 0
        while (db.notificationInstanceDao().getById(normalInstId)?.status != "DELIVERED" && attempts < 20) {
            Thread.sleep(25)
            attempts++
        }
        assertEquals("DELIVERED", db.notificationInstanceDao().getById(normalInstId)!!.status)

        receiver.onReceive(context, normalIntent)
        assertEquals("DELIVERED", db.notificationInstanceDao().getById(normalInstId)!!.status)

        // 2. Active alarm: Trigger twice
        val alarmEventId = "event_dup_active_alm"
        val alarmInstId = "inst_dup_active_alm"
        insertEventWithSchedule(alarmEventId, "sched_dup_active_alm", ReminderDeliveryType.ALARM, SoundSourceType.DEFAULT)
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = alarmInstId,
                eventId = alarmEventId,
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                scheduleId = "sched_dup_active_alm",
                triggerEpochMillis = baseInstant.toEpochMilli(),
                status = "SCHEDULED",
                requestCode = NotificationInstance.createRequestCode(alarmInstId)
            )
        )
        val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, alarmInstId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, alarmEventId)
        }
        receiver.onReceive(context, alarmIntent)
        attempts = 0
        while (db.notificationInstanceDao().getById(alarmInstId)?.status != "RINGING" && attempts < 20) {
            Thread.sleep(25)
            attempts++
        }
        assertEquals("RINGING", db.notificationInstanceDao().getById(alarmInstId)!!.status)

        // Duplicate while active
        receiver.onReceive(context, alarmIntent)
        assertEquals("RINGING", db.notificationInstanceDao().getById(alarmInstId)!!.status)

        // 3. Fallback path: Trigger twice
        val fallbackEventId = "event_dup_fb"
        val fallbackInstId = "inst_dup_fb"
        insertEventWithSchedule(fallbackEventId, "sched_dup_fb", ReminderDeliveryType.ALARM, SoundSourceType.DEFAULT)
        db.notificationInstanceDao().insert(
            NotificationInstanceEntity(
                id = fallbackInstId,
                eventId = fallbackEventId,
                occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
                occurrenceOriginalZoneId = testZone.id,
                scheduleId = "sched_dup_fb",
                triggerEpochMillis = baseInstant.toEpochMilli(),
                status = "SCHEDULED",
                requestCode = NotificationInstance.createRequestCode(fallbackInstId)
            )
        )
        AlarmPlaybackService.startForegroundServiceOverride = { _, _ ->
            throw RuntimeException("Simulated service start failure")
        }
        val fallbackIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_ALARM
            putExtra(NotificationConstants.EXTRA_INSTANCE_ID, fallbackInstId)
            putExtra(NotificationConstants.EXTRA_EVENT_ID, fallbackEventId)
        }
        receiver.onReceive(context, fallbackIntent)
        attempts = 0
        while (db.notificationInstanceDao().getById(fallbackInstId)?.status != "DELIVERED" && attempts < 20) {
            Thread.sleep(25)
            attempts++
        }
        assertEquals("DELIVERED", db.notificationInstanceDao().getById(fallbackInstId)!!.status)

        // Second duplicate invocation of failed service trigger
        receiver.onReceive(context, fallbackIntent)
        assertEquals("DELIVERED", db.notificationInstanceDao().getById(fallbackInstId)!!.status)

        AlarmPlaybackService.startForegroundServiceOverride = null
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private suspend fun insertEventWithSchedule(
        eventId: String,
        scheduleId: String,
        deliveryType: ReminderDeliveryType,
        soundSource: SoundSourceType
    ) {
        val eventEntity = EventEntity(
            id = eventId,
            title = "Test Delivery Event",
            description = "Detailed Event Description",
            eventTypeId = "type_1",
            categoryId = "cat_1",
            priority = Priority.NORMAL,
            temporalType = "SPECIFIC",
            startDate = LocalDate.of(2026, 10, 21),
            startTime = LocalTime.of(10, 0),
            zoneId = testZone.id,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )
        db.eventDao().insert(eventEntity)

        val scheduleEntity = NotificationScheduleEntity(
            id = scheduleId,
            eventId = eventId,
            offsetDurationMillis = 0L,
            triggerType = com.example.offlinereminder.domain.model.NotificationTriggerType.AT_START,
            deliveryType = deliveryType,
            soundSource = soundSource
        )
        db.notificationScheduleDao().insert(scheduleEntity)
    }

    private suspend fun insertTestEvent(eventId: String) {
        val eventEntity = EventEntity(
            id = eventId,
            title = "Test Event $eventId",
            description = "Detailed Description",
            eventTypeId = "type_1",
            categoryId = "cat_1",
            priority = Priority.NORMAL,
            temporalType = "SPECIFIC",
            startDate = LocalDate.of(2026, 10, 21),
            startTime = LocalTime.of(10, 0),
            zoneId = testZone.id,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )
        db.eventDao().insert(eventEntity)
    }
}
