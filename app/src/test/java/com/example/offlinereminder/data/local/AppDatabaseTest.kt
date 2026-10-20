package com.example.offlinereminder.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.ChecklistItemEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.data.local.entity.EventTagCrossRef
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationInstanceEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.local.entity.OccurrenceExceptionEntity
import com.example.offlinereminder.data.local.entity.TagEntity
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
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
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")

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

    private suspend fun insertDefaultCategoryAndType(
        categoryId: String = "cat-work",
        eventTypeId: String = "type-meeting"
    ) {
        db.categoryDao().insert(
            CategoryEntity(
                id = categoryId,
                name = "Work",
                color = 0xFF0000FF.toInt(),
                icon = "work_icon",
                sortOrder = 1
            )
        )
        db.eventTypeDao().insert(
            EventTypeEntity(
                id = eventTypeId,
                name = "Meeting",
                key = "work_meeting",
                categoryId = categoryId,
                aliases = listOf("sync", "catchup"),
                createdAt = testInstant
            )
        )
    }

    @Test
    fun categoryAndEventType_insertAndQuery() = runTest {
        insertDefaultCategoryAndType()

        val category = db.categoryDao().getById("cat-work")
        assertNotNull(category)
        assertEquals("Work", category?.name)

        val eventType = db.eventTypeDao().getByKey("work_meeting")
        assertNotNull(eventType)
        assertEquals("Meeting", eventType?.name)
        assertEquals(listOf("sync", "catchup"), eventType?.aliases)

        val searchedTypes = db.eventTypeDao().searchByNameOrAliasFlow("catchup").first()
        assertEquals(1, searchedTypes.size)
        assertEquals("Meeting", searchedTypes[0].name)
    }

    @Test
    fun eventPersistence_crudAndDetailsQuery() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-1",
            title = "Quarterly Business Review",
            description = "Q3 financial and engineering review",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            priority = Priority.HIGH,
            temporalType = "DATE_TIME",
            startDate = LocalDate.of(2026, 11, 15),
            startTime = LocalTime.of(10, 0),
            endDate = LocalDate.of(2026, 11, 15),
            endTime = LocalTime.of(12, 0),
            zoneId = ZoneId.systemDefault().id,
            locationPlaceName = "Main Conference Room",
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        val retrieved = db.eventDao().getById("evt-1")
        assertNotNull(retrieved)
        assertEquals("Quarterly Business Review", retrieved?.title)
        assertEquals(Priority.HIGH, retrieved?.priority)

        val withDetails = db.eventDao().getEventWithDetailsById("evt-1")
        assertNotNull(withDetails)
        assertEquals("Work", withDetails?.category?.name)
        assertEquals("Meeting", withDetails?.eventType?.name)
        assertEquals("Main Conference Room", withDetails?.event?.locationPlaceName)
    }

    @Test
    fun groupMembership_crossRefAndIndependentDeletion() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-exam",
            title = "DBMS Exam",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporalType = "DATE_ONLY",
            startDate = LocalDate.of(2026, 11, 20),
            zoneId = ZoneId.systemDefault().id,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        val collegeGroup = EventGroupEntity(
            id = "grp-college",
            name = "College",
            description = "Semester 5 courses",
            createdAt = testInstant,
            updatedAt = testInstant
        )
        val examsGroup = EventGroupEntity(
            id = "grp-exams",
            name = "Exams",
            description = "All upcoming finals",
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventGroupDao().insert(collegeGroup)
        db.eventGroupDao().insert(examsGroup)

        db.eventGroupDao().insertCrossRef(EventGroupCrossRef("evt-exam", "grp-college"))
        db.eventGroupDao().insertCrossRef(EventGroupCrossRef("evt-exam", "grp-exams"))

        val eventGroupIds = db.eventGroupDao().getGroupIdsForEvent("evt-exam")
        assertEquals(2, eventGroupIds.size)
        assertTrue(eventGroupIds.contains("grp-college"))
        assertTrue(eventGroupIds.contains("grp-exams"))

        // Deleting a group must NOT delete the event (Specification rule!)
        db.eventGroupDao().deleteById("grp-exams")

        val eventAfterGroupDelete = db.eventDao().getById("evt-exam")
        assertNotNull(eventAfterGroupDelete)
        assertEquals("DBMS Exam", eventAfterGroupDelete?.title)

        val remainingGroupIds = db.eventGroupDao().getGroupIdsForEvent("evt-exam")
        assertEquals(1, remainingGroupIds.size)
        assertEquals("grp-college", remainingGroupIds[0])
    }

    @Test
    fun tagMembership_crossRefPersistence() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-tag-test",
            title = "File Taxes",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporalType = "DATE_ONLY",
            startDate = LocalDate.of(2026, 12, 1),
            zoneId = ZoneId.systemDefault().id,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        val tag = TagEntity(
            id = "tag-urgent",
            name = "Urgent",
            normalizedName = "urgent"
        )
        db.tagDao().insert(tag)
        db.tagDao().insertCrossRef(EventTagCrossRef("evt-tag-test", "tag-urgent"))

        val tagsForEvent = db.tagDao().getTagsForEventFlow("evt-tag-test").first()
        assertEquals(1, tagsForEvent.size)
        assertEquals("Urgent", tagsForEvent[0].name)
    }

    @Test
    fun checklistAndItems_persistenceAndCascade() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-trip",
            title = "Mountain Trek",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporalType = "DATE_ONLY",
            startDate = LocalDate.of(2026, 12, 10),
            zoneId = ZoneId.systemDefault().id,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        val checklist = ChecklistEntity(id = "chk-trek", eventId = "evt-trip", title = "Gear")
        db.checklistDao().insertChecklist(checklist)

        val items = listOf(
            ChecklistItemEntity(id = "chk-item-1", checklistId = "chk-trek", text = "Tent", isCompleted = false, sortOrder = 0),
            ChecklistItemEntity(id = "chk-item-2", checklistId = "chk-trek", text = "Boots", isCompleted = true, sortOrder = 1)
        )
        db.checklistDao().insertItems(items)

        val retrieved = db.checklistDao().getChecklistWithItemsByEventId("evt-trip")
        assertNotNull(retrieved)
        assertEquals("Gear", retrieved?.checklist?.title)
        assertEquals(2, retrieved?.items?.size)

        db.checklistDao().setItemCompleted("chk-item-1", true)
        val updated = db.checklistDao().getChecklistWithItemsByEventId("evt-trip")
        assertTrue(updated?.items?.find { it.id == "chk-item-1" }?.isCompleted == true)
    }

    @Test
    fun notificationSchedulesAndInstances_persistenceAndSnooze() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-notif",
            title = "Doctor Appointment",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporalType = "DATE_TIME",
            startDate = LocalDate.of(2026, 11, 1),
            startTime = LocalTime.of(9, 0),
            zoneId = ZoneId.systemDefault().id,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        val schedule = NotificationScheduleEntity(
            id = "sched-1",
            eventId = "evt-notif",
            offsetDurationMillis = 30 * 60 * 1000L,
            triggerType = NotificationTriggerType.BEFORE_START
        )
        db.notificationScheduleDao().insert(schedule)

        val instance = NotificationInstanceEntity(
            id = "inst-1",
            eventId = "evt-notif",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 11, 1, 9, 0),
            occurrenceOriginalZoneId = ZoneId.systemDefault().id,
            scheduleId = "sched-1",
            triggerEpochMillis = 1793522400000L,
            status = "SCHEDULED",
            requestCode = 1001
        )
        db.notificationInstanceDao().insert(instance)

        val scheduledList = db.notificationInstanceDao().getAllScheduledFlow().first()
        assertEquals(1, scheduledList.size)
        assertEquals("inst-1", scheduledList[0].id)

        // Test snooze
        val newSnoozeTime = Instant.parse("2026-11-01T08:45:00Z")
        db.notificationInstanceDao().snooze("inst-1", newSnoozeTime, newSnoozeTime.toEpochMilli())

        val snoozed = db.notificationInstanceDao().getById("inst-1")
        assertNotNull(snoozed)
        assertEquals("SNOOZED", snoozed?.status)
        assertEquals(1, snoozed?.snoozeCount)
        assertEquals(newSnoozeTime.toEpochMilli(), snoozed?.triggerEpochMillis)
    }

    @Test
    fun occurrenceException_persistenceAndOccurrenceIdLookup() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-recur-meeting",
            title = "Weekly Standup",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporalType = "DATE_TIME",
            startDate = LocalDate.of(2026, 10, 1),
            startTime = LocalTime.of(10, 0),
            zoneId = "UTC",
            hasRecurrence = true,
            recurrenceFrequency = RecurrenceFrequency.WEEKLY,
            recurrenceInterval = 1,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        val originalLdt = LocalDateTime.of(2026, 10, 8, 10, 0)
        val rescheduledLdt = LocalDateTime.of(2026, 10, 9, 14, 0)
        val occurrenceId = "${event.id}_${originalLdt}_UTC"

        val exception = OccurrenceExceptionEntity(
            id = "exc-standup-1",
            eventId = "evt-recur-meeting",
            originalStartDateTime = originalLdt,
            originalZoneId = "UTC",
            occurrenceId = occurrenceId,
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = rescheduledLdt,
            rescheduledZoneId = "UTC",
            titleOverride = "Shifted Standup"
        )
        db.occurrenceExceptionDao().insert(exception)

        val retrieved = db.occurrenceExceptionDao().getByOccurrenceId(occurrenceId)
        assertNotNull(retrieved)
        assertEquals(OccurrenceStatus.RESCHEDULED, retrieved?.status)
        assertEquals(rescheduledLdt, retrieved?.rescheduledStartDateTime)
        assertEquals("Shifted Standup", retrieved?.titleOverride)
    }

    @Test
    fun eventDeletion_cascadesToChildTables() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-cascade-test",
            title = "Temporary Workshop",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporalType = "DATE_ONLY",
            startDate = LocalDate.of(2026, 10, 30),
            zoneId = ZoneId.systemDefault().id,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        db.notificationScheduleDao().insert(
            NotificationScheduleEntity(
                id = "sched-casc",
                eventId = "evt-cascade-test",
                offsetDurationMillis = 0L,
                triggerType = NotificationTriggerType.AT_START
            )
        )
        db.checklistDao().insertChecklist(
            ChecklistEntity(id = "chk-casc", eventId = "evt-cascade-test", title = "Tasks")
        )
        db.checklistDao().insertItem(
            ChecklistItemEntity(id = "item-casc", checklistId = "chk-casc", text = "Slide deck")
        )

        // Delete parent event
        db.eventDao().deleteById("evt-cascade-test")

        assertNull(db.eventDao().getById("evt-cascade-test"))
        assertTrue(db.notificationScheduleDao().getSchedulesForEvent("evt-cascade-test").isEmpty())
        assertNull(db.checklistDao().getChecklistWithItemsByEventId("evt-cascade-test"))
    }

    @Test
    fun eventLifecycleStatus_moveAndRestoreFromTrash() = runTest {
        insertDefaultCategoryAndType()

        val event = EventEntity(
            id = "evt-trash-test",
            title = "Old Draft",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporalType = "DATE_ONLY",
            startDate = LocalDate.of(2026, 10, 20),
            zoneId = ZoneId.systemDefault().id,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.eventDao().insert(event)

        val trashTime = Instant.parse("2026-10-20T12:00:00Z")
        db.eventDao().moveToTrash("evt-trash-test", trashTime, trashTime)

        var e = db.eventDao().getById("evt-trash-test")
        assertEquals(EventLifecycleStatus.TRASHED, e?.lifecycleStatus)
        assertEquals(trashTime, e?.deletedAt)

        val restoreTime = Instant.parse("2026-10-20T13:00:00Z")
        db.eventDao().restoreFromTrash("evt-trash-test", restoreTime)

        e = db.eventDao().getById("evt-trash-test")
        assertEquals(EventLifecycleStatus.ACTIVE, e?.lifecycleStatus)
        assertNull(e?.deletedAt)
    }

    @Test
    fun calendarProjectionQuery_filtersOneTimeAndRecurring() = runTest {
        insertDefaultCategoryAndType()

        // One-time event in range
        db.eventDao().insert(
            EventEntity(
                id = "evt-in-range",
                title = "In Range Event",
                eventTypeId = "type-meeting",
                categoryId = "cat-work",
                temporalType = "DATE_ONLY",
                startDate = LocalDate.of(2026, 11, 10),
                zoneId = ZoneId.systemDefault().id,
                hasRecurrence = false,
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )

        // One-time event outside range
        db.eventDao().insert(
            EventEntity(
                id = "evt-out-of-range",
                title = "Out of Range Event",
                eventTypeId = "type-meeting",
                categoryId = "cat-work",
                temporalType = "DATE_ONLY",
                startDate = LocalDate.of(2026, 12, 10),
                zoneId = ZoneId.systemDefault().id,
                hasRecurrence = false,
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )

        // Recurring event starting before range
        db.eventDao().insert(
            EventEntity(
                id = "evt-recurring-active",
                title = "Weekly Gym",
                eventTypeId = "type-meeting",
                categoryId = "cat-work",
                temporalType = "DATE_ONLY",
                startDate = LocalDate.of(2026, 1, 1),
                zoneId = ZoneId.systemDefault().id,
                hasRecurrence = true,
                recurrenceFrequency = RecurrenceFrequency.WEEKLY,
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )

        val results = db.eventDao().getEventsForCalendarProjection(
            rangeStart = LocalDate.of(2026, 11, 1),
            rangeEnd = LocalDate.of(2026, 11, 30)
        )

        val ids = results.map { it.id }
        assertEquals(2, ids.size)
        assertTrue(ids.contains("evt-in-range"))
        assertTrue(ids.contains("evt-recurring-active"))
        assertTrue(!ids.contains("evt-out-of-range"))
    }
}
