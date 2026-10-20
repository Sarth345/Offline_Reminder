package com.example.offlinereminder.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.TagEntity
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventDependency
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventRelationship
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.RelationshipType
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class OfflineEventRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineEventRepository
    private lateinit var timeProvider: TestTimeProvider

    private val fixedInstant = Instant.parse("2026-10-20T10:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        timeProvider = TestTimeProvider(currentInstant = fixedInstant)
        repository = OfflineEventRepository(database = database, timeProvider = timeProvider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedCategoryAndType(
        categoryId: String = "cat-personal",
        eventTypeId: String = "type-general"
    ) {
        database.categoryDao().insert(
            CategoryEntity(id = categoryId, name = "Personal", color = 1, icon = "icon", sortOrder = 0)
        )
        database.eventTypeDao().insert(
            EventTypeEntity(
                id = eventTypeId,
                name = "General",
                key = "general_reminder",
                categoryId = categoryId,
                createdAt = fixedInstant
            )
        )
    }

    @Test
    fun eventRoundTrip_preservesAllTemporalVariantsAndMetadata() = runTest {
        seedCategoryAndType()

        // 1. DateOnly event
        val dateOnlyEvent = Event(
            id = "evt-date-only",
            title = "Birthday Celebration",
            description = "Family gathering",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            priority = Priority.HIGH,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 25)),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        val saveDateOnlyResult = repository.saveEvent(dateOnlyEvent)
        assertTrue(saveDateOnlyResult is Result.Success)

        val retrievedDateOnly = repository.getEvent("evt-date-only")
        assertNotNull(retrievedDateOnly)
        assertEquals("Birthday Celebration", retrievedDateOnly?.title)
        assertTrue(retrievedDateOnly?.temporal is EventDateTime.DateOnly)
        assertEquals(LocalDate.of(2026, 11, 25), retrievedDateOnly?.temporal?.startDate)
        assertTrue(retrievedDateOnly?.temporal?.isAllDay == true)

        // 2. DateTime event with explicit timezone (Asia/Tokyo)
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val dateTimeEvent = Event(
            id = "evt-dt-tokyo",
            title = "Tokyo Sync",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateTime(
                dateTime = LocalDateTime.of(2026, 11, 26, 14, 0),
                zoneId = tokyoZone
            ),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(dateTimeEvent)
        val retrievedDt = repository.getEvent("evt-dt-tokyo")
        assertTrue(retrievedDt?.temporal is EventDateTime.DateTime)
        val dt = retrievedDt?.temporal as EventDateTime.DateTime
        assertEquals(LocalDateTime.of(2026, 11, 26, 14, 0), dt.dateTime)
        assertEquals(tokyoZone, dt.zoneId)

        // 3. DateRange event
        val dateRangeEvent = Event(
            id = "evt-date-range",
            title = "Conference Days",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateRange(
                startDate = LocalDate.of(2026, 12, 1),
                endDate = LocalDate.of(2026, 12, 5)
            ),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(dateRangeEvent)
        val retrievedRange = repository.getEvent("evt-date-range")
        assertTrue(retrievedRange?.temporal is EventDateTime.DateRange)
        val range = retrievedRange?.temporal as EventDateTime.DateRange
        assertEquals(LocalDate.of(2026, 12, 1), range.startDate)
        assertEquals(LocalDate.of(2026, 12, 5), range.endDate)

        // 4. DateTimeRange event with America/New_York
        val nyZone = ZoneId.of("America/New_York")
        val dtRangeEvent = Event(
            id = "evt-dt-range-ny",
            title = "Hackathon",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 12, 10, 9, 30),
                endDateTime = LocalDateTime.of(2026, 12, 11, 18, 0),
                zoneId = nyZone
            ),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(dtRangeEvent)
        val retrievedDtRange = repository.getEvent("evt-dt-range-ny")
        assertTrue(retrievedDtRange?.temporal is EventDateTime.DateTimeRange)
        val dtRange = retrievedDtRange?.temporal as EventDateTime.DateTimeRange
        assertEquals(LocalDateTime.of(2026, 12, 10, 9, 30), dtRange.startDateTime)
        assertEquals(LocalDateTime.of(2026, 12, 11, 18, 0), dtRange.endDateTime)
        assertEquals(nyZone, dtRange.zoneId)
    }

    @Test
    fun completeAggregate_withChecklistSchedulesAttachmentsGroupsAndTags_persistsAndLoads() = runTest {
        seedCategoryAndType()

        // Seed group and tag
        database.eventGroupDao().insert(
            EventGroupEntity(id = "grp-1", name = "Trip Planning", createdAt = fixedInstant, updatedAt = fixedInstant)
        )
        database.tagDao().insert(
            TagEntity(id = "tag-1", name = "Important", normalizedName = "important")
        )

        val fullEvent = Event(
            id = "evt-full-aggregate",
            title = "Expedition",
            description = "Himalayan Expedition",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            priority = Priority.CRITICAL,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 12, 15, 6, 0)),
            location = LocationData(
                placeName = "Base Camp",
                addressText = "Manali, HP",
                building = "Hut 4",
                room = "Bunk 2",
                latitude = 32.2432,
                longitude = 77.1892
            ),
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.YEARLY,
                interval = 1,
                monthOfYear = 12,
                dayOfMonth = 15,
                endCondition = RecurrenceEnd.Count(5),
                excludedDates = setOf(LocalDate.of(2027, 12, 15))
            ),
            notificationSchedules = listOf(
                NotificationSchedule.beforeStart(Duration.ofHours(24)),
                NotificationSchedule.atStart()
            ),
            checklist = Checklist(
                id = "chk-full",
                title = "Gear Checklist",
                items = listOf(
                    ChecklistItem(id = "item-1", text = "Thermal wear", isCompleted = true, sortOrder = 0),
                    ChecklistItem(id = "item-2", text = "Crampons", isCompleted = false, sortOrder = 1)
                )
            ),
            isFavorite = true,
            groupIds = setOf("grp-1"),
            tagIds = setOf("tag-1"),
            customMetadata = mapOf("permit_number" to "HP-2026-99", "guide" to "Tenzing"),
            attachments = listOf(
                AttachmentMetadata(
                    id = "att-map",
                    localUri = "content://media/map.png",
                    fileName = "trail_map.png",
                    mimeType = "image/png",
                    fileSizeBytes = 1048576L,
                    createdAt = fixedInstant
                )
            ),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )

        val saveResult = repository.saveEvent(fullEvent)
        assertTrue(saveResult is Result.Success)

        val retrieved = repository.getEvent("evt-full-aggregate")
        assertNotNull(retrieved)
        assertEquals("Expedition", retrieved?.title)
        assertEquals(Priority.CRITICAL, retrieved?.priority)
        assertTrue(retrieved?.isFavorite == true)

        // Verify Location
        assertEquals("Base Camp", retrieved?.location?.placeName)
        assertEquals("Hut 4", retrieved?.location?.building)
        assertEquals(32.2432, retrieved?.location?.latitude ?: 0.0, 0.0001)

        // Verify Recurrence
        val r = retrieved?.recurrenceRule
        assertNotNull(r)
        assertEquals(RecurrenceFrequency.YEARLY, r?.frequency)
        assertEquals(12, r?.monthOfYear)
        assertEquals(15, r?.dayOfMonth)
        assertTrue(r?.endCondition is RecurrenceEnd.Count)
        assertEquals(5, (r?.endCondition as RecurrenceEnd.Count).maxOccurrences)
        assertTrue(r.excludedDates.contains(LocalDate.of(2027, 12, 15)))

        // Verify Notifications
        assertEquals(2, retrieved?.notificationSchedules?.size)
        assertEquals(NotificationTriggerType.BEFORE_START, retrieved?.notificationSchedules?.get(0)?.triggerType)
        assertEquals(Duration.ofHours(24), retrieved?.notificationSchedules?.get(0)?.offsetDuration)

        // Verify Checklist
        val chk = retrieved?.checklist
        assertNotNull(chk)
        assertEquals(2, chk?.items?.size)
        assertEquals("Thermal wear", chk?.items?.get(0)?.text)
        assertTrue(chk?.items?.get(0)?.isCompleted == true)
        assertEquals("Crampons", chk?.items?.get(1)?.text)
        assertTrue(chk?.items?.get(1)?.isCompleted == false)

        // Verify Groups and Tags
        assertEquals(setOf("grp-1"), retrieved?.groupIds)
        assertEquals(setOf("tag-1"), retrieved?.tagIds)

        // Verify Attachments
        assertEquals(1, retrieved?.attachments?.size)
        assertEquals("trail_map.png", retrieved?.attachments?.get(0)?.fileName)

        // Verify Custom Metadata
        assertEquals("HP-2026-99", retrieved?.customMetadata?.get("permit_number"))
        assertEquals("Tenzing", retrieved?.customMetadata?.get("guide"))
    }

    @Test
    fun aggregateUpdate_atomicallySynchronizesChildRecordsWithoutOrphans() = runTest {
        seedCategoryAndType()

        val initialEvent = Event(
            id = "evt-sync-test",
            title = "Initial Title",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 20)),
            notificationSchedules = listOf(NotificationSchedule.atStart()),
            checklist = Checklist(
                id = "chk-sync",
                title = "Initial Tasks",
                items = listOf(ChecklistItem(id = "old-item", text = "Old Task", isCompleted = false))
            ),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(initialEvent)

        // Update event with new checklist items and updated notification schedule
        val updatedEvent = initialEvent.copy(
            title = "Updated Title",
            notificationSchedules = listOf(
                NotificationSchedule.beforeStart(Duration.ofMinutes(15)),
                NotificationSchedule.atStart()
            ),
            checklist = Checklist(
                id = "chk-sync",
                title = "Updated Tasks",
                items = listOf(
                    ChecklistItem(id = "new-item-1", text = "New Task 1", isCompleted = true, sortOrder = 0),
                    ChecklistItem(id = "new-item-2", text = "New Task 2", isCompleted = false, sortOrder = 1)
                )
            ),
            updatedAt = fixedInstant.plusSeconds(3600)
        )
        repository.saveEvent(updatedEvent)

        val retrieved = repository.getEvent("evt-sync-test")
        assertEquals("Updated Title", retrieved?.title)
        assertEquals(2, retrieved?.notificationSchedules?.size)
        assertEquals(2, retrieved?.checklist?.items?.size)
        assertTrue(retrieved?.checklist?.items?.none { it.id == "old-item" } == true)
        assertTrue(retrieved?.checklist?.items?.any { it.id == "new-item-1" && it.isCompleted } == true)
    }

    @Test
    fun lifecycleMutations_moveToTrashRestoreAndFavorite() = runTest {
        seedCategoryAndType()

        val event = Event(
            id = "evt-lifecycle",
            title = "Test Lifecycle",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 20)),
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(event)

        // Move to trash
        val trashTime = Instant.parse("2026-10-20T12:00:00Z")
        timeProvider.currentInstant = trashTime
        val trashResult = repository.moveToTrash("evt-lifecycle")
        assertTrue(trashResult is Result.Success)

        val trashedEvent = repository.getEvent("evt-lifecycle")
        assertEquals(EventLifecycleStatus.TRASHED, trashedEvent?.lifecycleStatus)
        assertEquals(trashTime, trashedEvent?.deletedAt)

        // Restore from trash
        val restoreTime = Instant.parse("2026-10-20T14:00:00Z")
        timeProvider.currentInstant = restoreTime
        val restoreResult = repository.restoreFromTrash("evt-lifecycle")
        assertTrue(restoreResult is Result.Success)

        val restoredEvent = repository.getEvent("evt-lifecycle")
        assertEquals(EventLifecycleStatus.ACTIVE, restoredEvent?.lifecycleStatus)
        assertNull(restoredEvent?.deletedAt)

        // Set favorite
        repository.setFavorite("evt-lifecycle", true)
        val favoriteEvent = repository.getEvent("evt-lifecycle")
        assertTrue(favoriteEvent?.isFavorite == true)
    }

    @Test
    fun eventDeletion_cascadesCompletely() = runTest {
        seedCategoryAndType()

        val event = Event(
            id = "evt-delete-test",
            title = "Delete Me",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 20)),
            notificationSchedules = listOf(NotificationSchedule.atStart()),
            checklist = Checklist(
                id = "chk-del",
                items = listOf(ChecklistItem(id = "item-del", text = "Item"))
            ),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(event)

        val deleteResult = repository.deleteEvent("evt-delete-test")
        assertTrue(deleteResult is Result.Success)

        assertNull(repository.getEvent("evt-delete-test"))
        assertTrue(database.notificationScheduleDao().getSchedulesForEvent("evt-delete-test").isEmpty())
        assertNull(database.checklistDao().getChecklistWithItemsByEventId("evt-delete-test"))
    }

    @Test
    fun flowObservation_emitsLiveUpdates() = runTest {
        seedCategoryAndType()

        val event = Event(
            id = "evt-flow-test",
            title = "Original Stream Item",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 1)),
            isFavorite = true,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(event)

        val observedById = repository.getEventById("evt-flow-test").first()
        assertEquals("Original Stream Item", observedById?.title)

        val activeList = repository.getAllActiveEvents().first()
        assertEquals(1, activeList.size)
        assertEquals("evt-flow-test", activeList[0].id)

        val favorites = repository.getFavoriteEvents().first()
        assertEquals(1, favorites.size)
        assertEquals("evt-flow-test", favorites[0].id)

        val inRange = repository.getEventsInDateRange(
            startDate = LocalDate.of(2026, 10, 25),
            endDate = LocalDate.of(2026, 11, 5)
        ).first()
        assertEquals(1, inRange.size)
        assertEquals("evt-flow-test", inRange[0].id)
    }

    @Test
    fun dependencyAndRelationship_persistence() = runTest {
        seedCategoryAndType()

        val eventA = Event(
            id = "evt-a",
            title = "Pack Bags",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 1)),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        val eventB = Event(
            id = "evt-b",
            title = "Airport Check-in",
            eventTypeId = "type-general",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 2)),
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        repository.saveEvent(eventA)
        repository.saveEvent(eventB)

        // 1. Dependency: Pack Bags -> Airport Check-in (BLOCKS)
        val dep = EventDependency(sourceEventId = "evt-a", targetEventId = "evt-b", type = DependencyType.BLOCKS)
        val addDepResult = repository.addDependency(dep)
        assertTrue(addDepResult is Result.Success)

        val depsOfA = repository.getDependenciesForEvent("evt-a").first()
        assertEquals(1, depsOfA.size)
        assertEquals("evt-b", depsOfA[0].targetEventId)
        assertEquals(DependencyType.BLOCKS, depsOfA[0].type)

        val dependentsOfB = repository.getDependentsOfEvent("evt-b").first()
        assertEquals(1, dependentsOfB.size)
        assertEquals("evt-a", dependentsOfB[0].sourceEventId)

        repository.removeDependency("evt-a", "evt-b")
        assertTrue(repository.getDependenciesForEvent("evt-a").first().isEmpty())

        // 2. Relationship: Event A -> Event B (PREPARATION_FOR)
        val rel = EventRelationship(sourceEventId = "evt-a", targetEventId = "evt-b", type = RelationshipType.PREPARATION_FOR)
        val addRelResult = repository.addRelationship(rel)
        assertTrue(addRelResult is Result.Success)

        val relsOfA = repository.getRelationshipsForEvent("evt-a").first()
        assertEquals(1, relsOfA.size)
        assertEquals("evt-b", relsOfA[0].targetEventId)
        assertEquals(RelationshipType.PREPARATION_FOR, relsOfA[0].type)

        repository.removeRelationship("evt-a", "evt-b")
        assertTrue(repository.getRelationshipsForEvent("evt-a").first().isEmpty())
    }
}
