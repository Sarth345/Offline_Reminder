package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryCustomSoundRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTemplateRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryNotificationProfileRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.ConflictSeverity
import com.example.offlinereminder.domain.model.ConflictType
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.RelationshipType
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

class Phase12BUseCasesTest {

    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
        currentZoneId = ZoneId.of("UTC")
    )

    private val eventRepository = InMemoryEventRepository()
    private val categoryRepository = InMemoryCategoryRepository()
    private val eventTypeRepository = InMemoryEventTypeRepository()
    private val profileRepository = InMemoryNotificationProfileRepository()
    private val soundRepository = InMemoryCustomSoundRepository()
    private val templateRepository = InMemoryEventTemplateRepository()
    private val exceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    // Use cases under test
    private val createNotificationProfileUseCase = CreateNotificationProfileUseCase(profileRepository)
    private val updateNotificationProfileUseCase = UpdateNotificationProfileUseCase(profileRepository)
    private val getNotificationProfilesUseCase = GetNotificationProfilesUseCase(profileRepository)
    private val getNotificationProfileUseCase = GetNotificationProfileUseCase(profileRepository)
    private val deleteNotificationProfileUseCase = DeleteNotificationProfileUseCase(profileRepository)
    private val setDefaultNotificationProfileUseCase = SetDefaultNotificationProfileUseCase(profileRepository)

    private val addCustomSoundUseCase = AddCustomSoundUseCase(soundRepository, timeProvider)
    private val updateCustomSoundUseCase = UpdateCustomSoundUseCase(soundRepository)
    private val getCustomSoundsUseCase = GetCustomSoundsUseCase(soundRepository)
    private val getCustomSoundUseCase = GetCustomSoundUseCase(soundRepository)
    private val deleteCustomSoundUseCase = DeleteCustomSoundUseCase(soundRepository)

    private val createEventTemplateUseCase = CreateEventTemplateUseCase(
        templateRepository, categoryRepository, eventTypeRepository, timeProvider
    )
    private val updateEventTemplateUseCase = UpdateEventTemplateUseCase(
        templateRepository, categoryRepository, eventTypeRepository, timeProvider
    )
    private val getEventTemplatesUseCase = GetEventTemplatesUseCase(templateRepository)
    private val getEventTemplateUseCase = GetEventTemplateUseCase(templateRepository)
    private val deleteEventTemplateUseCase = DeleteEventTemplateUseCase(templateRepository)
    private val createEventFromTemplateUseCase = CreateEventFromTemplateUseCase(
        templateRepository, eventRepository, categoryRepository, eventTypeRepository, timeProvider
    )

    private val setEventFavoriteUseCase = SetEventFavoriteUseCase(eventRepository)
    private val toggleEventFavoriteUseCase = ToggleEventFavoriteUseCase(eventRepository)
    private val getFavoriteEventsUseCase = GetFavoriteEventsUseCase(eventRepository)

    private val trashEventUseCase = TrashEventUseCase(timeProvider, eventRepository)
    private val restoreEventUseCase = RestoreEventUseCase(timeProvider, eventRepository)
    private val restoreTrashedEventUseCase = RestoreTrashedEventUseCase(eventRepository)
    private val archiveEventUseCase = ArchiveEventUseCase(timeProvider, eventRepository)
    private val restoreArchivedEventUseCase = RestoreArchivedEventUseCase(eventRepository)
    private val permanentlyDeleteEventUseCase = PermanentlyDeleteEventUseCase(eventRepository)
    private val emptyTrashUseCase = EmptyTrashUseCase(eventRepository)
    private val purgeExpiredTrashUseCase = PurgeExpiredTrashUseCase(eventRepository, timeProvider)

    private val duplicateEventUseCase = DuplicateEventUseCase(eventRepository, timeProvider)
    private val detectEventConflictsUseCase = DetectEventConflictsUseCase(
        eventRepository, recurrenceEngine, exceptionRepository, timeProvider
    )

    private val addEventDependencyUseCase = AddEventDependencyUseCase(eventRepository)
    private val removeEventDependencyUseCase = RemoveEventDependencyUseCase(eventRepository)
    private val getEventDependenciesUseCase = GetEventDependenciesUseCase(eventRepository)
    private val getEventDependentsUseCase = GetEventDependentsUseCase(eventRepository)
    private val addEventRelationshipUseCase = AddEventRelationshipUseCase(eventRepository)
    private val removeEventRelationshipUseCase = RemoveEventRelationshipUseCase(eventRepository)
    private val getEventRelationshipsUseCase = GetEventRelationshipsUseCase(eventRepository)

    @Before
    fun setup() {
        runBlocking {
            categoryRepository.saveCategory(Category(id = "cat-work", name = "Work", color = 0xFF00FF00.toInt()))
            categoryRepository.saveCategory(Category(id = "cat-personal", name = "Personal", color = 0xFFFF0000.toInt()))
            eventTypeRepository.saveEventType(
                EventType(
                    id = "type-meeting",
                    name = "Meeting",
                    key = "meeting",
                    categoryId = "cat-work",
                    createdAt = timeProvider.nowInstant()
                )
            )
        }
    }

    // =========================================================================
    // A. Notification Profiles
    // =========================================================================

    @Test
    fun createNotificationProfile_validData_savesAndReturnsProfile() = runBlocking {
        val result = createNotificationProfileUseCase(
            name = "Loud Alarm Profile",
            deliveryType = ReminderDeliveryType.ALARM,
            sound = SoundReference(SoundSourceType.SYSTEM),
            isDefault = true
        )

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("Loud Alarm Profile", profile.name)
        assertTrue(profile.isDefault)

        val retrieved = getNotificationProfileUseCase(profile.id)
        assertNotNull(retrieved)
        assertEquals("Loud Alarm Profile", retrieved?.name)
    }

    @Test
    fun createNotificationProfile_blankName_failsValidation() = runBlocking {
        val result = createNotificationProfileUseCase(name = "   ")
        assertTrue(result.isFailure)
    }

    @Test
    fun createNotificationProfile_customSoundWithoutId_failsValidation() = runBlocking {
        val result = createNotificationProfileUseCase(
            name = "Custom Without Sound ID",
            sound = SoundReference(SoundSourceType.CUSTOM, customSoundId = null)
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun updateNotificationProfile_updatesFieldsCorrectly() = runBlocking {
        val created = createNotificationProfileUseCase(name = "Original Name").getOrThrow()
        val toUpdate = created.copy(
            name = "Updated Name",
            deliveryType = ReminderDeliveryType.ALARM,
            sound = SoundReference(SoundSourceType.SYSTEM),
            vibrationEnabled = false,
            defaultSnoozeDurationMinutes = 15,
            isDefault = false
        )
        val updateRes = updateNotificationProfileUseCase(toUpdate)

        assertTrue(updateRes.isSuccess)
        val updated = getNotificationProfileUseCase(created.id)!!
        assertEquals("Updated Name", updated.name)
        assertEquals(15, updated.defaultSnoozeDurationMinutes)
        assertFalse(updated.vibrationEnabled)
    }

    @Test
    fun notificationProfile_defaultInvariant_singleDefaultAndNoDanglingOnDelete() = runBlocking {
        // Create Profile A as default
        val pA = createNotificationProfileUseCase(name = "Profile A", isDefault = true).getOrThrow()
        assertTrue(getNotificationProfileUseCase(pA.id)!!.isDefault)
        assertEquals(pA.id, profileRepository.getDefaultProfile()?.id)

        // Create Profile B as default -> clears A
        val pB = createNotificationProfileUseCase(name = "Profile B", isDefault = true).getOrThrow()
        assertFalse(getNotificationProfileUseCase(pA.id)!!.isDefault)
        assertTrue(getNotificationProfileUseCase(pB.id)!!.isDefault)
        assertEquals(pB.id, profileRepository.getDefaultProfile()?.id)

        // Set A back to default -> clears B
        val setRes = setDefaultNotificationProfileUseCase(pA.id)
        assertTrue(setRes.isSuccess)
        assertTrue(getNotificationProfileUseCase(pA.id)!!.isDefault)
        assertFalse(getNotificationProfileUseCase(pB.id)!!.isDefault)
        assertEquals(pA.id, profileRepository.getDefaultProfile()?.id)

        // At most one default exists
        val allProfiles = getNotificationProfilesUseCase().first()
        assertEquals(1, allProfiles.count { it.isDefault })

        // Delete default profile A -> leaves no dangling default reference
        val delRes = deleteNotificationProfileUseCase(pA.id)
        assertTrue(delRes.isSuccess)
        assertNull(getNotificationProfileUseCase(pA.id))
        assertNull(profileRepository.getDefaultProfile())
        assertEquals(0, getNotificationProfilesUseCase().first().count { it.isDefault })
    }

    // =========================================================================
    // B. Custom Sound Metadata
    // =========================================================================

    @Test
    fun addCustomSound_validMetadata_savesCorrectly() = runBlocking {
        val result = addCustomSoundUseCase(
            title = "Gentle Chime",
            fileName = "gentle_chime.ogg",
            mimeType = "audio/ogg",
            fileSizeBytes = 40960L,
            durationMillis = 3500L
        )

        assertTrue(result.isSuccess)
        val sound = result.getOrThrow()
        assertEquals("Gentle Chime", sound.title)
        assertEquals("gentle_chime.ogg", sound.fileName)

        val retrieved = getCustomSoundUseCase(sound.id)
        assertNotNull(retrieved)
        assertEquals(40960L, retrieved?.fileSizeBytes)
    }

    @Test
    fun addCustomSound_invalidSize_fails() = runBlocking {
        val result = addCustomSoundUseCase(
            title = "Zero Byte Sound",
            fileName = "empty.ogg",
            mimeType = "audio/ogg",
            fileSizeBytes = 0L
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun updateCustomSound_updatesTitle() = runBlocking {
        val sound = addCustomSoundUseCase(
            title = "Old Sound Title",
            fileName = "sound.ogg",
            mimeType = "audio/ogg",
            fileSizeBytes = 1000L
        ).getOrThrow()

        val updateRes = updateCustomSoundUseCase(sound.id, "New Sound Title")
        assertTrue(updateRes.isSuccess)
        assertEquals("New Sound Title", getCustomSoundUseCase(sound.id)?.title)
    }

    @Test
    fun deleteCustomSound_removesSound() = runBlocking {
        val sound = addCustomSoundUseCase(
            title = "Sound To Delete",
            fileName = "del.ogg",
            mimeType = "audio/ogg",
            fileSizeBytes = 1000L
        ).getOrThrow()

        val delRes = deleteCustomSoundUseCase(sound.id)
        assertTrue(delRes.isSuccess)
        assertNull(getCustomSoundUseCase(sound.id))
    }

    // =========================================================================
    // C. Event Templates
    // =========================================================================

    @Test
    fun createEventTemplate_valid_savesCorrectly() = runBlocking {
        val template = EventTemplate(
            id = "tmpl-1",
            name = "Weekly Sync Template",
            description = "Standard agenda",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            defaultDuration = Duration.ofMinutes(45),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        val res = createEventTemplateUseCase(template)
        assertTrue(res.isSuccess)

        val retrieved = getEventTemplateUseCase("tmpl-1")
        assertNotNull(retrieved)
        assertEquals("Weekly Sync Template", retrieved?.name)
    }

    @Test
    fun createEventTemplate_invalidCategory_fails() = runBlocking {
        val template = EventTemplate(
            id = "tmpl-invalid",
            name = "Invalid Template",
            eventTypeId = "type-meeting",
            categoryId = "non-existent-cat",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        val res = createEventTemplateUseCase(template)
        assertTrue(res.isFailure)
    }

    @Test
    fun createEventFromTemplate_preservesAllCanonicalFieldsAndIsolatesState() = runBlocking {
        val template = EventTemplate(
            id = "tmpl-canonical",
            name = "Canonical Project Review",
            description = "Bi-weekly sprint review",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            priority = Priority.HIGH,
            defaultDuration = Duration.ofMinutes(45),
            isAllDay = false,
            location = LocationData(placeName = "Conference Room B", addressText = "Floor 2"),
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 2,
                endCondition = RecurrenceEnd.Never
            ),
            notificationSchedules = listOf(
                NotificationSchedule(
                    id = "sched-template-1",
                    offsetDuration = Duration.ofMinutes(15),
                    triggerType = NotificationTriggerType.BEFORE_START
                )
            ),
            checklist = Checklist(
                id = "cl-template",
                title = "Review Checklist",
                items = listOf(
                    ChecklistItem(id = "item-t1", text = "Present demo", isCompleted = true),
                    ChecklistItem(id = "item-t2", text = "Take feedback", isCompleted = false)
                )
            ),
            tagIds = setOf("tag-sprint", "tag-eng"),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        createEventTemplateUseCase(template).getOrThrow()

        val eventStart = LocalDateTime.of(2026, 11, 2, 10, 0)
        val customMeta = mapOf("sprint" to "42", "lead" to "Alice")
        val groups = setOf("group-sprint-reviews")

        val result = createEventFromTemplateUseCase(
            templateId = "tmpl-canonical",
            startDateTime = eventStart,
            titleOverride = "Sprint 42 Review",
            customMetadata = customMeta,
            groupIds = groups
        )

        assertTrue(result.isSuccess)
        val event = result.getOrThrow()

        // 1. Fresh ID and correct template association
        assertNotEquals("tmpl-canonical", event.id)
        assertEquals("tmpl-canonical", event.templateId)

        // 2. Canonical field preservation
        assertEquals("Sprint 42 Review", event.title)
        assertEquals(template.description, event.description)
        assertEquals(Priority.HIGH, event.priority)
        assertEquals("cat-work", event.categoryId)
        assertEquals("type-meeting", event.eventTypeId)
        assertEquals(template.location, event.location)
        assertEquals(template.recurrenceRule, event.recurrenceRule)
        assertEquals(template.tagIds, event.tagIds)
        assertEquals(customMeta, event.customMetadata)
        assertEquals(groups, event.groupIds)

        // 3. Temporal mode and duration calculation
        assertTrue(event.temporal is EventDateTime.DateTimeRange)
        val temporal = event.temporal as EventDateTime.DateTimeRange
        assertEquals(eventStart, temporal.startDateTime)
        assertEquals(eventStart.plusMinutes(45), temporal.endDateTime)

        // 4. Fresh schedule IDs (zero runtime NotificationInstance state)
        assertEquals(1, event.notificationSchedules.size)
        assertNotEquals("sched-template-1", event.notificationSchedules.first().id)
        assertEquals(Duration.ofMinutes(15), event.notificationSchedules.first().offsetDuration)

        // 5. Fresh checklist IDs and all items reset to isCompleted = false
        assertNotNull(event.checklist)
        assertNotEquals("cl-template", event.checklist?.id)
        assertEquals(2, event.checklist?.items?.size)
        assertFalse(event.checklist!!.items.any { it.isCompleted })
        assertFalse(event.checklist!!.items.any { it.id == "item-t1" || it.id == "item-t2" })

        // 6. Lifecycle starts ACTIVE and clean
        assertEquals(EventLifecycleStatus.ACTIVE, event.lifecycleStatus)
        assertNull(event.deletedAt)
        assertFalse(event.isFavorite)

        // 7. Template in repository remains unchanged
        val originalTmpl = getEventTemplateUseCase("tmpl-canonical")!!
        assertEquals("Canonical Project Review", originalTmpl.name)
        assertEquals(1, originalTmpl.checklist?.items?.count { it.isCompleted })
    }

    // =========================================================================
    // D. Favorites
    // =========================================================================

    @Test
    fun favoriteUseCases_setToggleAndQueryFavorites() = runBlocking {
        val event = createBaseEvent("fav-test", "Favorite Candidate", isFavorite = false)
        eventRepository.saveEvent(event)

        assertFalse(eventRepository.getEvent("fav-test")!!.isFavorite)

        // Set favorite true
        val setRes = setEventFavoriteUseCase("fav-test", true)
        assertTrue(setRes.isSuccess)
        assertTrue(eventRepository.getEvent("fav-test")!!.isFavorite)

        // Toggle favorite
        val toggleRes = toggleEventFavoriteUseCase("fav-test")
        assertTrue(toggleRes.isSuccess)
        assertFalse(toggleRes.getOrThrow())
        assertFalse(eventRepository.getEvent("fav-test")!!.isFavorite)

        // Toggle back to true and query favorites
        toggleEventFavoriteUseCase("fav-test")
        val favorites = getFavoriteEventsUseCase().first()
        assertEquals(1, favorites.size)
        assertEquals("fav-test", favorites.first().id)
    }

    // =========================================================================
    // E. Lifecycle (Trash, Archive, Empty, Purge)
    // =========================================================================

    @Test
    fun trashAndRestoreEvent_updatesStatusProperly() = runBlocking {
        val event = createBaseEvent("life-1", "Lifecycle Event")
        eventRepository.saveEvent(event)

        // Move to trash
        val trashRes = trashEventUseCase("life-1")
        assertTrue(trashRes.isSuccess)
        val trashed = eventRepository.getEvent("life-1")!!
        assertEquals(EventLifecycleStatus.TRASHED, trashed.lifecycleStatus)

        // Restore from trash
        val restoreRes = restoreEventUseCase("life-1")
        assertTrue(restoreRes.isSuccess)
        val restored = eventRepository.getEvent("life-1")!!
        assertEquals(EventLifecycleStatus.ACTIVE, restored.lifecycleStatus)
    }

    @Test
    fun archiveAndRestoreEvent_updatesStatusProperly() = runBlocking {
        val event = createBaseEvent("life-2", "Archive Event")
        eventRepository.saveEvent(event)

        // Archive
        val archRes = archiveEventUseCase("life-2")
        assertTrue(archRes.isSuccess)
        assertEquals(EventLifecycleStatus.ARCHIVED, eventRepository.getEvent("life-2")!!.lifecycleStatus)

        // Restore
        val resRes = restoreArchivedEventUseCase("life-2")
        assertTrue(resRes.isSuccess)
        assertEquals(EventLifecycleStatus.ACTIVE, eventRepository.getEvent("life-2")!!.lifecycleStatus)
    }

    @Test
    fun emptyTrash_removesAllTrashedEventsOnly() = runBlocking {
        val e1 = createBaseEvent("active-1", "Active")
        val e2 = createBaseEvent("trashed-1", "Trashed 1").copy(lifecycleStatus = EventLifecycleStatus.TRASHED)
        val e3 = createBaseEvent("trashed-2", "Trashed 2").copy(lifecycleStatus = EventLifecycleStatus.TRASHED)
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)
        eventRepository.saveEvent(e3)

        val emptyRes = emptyTrashUseCase()
        assertTrue(emptyRes.isSuccess)
        assertEquals(2, emptyRes.getOrThrow())

        assertNotNull(eventRepository.getEvent("active-1"))
        assertNull(eventRepository.getEvent("trashed-1"))
        assertNull(eventRepository.getEvent("trashed-2"))
    }

    @Test
    fun purgeExpiredTrash_strictBoundaryAndStatusVerification() = runBlocking {
        val now = timeProvider.nowInstant() // 2026-10-21T10:00:00Z
        val retention = Duration.ofDays(30)
        val exactCutoff = now.minus(retention) // 2026-09-21T10:00:00Z

        // 1. 29 days 23:59 old (1 minute after cutoff) -> RETAINED
        val t29d = createBaseEvent("t-29d", "29d old").copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = exactCutoff.plusSeconds(60)
        )
        // 2. Exactly 30 days old -> PURGED
        val t30dExact = createBaseEvent("t-30d-exact", "30d exact").copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = exactCutoff
        )
        // 3. 30 days + 1 second old -> PURGED
        val t30dPlus1s = createBaseEvent("t-30d-plus", "30d + 1s").copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = exactCutoff.minusSeconds(1)
        )
        // 4. Future deletedAt -> RETAINED
        val tFuture = createBaseEvent("t-future", "Future deletedAt").copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = now.plus(Duration.ofDays(5))
        )
        // 5. ACTIVE event with old timestamp -> RETAINED
        val activeOld = createBaseEvent("active-old", "Active Old").copy(
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            deletedAt = exactCutoff.minusSeconds(100)
        )
        // 6. ARCHIVED event with old timestamp -> RETAINED
        val archivedOld = createBaseEvent("archived-old", "Archived Old").copy(
            lifecycleStatus = EventLifecycleStatus.ARCHIVED,
            deletedAt = exactCutoff.minusSeconds(100)
        )
        // 7. TRASHED event without deletedAt (null) -> RETAINED safely
        val trashedNoDate = createBaseEvent("t-no-date", "Trashed No Date").copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = null
        )

        eventRepository.saveEvent(t29d)
        eventRepository.saveEvent(t30dExact)
        eventRepository.saveEvent(t30dPlus1s)
        eventRepository.saveEvent(tFuture)
        eventRepository.saveEvent(activeOld)
        eventRepository.saveEvent(archivedOld)
        eventRepository.saveEvent(trashedNoDate)

        val purgeRes = purgeExpiredTrashUseCase(retentionDuration = retention)
        assertTrue(purgeRes.isSuccess)
        assertEquals(2, purgeRes.getOrThrow()) // exactly 30d and 30d+1s purged

        assertNotNull("29d 23:59 old must be retained", eventRepository.getEvent("t-29d"))
        assertNull("30d exact must be purged", eventRepository.getEvent("t-30d-exact"))
        assertNull("30d + 1s must be purged", eventRepository.getEvent("t-30d-plus"))
        assertNotNull("Future deletedAt must be retained", eventRepository.getEvent("t-future"))
        assertNotNull("Active event must be retained", eventRepository.getEvent("active-old"))
        assertNotNull("Archived event must be retained", eventRepository.getEvent("archived-old"))
        assertNotNull("Trashed event with null deletedAt must be retained", eventRepository.getEvent("t-no-date"))
    }

    // =========================================================================
    // F. Duplicate Event
    // =========================================================================

    @Test
    fun duplicateEvent_intentionalCloningSemanticsVerified() = runBlocking {
        // Set up source event with full properties, tags, groups, metadata, dependencies & relationships
        val sourceEvent = createBaseEvent(
            id = "src-master",
            title = "Master Task",
            isFavorite = true, // Source is favorite
            notificationSchedules = listOf(
                NotificationSchedule(id = "sched-src-master", offsetDuration = Duration.ofMinutes(15))
            ),
            checklist = Checklist(
                id = "cl-src-master",
                title = "Checklist Title",
                items = listOf(ChecklistItem(id = "item-src-1", text = "Finished Step", isCompleted = true))
            )
        ).copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED, // Source is trashed
            deletedAt = timeProvider.nowInstant(),
            groupIds = setOf("group-eng"),
            tagIds = setOf("tag-urgent"),
            customMetadata = mapOf("client" to "Acme Corp"),
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                endCondition = RecurrenceEnd.Never
            )
        )
        eventRepository.saveEvent(sourceEvent)

        // Add dependency and relationship to source
        val partner = createBaseEvent("partner-task", "Partner Task")
        eventRepository.saveEvent(partner)
        addEventDependencyUseCase("src-master", "partner-task", DependencyType.PREREQUISITE)
        addEventRelationshipUseCase("src-master", "partner-task", RelationshipType.RELATED_TO)

        // Duplicate with start time shift
        val newStart = LocalDateTime.of(2026, 11, 10, 15, 0)
        val dupRes = duplicateEventUseCase(
            sourceEventId = "src-master",
            newTitle = "Duplicated Task",
            newStartDateTime = newStart
        )
        assertTrue(dupRes.isSuccess)
        val dup = dupRes.getOrThrow()

        // 1. Fresh ID
        assertNotEquals("src-master", dup.id)
        assertEquals("Duplicated Task", dup.title)

        // 2. Lifecycle reset to ACTIVE and deletedAt cleared
        assertEquals(EventLifecycleStatus.ACTIVE, dup.lifecycleStatus)
        assertNull(dup.deletedAt)

        // 3. Favorite state reset to false (unpinned)
        assertFalse(dup.isFavorite)

        // 4. Tags, Groups, Custom Metadata, Recurrence preserved
        assertEquals(sourceEvent.tagIds, dup.tagIds)
        assertEquals(sourceEvent.groupIds, dup.groupIds)
        assertEquals(sourceEvent.customMetadata, dup.customMetadata)
        assertEquals(sourceEvent.recurrenceRule, dup.recurrenceRule)

        // 5. Schedules regenerated with fresh IDs (zero runtime notification instances)
        assertEquals(1, dup.notificationSchedules.size)
        assertNotEquals("sched-src-master", dup.notificationSchedules.first().id)

        // 6. Checklist regenerated with fresh IDs and isCompleted reset to false
        assertNotNull(dup.checklist)
        assertNotEquals("cl-src-master", dup.checklist?.id)
        assertEquals(1, dup.checklist?.items?.size)
        assertNotEquals("item-src-1", dup.checklist?.items?.first()?.id)
        assertFalse(dup.checklist!!.items.first().isCompleted)

        // 7. Dependencies and Relationships intentionally OMITTED for duplicate
        val dupDependencies = getEventDependenciesUseCase(dup.id).first()
        assertTrue("Duplicate must not inherit source dependencies", dupDependencies.isEmpty())
        val dupRelationships = getEventRelationshipsUseCase(dup.id).first()
        assertTrue("Duplicate must not inherit source relationships", dupRelationships.isEmpty())

        // 8. Source dependencies and relationships remain intact
        val srcDependencies = getEventDependenciesUseCase("src-master").first()
        assertEquals(1, srcDependencies.size)
        val srcRelationships = getEventRelationshipsUseCase("src-master").first()
        assertEquals(1, srcRelationships.size)
    }

    // =========================================================================
    // G. Conflict Detection
    // =========================================================================

    @Test
    fun detectEventConflicts_directTimedOverlap_reportsHardConflict() = runBlocking {
        // Existing event: 10:00 - 11:30 UTC
        val existing = createBaseEvent(
            id = "existing-1",
            title = "Existing Meeting",
            startDateTime = LocalDateTime.of(2026, 10, 25, 10, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 11, 30)
        )
        eventRepository.saveEvent(existing)

        // Candidate event: 11:00 - 12:00 UTC (overlaps from 11:00 to 11:30)
        val candidate = createBaseEvent(
            id = "candidate-1",
            title = "New Meeting",
            startDateTime = LocalDateTime.of(2026, 10, 25, 11, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 12, 0)
        )

        val conflicts = detectEventConflictsUseCase(candidate)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals(ConflictType.DIRECT_OVERLAP, conflict.type)
        assertEquals(ConflictSeverity.HARD, conflict.severity)
        assertEquals("existing-1", conflict.existingEventId)
    }

    @Test
    fun detectEventConflicts_adjacentEvents_noConflict() = runBlocking {
        // Existing event: 10:00 - 11:00 UTC
        val existing = createBaseEvent(
            id = "existing-adj",
            title = "Morning Sync",
            startDateTime = LocalDateTime.of(2026, 10, 25, 10, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 11, 0)
        )
        eventRepository.saveEvent(existing)

        // Candidate event: 11:00 - 12:00 UTC (strictly adjacent, no overlap)
        val candidate = createBaseEvent(
            id = "candidate-adj",
            title = "Late Morning Sync",
            startDateTime = LocalDateTime.of(2026, 10, 25, 11, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 12, 0)
        )

        val conflicts = detectEventConflictsUseCase(candidate)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun detectEventConflicts_allDayCoexistence_reportsAdvisoryConflict() = runBlocking {
        // Existing all-day event
        val allDayEvent = Event(
            id = "all-day-1",
            title = "Company Holiday",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(allDayEvent)

        // Timed candidate on same day
        val candidate = createBaseEvent(
            id = "candidate-holiday",
            title = "Holiday Work",
            startDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 15, 0)
        )

        val conflicts = detectEventConflictsUseCase(candidate)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals(ConflictType.ALL_DAY_COEXISTENCE, conflict.type)
        assertEquals(ConflictSeverity.ADVISORY, conflict.severity)
    }

    @Test
    fun detectEventConflicts_timezoneAndDstAwareIntervalEvaluation() = runBlocking {
        // Existing event in London (UTC): 14:00 - 16:00 UTC
        val existingLondon = Event(
            id = "london-meeting",
            title = "London Team Meeting",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
                endDateTime = LocalDateTime.of(2026, 10, 25, 16, 0),
                zoneId = ZoneId.of("UTC")
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(existingLondon)

        // Candidate 1 in New York (EDT, UTC-4): 10:30 - 11:30 EDT
        // 10:30 EDT = 14:30 UTC, 11:30 EDT = 15:30 UTC -> Physically overlaps London meeting (14:00-16:00 UTC)
        val candidateNyOverlap = Event(
            id = "ny-meeting-overlap",
            title = "New York Overlapping Call",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 25, 10, 30),
                endDateTime = LocalDateTime.of(2026, 10, 25, 11, 30),
                zoneId = ZoneId.of("America/New_York")
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        val conflictsOverlap = detectEventConflictsUseCase(candidateNyOverlap)
        assertEquals(1, conflictsOverlap.size)
        assertEquals("london-meeting", conflictsOverlap.first().existingEventId)
        assertEquals(ConflictType.DIRECT_OVERLAP, conflictsOverlap.first().type)
        assertEquals(ConflictSeverity.HARD, conflictsOverlap.first().severity)

        // Candidate 2 in New York (EDT, UTC-4): 12:00 - 13:00 EDT
        // 12:00 EDT = 16:00 UTC -> Strictly adjacent to London meeting end (16:00 UTC), NO conflict
        val candidateNyAdjacent = Event(
            id = "ny-meeting-adjacent",
            title = "New York Adjacent Call",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 10, 25, 12, 0),
                endDateTime = LocalDateTime.of(2026, 10, 25, 13, 0),
                zoneId = ZoneId.of("America/New_York")
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        val conflictsAdjacent = detectEventConflictsUseCase(candidateNyAdjacent)
        assertTrue("Adjacent intervals across timezones must not conflict", conflictsAdjacent.isEmpty())

        // Occurrence Exception respect: Exclude the London occurrence -> conflict should vanish
        val initialOcc = existingLondon.createInitialOccurrence()
        val exception = OccurrenceException(
            id = "ex-london-excluded",
            eventId = existingLondon.id,
            originalStartDateTime = initialOcc.originalStartDateTime,
            originalZoneId = initialOcc.zoneId,
            status = OccurrenceStatus.EXCLUDED
        )
        exceptionRepository.saveException(exception)

        val conflictsAfterException = detectEventConflictsUseCase(candidateNyOverlap)
        assertTrue("Excluded occurrence must not trigger conflict", conflictsAfterException.isEmpty())
    }

    @Test
    fun detectEventConflicts_ignoresSelfAndTrashedEvents() = runBlocking {
        val event = createBaseEvent(
            id = "event-self",
            title = "Self Event",
            startDateTime = LocalDateTime.of(2026, 10, 25, 10, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 11, 0)
        )
        eventRepository.saveEvent(event)

        val trashed = createBaseEvent(
            id = "event-trashed",
            title = "Trashed Overlap",
            startDateTime = LocalDateTime.of(2026, 10, 25, 10, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 11, 0)
        ).copy(lifecycleStatus = EventLifecycleStatus.TRASHED)
        eventRepository.saveEvent(trashed)

        // Candidate testing against self
        val conflicts = detectEventConflictsUseCase(event)
        assertTrue(conflicts.isEmpty())
    }

    // =========================================================================
    // H. Dependencies & Relationships
    // =========================================================================

    @Test
    fun dependencies_addRemoveAndQuery() = runBlocking {
        val e1 = createBaseEvent("task-1", "Task 1")
        val e2 = createBaseEvent("task-2", "Task 2")
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        // Self-dependency fails
        val selfRes = addEventDependencyUseCase("task-1", "task-1")
        assertTrue(selfRes.isFailure)

        // Valid dependency
        val addRes = addEventDependencyUseCase("task-1", "task-2", DependencyType.PREREQUISITE)
        assertTrue(addRes.isSuccess)

        val depsOf1 = getEventDependenciesUseCase("task-1").first()
        assertEquals(1, depsOf1.size)
        assertEquals("task-2", depsOf1.first().targetEventId)

        val dependentsOf2 = getEventDependentsUseCase("task-2").first()
        assertEquals(1, dependentsOf2.size)
        assertEquals("task-1", dependentsOf2.first().sourceEventId)

        // Remove dependency
        val removeRes = removeEventDependencyUseCase("task-1", "task-2")
        assertTrue(removeRes.isSuccess)
        assertTrue(getEventDependenciesUseCase("task-1").first().isEmpty())
    }

    @Test
    fun relationships_addRemoveAndQuery() = runBlocking {
        val e1 = createBaseEvent("rel-1", "Event A")
        val e2 = createBaseEvent("rel-2", "Event B")
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        // Self-relationship fails
        val selfRes = addEventRelationshipUseCase("rel-1", "rel-1")
        assertTrue(selfRes.isFailure)

        // Add relationship
        val addRes = addEventRelationshipUseCase("rel-1", "rel-2", RelationshipType.RELATED_TO)
        assertTrue(addRes.isSuccess)

        val relsOf1 = getEventRelationshipsUseCase("rel-1").first()
        assertEquals(1, relsOf1.size)
        val relsOf2 = getEventRelationshipsUseCase("rel-2").first()
        assertEquals(1, relsOf2.size)

        // Remove relationship
        val removeRes = removeEventRelationshipUseCase("rel-1", "rel-2")
        assertTrue(removeRes.isSuccess)
        assertTrue(getEventRelationshipsUseCase("rel-1").first().isEmpty())
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private fun createBaseEvent(
        id: String,
        title: String,
        startDateTime: LocalDateTime = LocalDateTime.of(2026, 10, 25, 10, 0),
        endDateTime: LocalDateTime = LocalDateTime.of(2026, 10, 25, 11, 0),
        isFavorite: Boolean = false,
        notificationSchedules: List<NotificationSchedule> = emptyList(),
        checklist: Checklist? = null
    ): Event = Event(
        id = id,
        title = title,
        description = "Test description",
        eventTypeId = "type-meeting",
        categoryId = "cat-work",
        priority = Priority.NORMAL,
        temporal = EventDateTime.DateTimeRange(
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            zoneId = ZoneId.of("UTC")
        ),
        notificationSchedules = notificationSchedules,
        checklist = checklist,
        lifecycleStatus = EventLifecycleStatus.ACTIVE,
        isFavorite = isFavorite,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )
}
