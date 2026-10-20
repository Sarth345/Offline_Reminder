package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.relation.ChecklistWithItems
import com.example.offlinereminder.data.local.relation.EventWithDetails
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class EventMapperTest {

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")

    @Test
    fun dateOnlyEvent_mapsToEntityAndBackCorrectly() {
        val event = Event(
            id = "evt-date-only",
            title = "Annual Company Holiday",
            description = "Public holiday celebration",
            eventTypeId = "type-holiday",
            categoryId = "cat-work",
            priority = Priority.LOW,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 12, 25)),
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val entity = event.toEntity()
        assertEquals("DATE_ONLY", entity.temporalType)
        assertEquals(LocalDate.of(2026, 12, 25), entity.startDate)
        assertNull(entity.startTime)
        assertNull(entity.endDate)

        val eventWithDetails = EventWithDetails(event = entity)
        val restored = eventWithDetails.toDomain()

        assertEquals(event.id, restored.id)
        assertEquals(event.title, restored.title)
        assertTrue(restored.temporal is EventDateTime.DateOnly)
        assertEquals(LocalDate.of(2026, 12, 25), restored.temporal.startDate)
        assertTrue(restored.temporal.isAllDay)
    }

    @Test
    fun dateTimeEvent_withExplicitTimezone_preservesZoneAndLocalTime() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val event = Event(
            id = "evt-dt",
            title = "Tokyo Sync Meeting",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporal = EventDateTime.DateTime(
                dateTime = LocalDateTime.of(2026, 10, 25, 15, 30),
                zoneId = tokyoZone
            ),
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val entity = event.toEntity()
        assertEquals("DATE_TIME", entity.temporalType)
        assertEquals(LocalDate.of(2026, 10, 25), entity.startDate)
        assertEquals(LocalTime.of(15, 30), entity.startTime)
        assertEquals("Asia/Tokyo", entity.zoneId)

        val restored = EventWithDetails(event = entity).toDomain()
        assertTrue(restored.temporal is EventDateTime.DateTime)
        val dt = restored.temporal as EventDateTime.DateTime
        assertEquals(LocalDateTime.of(2026, 10, 25, 15, 30), dt.dateTime)
        assertEquals(tokyoZone, dt.zoneId)
    }

    @Test
    fun dateRangeEvent_mapsCorrectly() {
        val event = Event(
            id = "evt-range",
            title = "Goa Trip",
            eventTypeId = "type-trip",
            categoryId = "cat-personal",
            temporal = EventDateTime.DateRange(
                startDate = LocalDate.of(2026, 11, 1),
                endDate = LocalDate.of(2026, 11, 7)
            ),
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val entity = event.toEntity()
        assertEquals("DATE_RANGE", entity.temporalType)
        assertEquals(LocalDate.of(2026, 11, 1), entity.startDate)
        assertEquals(LocalDate.of(2026, 11, 7), entity.endDate)

        val restored = EventWithDetails(event = entity).toDomain()
        assertTrue(restored.temporal is EventDateTime.DateRange)
        val range = restored.temporal as EventDateTime.DateRange
        assertEquals(LocalDate.of(2026, 11, 1), range.startDate)
        assertEquals(LocalDate.of(2026, 11, 7), range.endDate)
    }

    @Test
    fun dateTimeRangeEvent_withExplicitTimezone_mapsCorrectly() {
        val nyZone = ZoneId.of("America/New_York")
        val event = Event(
            id = "evt-dt-range",
            title = "Conference Keynote",
            eventTypeId = "type-conf",
            categoryId = "cat-work",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 11, 10, 9, 0),
                endDateTime = LocalDateTime.of(2026, 11, 10, 11, 30),
                zoneId = nyZone
            ),
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val entity = event.toEntity()
        assertEquals("DATE_TIME_RANGE", entity.temporalType)
        assertEquals(LocalDate.of(2026, 11, 10), entity.startDate)
        assertEquals(LocalTime.of(9, 0), entity.startTime)
        assertEquals(LocalDate.of(2026, 11, 10), entity.endDate)
        assertEquals(LocalTime.of(11, 30), entity.endTime)
        assertEquals("America/New_York", entity.zoneId)

        val restored = EventWithDetails(event = entity).toDomain()
        assertTrue(restored.temporal is EventDateTime.DateTimeRange)
        val range = restored.temporal as EventDateTime.DateTimeRange
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 0), range.startDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 10, 11, 30), range.endDateTime)
        assertEquals(nyZone, range.zoneId)
    }

    @Test
    fun recurringEvent_withExcludedDatesAndUntilDate_mapsCorrectly() {
        val untilDate = LocalDate.of(2027, 10, 20)
        val excluded = setOf(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 1))
        val rrule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            endCondition = RecurrenceEnd.UntilDate(untilDate),
            excludedDates = excluded
        )

        val event = Event(
            id = "evt-recur",
            title = "Bi-weekly Standup",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 20)),
            recurrenceRule = rrule,
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val entity = event.toEntity()
        assertTrue(entity.hasRecurrence)
        assertEquals(RecurrenceFrequency.WEEKLY, entity.recurrenceFrequency)
        assertEquals(2, entity.recurrenceInterval)
        assertEquals("UNTIL_DATE", entity.recurrenceEndType)
        assertEquals(untilDate, entity.recurrenceUntilDate)
        assertEquals(excluded, entity.recurrenceExcludedDates)

        val restored = EventWithDetails(event = entity).toDomain()
        assertNotNull(restored.recurrenceRule)
        val restoredRrule = restored.recurrenceRule!!
        assertEquals(RecurrenceFrequency.WEEKLY, restoredRrule.frequency)
        assertEquals(2, restoredRrule.interval)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), restoredRrule.daysOfWeek)
        assertTrue(restoredRrule.endCondition is RecurrenceEnd.UntilDate)
        assertEquals(untilDate, (restoredRrule.endCondition as RecurrenceEnd.UntilDate).date)
        assertEquals(excluded, restoredRrule.excludedDates)
    }

    @Test
    fun eventWithChecklistAndNotifications_mapsCorrectly() {
        val checklist = Checklist(
            id = "chk-1",
            title = "Packing List",
            items = listOf(
                ChecklistItem(id = "item-1", text = "Passport", isCompleted = true, sortOrder = 0),
                ChecklistItem(id = "item-2", text = "Charger", isCompleted = false, sortOrder = 1)
            )
        )
        val schedules = listOf(
            NotificationSchedule.beforeStart(Duration.ofMinutes(30)),
            NotificationSchedule.atStart()
        )
        val attachments = listOf(
            AttachmentMetadata(
                id = "att-1",
                localUri = "content://app/files/ticket.pdf",
                fileName = "ticket.pdf",
                mimeType = "application/pdf",
                fileSizeBytes = 204800L,
                createdAt = testInstant
            )
        )
        val location = LocationData(
            placeName = "Airport Terminal 3",
            addressText = "New Delhi, India",
            building = "T3",
            room = "Gate 14",
            latitude = 28.5562,
            longitude = 77.1000
        )

        val event = Event(
            id = "evt-full",
            title = "Flight to Mumbai",
            eventTypeId = "type-flight",
            categoryId = "cat-travel",
            priority = Priority.CRITICAL,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 11, 5, 18, 0)),
            location = location,
            checklist = checklist,
            notificationSchedules = schedules,
            attachments = attachments,
            isFavorite = true,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val entity = event.toEntity()
        val chkEntity = checklist.toEntity(event.id)
        val chkItemEntities = checklist.items.map { it.toEntity(checklist.id) }
        val chkWithItems = ChecklistWithItems(checklist = chkEntity, items = chkItemEntities)
        val schedEntities = schedules.map { it.toEntity(event.id) }
        val attEntities = attachments.map { it.toEntity(event.id) }

        val eventWithDetails = EventWithDetails(
            event = entity,
            checklistWithItems = chkWithItems,
            notificationSchedules = schedEntities,
            attachments = attEntities
        )

        val restored = eventWithDetails.toDomain()

        assertEquals("Flight to Mumbai", restored.title)
        assertEquals(Priority.CRITICAL, restored.priority)
        assertTrue(restored.isFavorite)
        assertNotNull(restored.location)
        assertEquals("Airport Terminal 3", restored.location?.placeName)
        assertEquals("Gate 14", restored.location?.room)

        assertNotNull(restored.checklist)
        assertEquals(2, restored.checklist?.items?.size)
        assertEquals("Passport", restored.checklist?.items?.get(0)?.text)
        assertTrue(restored.checklist?.items?.get(0)?.isCompleted == true)

        assertEquals(2, restored.notificationSchedules.size)
        assertEquals(NotificationTriggerType.BEFORE_START, restored.notificationSchedules[0].triggerType)
        assertEquals(Duration.ofMinutes(30), restored.notificationSchedules[0].offsetDuration)
        assertEquals(NotificationTriggerType.AT_START, restored.notificationSchedules[1].triggerType)

        assertEquals(1, restored.attachments.size)
        assertEquals("ticket.pdf", restored.attachments[0].fileName)
    }

    @Test
    fun occurrenceException_mapsCorrectly_preservingOriginalAndRescheduledSemantics() {
        val originalLdt = LocalDateTime.of(2026, 10, 20, 10, 0)
        val rescheduledLdt = LocalDateTime.of(2026, 10, 21, 14, 0)
        val executionLdt = LocalDateTime.of(2026, 10, 21, 14, 5)
        val completedInstant = Instant.parse("2026-10-21T14:05:00Z")

        val exception = OccurrenceException(
            id = "exc-1",
            eventId = "evt-recur-1",
            originalStartDateTime = originalLdt,
            originalZoneId = ZoneId.of("Asia/Kolkata"),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = rescheduledLdt,
            rescheduledZoneId = ZoneId.of("Asia/Kolkata"),
            actualExecutionDateTime = executionLdt,
            completedAt = completedInstant,
            titleOverride = "Rescheduled Discussion",
            notesOverride = "Shifted by 1 day due to holiday"
        )

        val entity = exception.toEntity()
        assertEquals("exc-1", entity.id)
        assertEquals(exception.occurrenceId, entity.occurrenceId)
        assertEquals(originalLdt, entity.originalStartDateTime)
        assertEquals("Asia/Kolkata", entity.originalZoneId)
        assertEquals(rescheduledLdt, entity.rescheduledStartDateTime)
        assertEquals(executionLdt, entity.actualExecutionDateTime)

        val restored = entity.toDomain()
        assertEquals(exception.id, restored.id)
        assertEquals(exception.occurrenceId, restored.occurrenceId)
        assertEquals(originalLdt, restored.originalStartDateTime)
        assertEquals(ZoneId.of("Asia/Kolkata"), restored.originalZoneId)
        assertEquals(OccurrenceStatus.RESCHEDULED, restored.status)
        assertEquals(rescheduledLdt, restored.rescheduledStartDateTime)
        assertEquals(executionLdt, restored.actualExecutionDateTime)
        assertEquals(completedInstant, restored.completedAt)
        assertEquals("Rescheduled Discussion", restored.titleOverride)
        assertEquals("Shifted by 1 day due to holiday", restored.notesOverride)
    }
}
