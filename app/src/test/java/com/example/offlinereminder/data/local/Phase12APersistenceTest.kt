package com.example.offlinereminder.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.CustomSoundEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventTemplateEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationProfileEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.local.entity.RestoreOperationEntity
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.repository.CategoryRepositoryImpl
import com.example.offlinereminder.data.repository.CustomSoundRepositoryImpl
import com.example.offlinereminder.data.repository.EventTemplateRepositoryImpl
import com.example.offlinereminder.data.repository.EventTypeRepositoryImpl
import com.example.offlinereminder.data.repository.NotificationProfileRepositoryImpl
import com.example.offlinereminder.data.repository.OfflineEventRepository
import com.example.offlinereminder.data.repository.RoomBackupRepository
import com.example.offlinereminder.domain.backup.BackupConstants
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.CategoryBackupDto
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.usecase.DeleteCategoryUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventTypeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class Phase12APersistenceTest {

    private lateinit var db: AppDatabase
    private val testInstant = Instant.parse("2026-10-21T10:00:00Z")

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun insertTaxonomy(catId: String = "cat-work", typeId: String = "type-task") {
        db.categoryDao().insert(CategoryEntity(catId, "Work", isCustom = true, sortOrder = 1))
        db.eventTypeDao().insert(EventTypeEntity(typeId, "Task", "work_task", catId, createdAt = testInstant))
    }

    @Test
    fun restoreOperationDao_crudOperations() = runTest {
        val dao = db.restoreOperationDao()
        val now = Instant.parse("2026-10-21T11:00:00Z")

        val op = RestoreOperationEntity(
            operationId = "op-uuid-1",
            status = "COMMITTED",
            backupVersion = 2,
            committedAt = now
        )

        dao.insert(op)

        val retrieved = dao.getById("op-uuid-1")
        assertNotNull(retrieved)
        assertEquals("op-uuid-1", retrieved?.operationId)
        assertEquals("COMMITTED", retrieved?.status)
        assertEquals(2, retrieved?.backupVersion)
        assertEquals(now, retrieved?.committedAt)

        val all = dao.getAll()
        assertEquals(1, all.size)

        dao.deleteById("op-uuid-1")
        assertNull(dao.getById("op-uuid-1"))
    }

    @Test
    fun customSound_repositoryAndDao_persistence() = runTest {
        val repo = CustomSoundRepositoryImpl(db.customSoundDao())
        val sound = CustomSoundMetadata(
            id = "sound-custom-1",
            title = "Morning Chime",
            fileName = "morning_chime.wav",
            mimeType = "audio/wav",
            fileSizeBytes = 40960L,
            durationMillis = 4200L,
            addedAt = testInstant
        )

        val saveResult = repo.saveSound(sound)
        assertTrue(saveResult.isSuccess)

        val fetched = repo.getSoundById("sound-custom-1")
        assertNotNull(fetched)
        assertEquals("Morning Chime", fetched?.title)
        assertEquals("morning_chime.wav", fetched?.fileName)
        assertEquals("audio/wav", fetched?.mimeType)
        assertEquals(40960L, fetched?.fileSizeBytes)
        assertEquals(4200L, fetched?.durationMillis)

        val flowList = repo.getAllSounds().first()
        assertEquals(1, flowList.size)
        assertEquals("Morning Chime", flowList[0].title)

        repo.deleteSound("sound-custom-1")
        assertNull(repo.getSoundById("sound-custom-1"))
    }

    @Test
    fun notificationProfile_repositoryAndDao_persistenceAndDetachment() = runTest {
        val repo = NotificationProfileRepositoryImpl(
            db.notificationProfileDao(),
            db.notificationScheduleDao()
        )
        insertTaxonomy()

        // 1. Create Profile
        val profile = NotificationProfile(
            id = "profile-alarm",
            name = "Emergency Alarm",
            deliveryType = ReminderDeliveryType.ALARM,
            sound = SoundReference(SoundSourceType.DEFAULT),
            vibrationEnabled = true,
            defaultSnoozeDurationMinutes = 5,
            isDefault = true
        )
        repo.saveProfile(profile)

        val fetched = repo.getProfileById("profile-alarm")
        assertNotNull(fetched)
        assertEquals("Emergency Alarm", fetched?.name)
        assertEquals(ReminderDeliveryType.ALARM, fetched?.deliveryType)
        assertEquals(5, fetched?.defaultSnoozeDurationMinutes)
        assertTrue(fetched?.isDefault == true)

        val def = repo.getDefaultProfile()
        assertNotNull(def)
        assertEquals("Emergency Alarm", def?.name)

        // 2. Link to a notification schedule
        db.eventDao().insert(
            EventEntity(
                id = "evt-profile-test",
                title = "Fire Drill",
                eventTypeId = "type-task",
                categoryId = "cat-work",
                temporalType = "DATE_ONLY",
                startDate = LocalDate.of(2026, 10, 22),
                zoneId = ZoneId.systemDefault().id,
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )
        db.notificationScheduleDao().insert(
            NotificationScheduleEntity(
                id = "sched-prof-1",
                eventId = "evt-profile-test",
                offsetDurationMillis = 0L,
                triggerType = NotificationTriggerType.AT_START,
                profileId = "profile-alarm"
            )
        )

        // 3. Delete Profile -> schedule must be detached (profileId = NULL) and NOT deleted
        repo.deleteProfile("profile-alarm")
        assertNull(repo.getProfileById("profile-alarm"))

        val scheduleAfterProfileDelete = db.notificationScheduleDao().getSchedulesForEvent("evt-profile-test")[0]
        assertNull(scheduleAfterProfileDelete.profileId)
        assertEquals("sched-prof-1", scheduleAfterProfileDelete.id)
        assertEquals(ReminderDeliveryType.NOTIFICATION, scheduleAfterProfileDelete.deliveryType)
        assertEquals(SoundSourceType.DEFAULT, scheduleAfterProfileDelete.soundSource)
    }

    @Test
    fun customSound_deletion_detachesFromSchedulesAndProfiles_andPreservesValidDefaultFallback() = runTest {
        insertTaxonomy("cat-audio", "type-audio")
        val customSoundRepo = CustomSoundRepositoryImpl(
            customSoundDao = db.customSoundDao(),
            notificationScheduleDao = db.notificationScheduleDao(),
            notificationProfileDao = db.notificationProfileDao()
        )
        val profileRepo = NotificationProfileRepositoryImpl(
            notificationProfileDao = db.notificationProfileDao(),
            notificationScheduleDao = db.notificationScheduleDao()
        )

        // 1. Create and save custom sound
        val sound = CustomSoundMetadata(
            id = "sound-custom-urgent",
            title = "Urgent Chime",
            fileName = "urgent.ogg",
            mimeType = "audio/ogg",
            fileSizeBytes = 2048L,
            durationMillis = 1500L,
            addedAt = testInstant
        )
        customSoundRepo.saveSound(sound)

        // 2. Create profile referencing custom sound
        val profile = NotificationProfile(
            id = "prof-custom",
            name = "Profile with Custom Sound",
            deliveryType = ReminderDeliveryType.ALARM,
            sound = SoundReference(SoundSourceType.CUSTOM, customSoundId = "sound-custom-urgent"),
            vibrationEnabled = true,
            defaultSnoozeDurationMinutes = 10,
            isDefault = false
        )
        profileRepo.saveProfile(profile)

        // 3. Create schedule referencing custom sound
        db.eventDao().insert(
            EventEntity(
                id = "evt-audio-test",
                title = "Audio Test Event",
                eventTypeId = "type-audio",
                categoryId = "cat-audio",
                temporalType = "DATE_ONLY",
                startDate = LocalDate.of(2026, 10, 22),
                zoneId = ZoneId.systemDefault().id,
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )
        db.notificationScheduleDao().insert(
            NotificationScheduleEntity(
                id = "sched-audio-1",
                eventId = "evt-audio-test",
                offsetDurationMillis = 0L,
                triggerType = NotificationTriggerType.AT_START,
                profileId = "prof-custom",
                deliveryType = ReminderDeliveryType.ALARM,
                soundSource = SoundSourceType.CUSTOM,
                customSoundId = "sound-custom-urgent"
            )
        )

        // 4. Verify both reference custom sound prior to deletion
        val scheduleBefore = db.notificationScheduleDao().getSchedulesForEvent("evt-audio-test")[0].toDomain()
        assertEquals(SoundSourceType.CUSTOM, scheduleBefore.sound.type)
        assertEquals("sound-custom-urgent", scheduleBefore.sound.customSoundId)

        val profileBefore = profileRepo.getProfileById("prof-custom")
        assertEquals(SoundSourceType.CUSTOM, profileBefore?.sound?.type)
        assertEquals("sound-custom-urgent", profileBefore?.sound?.customSoundId)

        // 5. Delete custom sound
        val delResult = customSoundRepo.deleteSound("sound-custom-urgent")
        assertTrue(delResult.isSuccess)
        assertNull(customSoundRepo.getSoundById("sound-custom-urgent"))

        // 6. Verify schedule was safely detached to DEFAULT sound (never CUSTOM with null id)
        val scheduleEntityAfter = db.notificationScheduleDao().getSchedulesForEvent("evt-audio-test")[0]
        assertEquals(SoundSourceType.DEFAULT, scheduleEntityAfter.soundSource)
        assertNull(scheduleEntityAfter.customSoundId)
        val scheduleDomainAfter = scheduleEntityAfter.toDomain()
        assertEquals(SoundSourceType.DEFAULT, scheduleDomainAfter.sound.type)
        assertNull(scheduleDomainAfter.sound.customSoundId)
        // Profile ID and ALARM delivery type remain intact
        assertEquals("prof-custom", scheduleDomainAfter.profileId)
        assertEquals(ReminderDeliveryType.ALARM, scheduleDomainAfter.deliveryType)

        // 7. Verify profile was safely detached to DEFAULT sound
        val profileDomainAfter = profileRepo.getProfileById("prof-custom")
        assertNotNull(profileDomainAfter)
        assertEquals(SoundSourceType.DEFAULT, profileDomainAfter?.sound?.type)
        assertNull(profileDomainAfter?.sound?.customSoundId)
        assertEquals(ReminderDeliveryType.ALARM, profileDomainAfter?.deliveryType)

        // 8. Defensive mapper test: even if raw DB row somehow had CUSTOM + null id, toDomain must return DEFAULT
        val corruptedEntity = scheduleEntityAfter.copy(
            soundSource = SoundSourceType.CUSTOM,
            customSoundId = null
        )
        val defensivelyMapped = corruptedEntity.toDomain()
        assertEquals(SoundSourceType.DEFAULT, defensivelyMapped.sound.type)
        assertNull(defensivelyMapped.sound.customSoundId)
    }

    @Test
    fun eventTemplate_repositoryAndDao_persistenceAndCounting() = runTest {
        insertTaxonomy("cat-study", "type-homework")
        val repo = EventTemplateRepositoryImpl(db.eventTemplateDao())

        val template = EventTemplate(
            id = "tmpl-math",
            name = "Math Homework",
            description = "Daily exercises",
            eventTypeId = "type-homework",
            categoryId = "cat-study",
            priority = Priority.HIGH,
            defaultDuration = Duration.ofMinutes(90),
            isAllDay = false,
            createdAt = testInstant,
            updatedAt = testInstant
        )

        repo.saveTemplate(template)

        val fetched = repo.getTemplateById("tmpl-math")
        assertNotNull(fetched)
        assertEquals("Math Homework", fetched?.name)
        assertEquals("Daily exercises", fetched?.description)
        assertEquals(Priority.HIGH, fetched?.priority)
        assertEquals(Duration.ofMinutes(90), fetched?.defaultDuration)

        val byCategory = repo.countTemplatesByCategory("cat-study")
        assertEquals(1, byCategory)

        val byType = repo.countTemplatesByEventType("type-homework")
        assertEquals(1, byType)

        repo.deleteTemplate("tmpl-math")
        assertNull(repo.getTemplateById("tmpl-math"))
        assertEquals(0, repo.countTemplatesByCategory("cat-study"))
    }

    @Test
    fun notificationSchedule_deliveryAndSoundAttributes_persistThroughEventRepository() = runTest {
        insertTaxonomy()
        val soundRepo = CustomSoundRepositoryImpl(db.customSoundDao())
        val sound = CustomSoundMetadata(
            id = "sound-custom-alarm",
            title = "Loud Siren",
            fileName = "loud_siren.mp3",
            mimeType = "audio/mpeg",
            fileSizeBytes = 51200L,
            durationMillis = 6000L,
            addedAt = testInstant
        )
        soundRepo.saveSound(sound)

        val eventRepo = OfflineEventRepository(db)

        val event = Event(
            id = "evt-alarm-test",
            title = "Critical Medication",
            eventTypeId = "type-task",
            categoryId = "cat-work",
            priority = Priority.CRITICAL,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 22)),
            notificationSchedules = listOf(
                NotificationSchedule(
                    id = "sched-alarm-1",
                    offsetDuration = Duration.ofMinutes(15),
                    triggerType = NotificationTriggerType.BEFORE_START,
                    deliveryType = ReminderDeliveryType.ALARM,
                    sound = SoundReference(
                        type = SoundSourceType.CUSTOM,
                        customSoundId = "sound-custom-alarm"
                    )
                ),
                NotificationSchedule(
                    id = "sched-notif-1",
                    offsetDuration = Duration.ZERO,
                    triggerType = NotificationTriggerType.AT_START,
                    deliveryType = ReminderDeliveryType.NOTIFICATION,
                    sound = SoundReference(SoundSourceType.DEFAULT)
                )
            ),
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val saveResult = eventRepo.saveEvent(event)
        assertTrue(saveResult.isSuccess)

        val loaded = eventRepo.getEvent("evt-alarm-test")
        assertNotNull(loaded)
        assertEquals(2, loaded?.notificationSchedules?.size)

        val alarmSched = loaded?.notificationSchedules?.first { it.id == "sched-alarm-1" }
        assertNotNull(alarmSched)
        assertEquals(ReminderDeliveryType.ALARM, alarmSched?.deliveryType)
        assertEquals(SoundSourceType.CUSTOM, alarmSched?.sound?.type)
        assertEquals("sound-custom-alarm", alarmSched?.sound?.customSoundId)

        val notifSched = loaded?.notificationSchedules?.first { it.id == "sched-notif-1" }
        assertNotNull(notifSched)
        assertEquals(ReminderDeliveryType.NOTIFICATION, notifSched?.deliveryType)
        assertEquals(SoundSourceType.DEFAULT, notifSched?.sound?.type)
    }

    @Test
    fun referentialIntegrity_categoryAndEventTypeDeletion_blockedByTemplates() = runTest {
        insertTaxonomy("cat-parent", "type-in-parent")
        db.categoryDao().insert(
            CategoryEntity(
                id = "cat-for-template",
                name = "Template Category",
                color = 0xFF5555.toInt()
            )
        )
        val templateRepo = EventTemplateRepositoryImpl(db.eventTemplateDao())
        val categoryRepo = CategoryRepositoryImpl(db.categoryDao())
        val eventTypeRepo = EventTypeRepositoryImpl(db.eventTypeDao())
        val eventRepo = OfflineEventRepository(db)

        // Create template referencing category & type
        templateRepo.saveTemplate(
            EventTemplate(
                id = "tmpl-ref",
                name = "Referencing Template",
                eventTypeId = "type-in-parent",
                categoryId = "cat-for-template",
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )

        val deleteCategoryUseCase = DeleteCategoryUseCase(
            categoryRepository = categoryRepo,
            eventRepository = eventRepo,
            eventTypeRepository = eventTypeRepo,
            eventTemplateRepository = templateRepo
        )

        val deleteCatResult = deleteCategoryUseCase("cat-for-template")
        assertTrue(deleteCatResult.isFailure)
        assertTrue((deleteCatResult as Result.Failure).message?.contains("event template") == true)

        val deleteTypeUseCase = DeleteEventTypeUseCase(
            eventTypeRepository = eventTypeRepo,
            eventRepository = eventRepo,
            eventTemplateRepository = templateRepo
        )

        val deleteTypeResult = deleteTypeUseCase("type-in-parent")
        assertTrue(deleteTypeResult.isFailure)
        assertTrue((deleteTypeResult as Result.Failure).message?.contains("event template") == true)

        // Delete template -> now category & type can be deleted successfully
        templateRepo.deleteTemplate("tmpl-ref")
        val deleteCatSuccess = deleteCategoryUseCase("cat-for-template")
        assertTrue(deleteCatSuccess.isSuccess)

        val deleteTypeSuccess = deleteTypeUseCase("type-in-parent")
        assertTrue(deleteTypeSuccess.isSuccess)
    }

    @Test
    fun publicBackup_doesNotSerializeOrWipeRestoreOperations() = runTest {
        val backupRepo = RoomBackupRepository(
            database = db,
            timeProvider = TestTimeProvider(testInstant, ZoneId.of("UTC"))
        )

        // 1. Insert restore_operations entry
        db.restoreOperationDao().insert(
            RestoreOperationEntity(
                operationId = "internal-op-1",
                status = "COMMITTED",
                backupVersion = 2,
                committedAt = testInstant
            )
        )

        // 2. Insert some public category data
        db.categoryDao().insert(CategoryEntity("cat-1", "Personal", isCustom = true, sortOrder = 1))

        // 3. Export backup payload
        val exportResult = backupRepo.createBackupPayload()
        assertTrue(exportResult.isSuccess)
        val payload = exportResult.getOrThrow()

        // Verify public backup does NOT contain restore_operations metadata
        assertEquals(1, payload.categories.size)

        // 4. Perform public backup restore with REPLACE_ALL strategy
        val restoreResult = backupRepo.restoreBackupPayload(
            payload = BackupPayload(
                backupVersion = BackupConstants.CURRENT_BACKUP_VERSION,
                appVersion = BackupConstants.DEFAULT_APP_VERSION,
                exportedAt = testInstant.toString(),
                categories = listOf(
                    CategoryBackupDto("cat-restored", "Restored Cat", isCustom = true, sortOrder = 1)
                )
            ),
            strategy = RestoreStrategy.REPLACE_ALL
        )
        assertTrue(restoreResult.isSuccess)

        // 5. Verify restore_operations table was NOT wiped by public restore
        val internalOp = db.restoreOperationDao().getById("internal-op-1")
        assertNotNull(internalOp)
        assertEquals("internal-op-1", internalOp?.operationId)
        assertEquals("COMMITTED", internalOp?.status)

        // Verify public data was replaced
        assertNull(db.categoryDao().getById("cat-1"))
        assertNotNull(db.categoryDao().getById("cat-restored"))

        // 6. Verify an authoritative recovery marker can be written inside a Room transaction
        db.runInTransaction {
            kotlinx.coroutines.runBlocking {
                db.restoreOperationDao().insert(
                    RestoreOperationEntity(
                        operationId = "internal-op-post-restore",
                        status = "COMMITTED",
                        backupVersion = 2,
                        committedAt = testInstant
                    )
                )
            }
        }
        val postRestoreOp = db.restoreOperationDao().getById("internal-op-post-restore")
        assertNotNull(postRestoreOp)
        assertEquals("COMMITTED", postRestoreOp?.status)
    }
}
