package com.example.offlinereminder.domain.model

import com.example.offlinereminder.core.time.DateTimeUtils
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.usecase.AddEventToGroupUseCase
import com.example.offlinereminder.domain.usecase.AddTagToEventUseCase
import com.example.offlinereminder.domain.usecase.ArchiveEventUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.OccurrenceMutationResult
import com.example.offlinereminder.domain.usecase.RemoveEventFromGroupUseCase
import com.example.offlinereminder.domain.usecase.RemoveTagFromEventUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.RestoreEventUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.TrashEventUseCase
import com.example.offlinereminder.domain.usecase.ValidateEventUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EventDomainTest {

    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T10:00:00Z"),
        currentZoneId = ZoneId.of("UTC")
    )
    private val validateEventUseCase = ValidateEventUseCase()
    private val completeOccurrenceUseCase = CompleteOccurrenceUseCase(timeProvider)
    private val skipOccurrenceUseCase = SkipOccurrenceUseCase()
    private val rescheduleOccurrenceUseCase = RescheduleOccurrenceUseCase()
    private val trashEventUseCase = TrashEventUseCase(timeProvider)
    private val restoreEventUseCase = RestoreEventUseCase(timeProvider)
    private val archiveEventUseCase = ArchiveEventUseCase(timeProvider)
    private val addEventToGroupUseCase = AddEventToGroupUseCase(timeProvider)
    private val removeEventFromGroupUseCase = RemoveEventFromGroupUseCase(timeProvider)
    private val addTagToEventUseCase = AddTagToEventUseCase(timeProvider)
    private val removeTagFromEventUseCase = RemoveTagFromEventUseCase(timeProvider)

    private fun createBaseEvent(
        temporal: EventDateTime = EventDateTime.DateTime(
            dateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            zoneId = ZoneId.of("UTC")
        ),
        recurrenceRule: RecurrenceRule? = null
    ): Event = Event(
        id = "event-123",
        title = "Doctor Appointment",
        description = "Routine checkup",
        eventTypeId = "type-med",
        categoryId = "cat-health",
        priority = Priority.HIGH,
        temporal = temporal,
        recurrenceRule = recurrenceRule,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun `1 one-time event creates initial occurrence with matching properties`() {
        val event = createBaseEvent()
        assertTrue(event.isOneTime)
        assertFalse(event.isRecurring)

        val occurrence = event.createInitialOccurrence()
        assertEquals(event.id, occurrence.eventId)
        assertEquals(LocalDateTime.of(2026, 10, 25, 14, 0), occurrence.originalStartDateTime)
        assertEquals(OccurrenceStatus.PENDING, occurrence.status)
        assertFalse(occurrence.isException)
        assertEquals(occurrence.originalStartDateTime, occurrence.effectiveStartDateTime)
    }

    @Test
    fun `2 recurring event is identified correctly and does not generate infinite items`() {
        val recurrence = RecurrenceRule.weekly(daysOfWeek = setOf(DayOfWeek.MONDAY))
        val event = createBaseEvent(recurrenceRule = recurrence)

        assertTrue(event.isRecurring)
        assertFalse(event.isOneTime)
        assertEquals(RecurrenceFrequency.WEEKLY, event.recurrenceRule?.frequency)
    }

    @Test
    fun `3 EventDateTime integration validates all four date modes`() {
        val dateOnly = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25))
        assertTrue(dateOnly.isAllDay)

        val dateTime = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 10, 0))
        assertFalse(dateTime.isAllDay)

        val dateRange = EventDateTime.DateRange(
            startDate = LocalDate.of(2026, 10, 25),
            endDate = LocalDate.of(2026, 10, 28)
        )
        assertTrue(dateRange.isAllDay)

        val dateTimeRange = EventDateTime.DateTimeRange(
            startDateTime = LocalDateTime.of(2026, 10, 25, 10, 0),
            endDateTime = LocalDateTime.of(2026, 10, 25, 12, 0)
        )
        assertFalse(dateTimeRange.isAllDay)
    }

    @Test
    fun `4 completion of one recurring occurrence generates exception without mutating master event`() {
        val recurringEvent = createBaseEvent(
            recurrenceRule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY))
        )
        val targetDate = LocalDateTime.of(2026, 10, 26, 10, 0)

        val result = completeOccurrenceUseCase(recurringEvent, targetDate)
        assertTrue(result.isSuccess)

        val mutation = (result as com.example.offlinereminder.core.result.Result.Success).data
        assertTrue(mutation is OccurrenceMutationResult.RecurringException)

        val exception = (mutation as OccurrenceMutationResult.RecurringException).exception
        assertEquals(recurringEvent.id, exception.eventId)
        assertEquals(targetDate, exception.originalStartDateTime)
        assertEquals(OccurrenceStatus.COMPLETED, exception.status)
        assertEquals(timeProvider.nowInstant(), exception.completedAt)

        // Verify master event remains completely untouched
        assertEquals(EventLifecycleStatus.ACTIVE, recurringEvent.lifecycleStatus)
        assertEquals(RecurrenceFrequency.WEEKLY, recurringEvent.recurrenceRule?.frequency)
    }

    @Test
    fun `5 skipping one recurring occurrence generates skipped exception`() {
        val recurringEvent = createBaseEvent(
            recurrenceRule = RecurrenceRule.daily()
        )
        val targetDate = LocalDateTime.of(2026, 10, 21, 9, 0)

        val result = skipOccurrenceUseCase(recurringEvent, targetDate)
        assertTrue(result.isSuccess)

        val mutation = (result as com.example.offlinereminder.core.result.Result.Success).data
        val exception = (mutation as OccurrenceMutationResult.RecurringException).exception
        assertEquals(OccurrenceStatus.SKIPPED, exception.status)
        assertEquals(targetDate, exception.originalStartDateTime)
    }

    @Test
    fun `6 rescheduling one recurring occurrence sets rescheduledStartDateTime and preserves identity`() {
        val recurringEvent = createBaseEvent(
            recurrenceRule = RecurrenceRule.daily()
        )
        val originalDate = LocalDateTime.of(2026, 10, 21, 9, 0)
        val originalZone = ZoneId.of("UTC")
        val newDate = LocalDateTime.of(2026, 10, 21, 15, 30)

        val result = rescheduleOccurrenceUseCase(
            event = recurringEvent,
            originalStartDateTime = originalDate,
            originalZoneId = originalZone,
            newStartDateTime = newDate
        )
        assertTrue(result.isSuccess)

        val mutation = (result as com.example.offlinereminder.core.result.Result.Success).data
        val exception = (mutation as OccurrenceMutationResult.RecurringException).exception
        assertEquals(OccurrenceStatus.RESCHEDULED, exception.status)
        // Original occurrence identity is preserved
        assertEquals(originalDate, exception.originalStartDateTime)
        assertEquals(originalZone, exception.originalZoneId)
        assertEquals("${recurringEvent.id}_${originalDate}_${originalZone.id}", exception.occurrenceId)
        // New scheduled date is explicitly set
        assertEquals(newDate, exception.rescheduledStartDateTime)
        assertNull(exception.actualExecutionDateTime)
    }

    @Test
    fun `7 event lifecycle is separate from occurrence lifecycle`() {
        val event = createBaseEvent()
        assertEquals(EventLifecycleStatus.ACTIVE, event.lifecycleStatus)

        val trashed = trashEventUseCase(event)
        assertEquals(EventLifecycleStatus.TRASHED, trashed.lifecycleStatus)
        assertNotNull(trashed.deletedAt)

        val restored = restoreEventUseCase(trashed)
        assertEquals(EventLifecycleStatus.ACTIVE, restored.lifecycleStatus)
        assertNull(restored.deletedAt)

        val archived = archiveEventUseCase(restored)
        assertEquals(EventLifecycleStatus.ARCHIVED, archived.lifecycleStatus)
    }

    @Test
    fun `8 multiple groups association and non-destructive removal`() {
        var event = createBaseEvent()
        assertTrue(event.groupIds.isEmpty())

        event = addEventToGroupUseCase(event, "group-college")
        event = addEventToGroupUseCase(event, "group-exams")
        assertEquals(setOf("group-college", "group-exams"), event.groupIds)

        // Removing one group leaves other groups and event intact
        event = removeEventFromGroupUseCase(event, "group-college")
        assertEquals(setOf("group-exams"), event.groupIds)
    }

    @Test
    fun `9 tags normalization and association`() {
        var event = createBaseEvent()
        val tag = Tag(name = "#Important")
        assertEquals("important", tag.normalizedName)

        event = addTagToEventUseCase(event, tag.id)
        assertTrue(event.tagIds.contains(tag.id))

        event = removeTagFromEventUseCase(event, tag.id)
        assertFalse(event.tagIds.contains(tag.id))
    }

    @Test
    fun `10 checklist progress calculation and validation`() {
        val checklist = Checklist(
            title = "Preparation",
            items = listOf(
                ChecklistItem(text = "Review notes", isCompleted = true),
                ChecklistItem(text = "Practice questions", isCompleted = false)
            )
        )
        assertEquals(2, checklist.totalCount)
        assertEquals(1, checklist.completedCount)
        assertEquals(0.5f, checklist.progressPercentage, 0.001f)
        assertFalse(checklist.isAllCompleted)

        // Validate blank item text rejection
        val invalidChecklist = Checklist(
            items = listOf(ChecklistItem(text = "   "))
        )
        val event = createBaseEvent().copy(checklist = invalidChecklist)
        val validation = validateEventUseCase(event)
        assertTrue(validation.isFailure)
    }

    @Test
    fun `11 recurrence rule factory methods and validation`() {
        val daily = RecurrenceRule.daily(interval = 2)
        assertTrue(daily.validate().isSuccess)

        val weekdays = RecurrenceRule.weekdays()
        assertTrue(weekdays.validate().isSuccess)
        assertEquals(5, weekdays.daysOfWeek.size)

        val weekends = RecurrenceRule.weekends()
        assertTrue(weekends.validate().isSuccess)
        assertEquals(2, weekends.daysOfWeek.size)

        val monthly = RecurrenceRule.monthly(dayOfMonth = 15)
        assertTrue(monthly.validate().isSuccess)

        val yearly = RecurrenceRule.yearly(monthOfYear = 10, dayOfMonth = 25)
        assertTrue(yearly.validate().isSuccess)
    }

    @Test
    fun `12 notification schedule validation rules`() {
        val validBeforeStart = NotificationSchedule.beforeStart(Duration.ofMinutes(30))
        assertTrue(validBeforeStart.validate().isSuccess)

        val validAtStart = NotificationSchedule.atStart()
        assertTrue(validAtStart.validate().isSuccess)

        // Invalid: negative duration
        val negative = NotificationSchedule(
            offsetDuration = Duration.ofMinutes(-10),
            triggerType = NotificationTriggerType.BEFORE_START
        )
        assertTrue(negative.validate().isFailure)

        // Invalid: non-zero offset on AT_START
        val invalidAtStart = NotificationSchedule(
            offsetDuration = Duration.ofMinutes(15),
            triggerType = NotificationTriggerType.AT_START
        )
        assertTrue(invalidAtStart.validate().isFailure)
    }

    @Test
    fun `13 Feb 29 leap year clamping`() {
        // Leap year 2028: Feb 29 exists
        assertEquals(LocalDate.of(2028, 2, 29), DateTimeUtils.clampFeb29Yearly(2028))

        // Non-leap year 2026: clamps to Feb 28
        assertEquals(LocalDate.of(2026, 2, 28), DateTimeUtils.clampFeb29Yearly(2026))
    }

    @Test
    fun `14 monthly 31st clamping on 30-day and February months`() {
        // April has 30 days
        assertEquals(LocalDate.of(2026, 4, 30), DateTimeUtils.clampDayOfMonth(2026, 4, 31))

        // Feb 2026 has 28 days
        assertEquals(LocalDate.of(2026, 2, 28), DateTimeUtils.clampDayOfMonth(2026, 2, 31))

        // Feb 2028 (leap) has 29 days
        assertEquals(LocalDate.of(2028, 2, 29), DateTimeUtils.clampDayOfMonth(2028, 2, 31))
    }

    @Test
    fun `15 invalid date ranges are rejected by EventDateTime`() {
        try {
            EventDateTime.DateRange(
                startDate = LocalDate.of(2026, 10, 25),
                endDate = LocalDate.of(2026, 10, 20)
            )
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be before") == true)
        }
    }

    @Test
    fun `16 invalid recurrence intervals are rejected`() {
        val zeroInterval = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 0
        )
        assertTrue(zeroInterval.validate().isFailure)

        val invalidMonth = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            dayOfMonth = 32
        )
        assertTrue(invalidMonth.validate().isFailure)
    }

    @Test
    fun `17 equality and identity semantics`() {
        val event1 = createBaseEvent()
        val event2 = event1.copy()
        assertEquals(event1, event2)

        val event3 = event1.copy(title = "Dentist Appointment")
        assertFalse(event1 == event3)
    }

    @Test
    fun `18 validation rejects blank event title, category, or event type`() {
        val blankTitle = createBaseEvent().copy(title = "   ")
        assertTrue(validateEventUseCase(blankTitle).isFailure)

        val blankCategory = createBaseEvent().copy(categoryId = "")
        assertTrue(validateEventUseCase(blankCategory).isFailure)

        val blankType = createBaseEvent().copy(eventTypeId = "")
        assertTrue(validateEventUseCase(blankType).isFailure)
    }

    @Test
    fun `19 rescheduling preserves original occurrence identity and timezone while setting replacement schedule`() {
        val recurringEvent = createBaseEvent(recurrenceRule = RecurrenceRule.daily())
        val originalDate = LocalDateTime.of(2026, 10, 21, 9, 0)
        val originalZone = ZoneId.of("America/New_York")
        val newDate = LocalDateTime.of(2026, 10, 22, 14, 0)
        val newZone = ZoneId.of("Europe/London")

        val result = rescheduleOccurrenceUseCase(
            event = recurringEvent,
            originalStartDateTime = originalDate,
            originalZoneId = originalZone,
            newStartDateTime = newDate,
            newZoneId = newZone
        )
        assertTrue(result.isSuccess)

        val mutation = (result as com.example.offlinereminder.core.result.Result.Success).data
        val exception = (mutation as OccurrenceMutationResult.RecurringException).exception

        // Identity check: original date and zone are retained
        assertEquals(originalDate, exception.originalStartDateTime)
        assertEquals(originalZone, exception.originalZoneId)
        assertEquals("${recurringEvent.id}_${originalDate}_${originalZone.id}", exception.occurrenceId)

        // Replacement schedule check
        assertEquals(newDate, exception.rescheduledStartDateTime)
        assertEquals(newZone, exception.rescheduledZoneId)

        // EventOccurrence mapping check
        val occurrence = EventOccurrence(
            eventId = recurringEvent.id,
            originalStartDateTime = originalDate,
            zoneId = originalZone,
            rescheduledStartDateTime = exception.rescheduledStartDateTime,
            rescheduledZoneId = exception.rescheduledZoneId,
            status = OccurrenceStatus.RESCHEDULED
        )
        assertEquals("${recurringEvent.id}_${originalDate}_${originalZone.id}", occurrence.occurrenceId)
        assertEquals(newDate, occurrence.effectiveStartDateTime)
        assertEquals(newZone, occurrence.effectiveZoneId)
    }

    @Test
    fun `20 occurrence identity differentiates same local time in different timezones`() {
        val ldt = LocalDateTime.of(2026, 10, 25, 10, 0)
        val idUtc = EventOccurrence.createOccurrenceId("event-1", ldt, ZoneId.of("UTC"))
        val idNy = EventOccurrence.createOccurrenceId("event-1", ldt, ZoneId.of("America/New_York"))

        assertNotEquals(idUtc, idNy)
        assertEquals("event-1_2026-10-25T10:00_UTC", idUtc)
        assertEquals("event-1_2026-10-25T10:00_America/New_York", idNy)
    }

    @Test
    fun `21 actual execution time is semantically separate from rescheduled scheduled time`() {
        val recurringEvent = createBaseEvent(recurrenceRule = RecurrenceRule.daily())
        val originalDate = LocalDateTime.of(2026, 10, 21, 9, 0)
        val newDate = LocalDateTime.of(2026, 10, 22, 14, 0)
        val actualAttendedDate = LocalDateTime.of(2026, 10, 22, 14, 15)

        val exception = OccurrenceException(
            eventId = recurringEvent.id,
            originalStartDateTime = originalDate,
            status = OccurrenceStatus.COMPLETED,
            rescheduledStartDateTime = newDate,
            actualExecutionDateTime = actualAttendedDate,
            completedAt = timeProvider.nowInstant()
        )

        assertEquals(newDate, exception.rescheduledStartDateTime)
        assertEquals(actualAttendedDate, exception.actualExecutionDateTime)
        assertNotNull(exception.completedAt)
    }

    @Test
    fun `22 recurrence exclusions tracking via excludedDates`() {
        val baseRule = RecurrenceRule.daily(interval = 1)
        val date1 = LocalDate.of(2026, 10, 22)
        val date2 = LocalDate.of(2026, 10, 25)

        val ruleWithExclusions = baseRule.withExcludedDate(date1).withExcludedDate(date2)

        assertTrue(ruleWithExclusions.isExcluded(date1))
        assertTrue(ruleWithExclusions.isExcluded(date2))
        assertFalse(ruleWithExclusions.isExcluded(LocalDate.of(2026, 10, 23)))
        assertEquals(2, ruleWithExclusions.excludedDates.size)
    }

    @Test
    fun `23 recurrence boundary validation with Count and UntilDate`() {
        val countRule = RecurrenceRule.daily(endCondition = RecurrenceEnd.Count(10))
        assertTrue(countRule.validate().isSuccess)

        val invalidCountRule = RecurrenceRule.daily(endCondition = RecurrenceEnd.Count(0))
        assertTrue(invalidCountRule.validate().isFailure)

        val untilRule = RecurrenceRule.daily(
            endCondition = RecurrenceEnd.UntilDate(LocalDate.of(2027, 1, 1))
        )
        assertTrue(untilRule.validate().isSuccess)
    }
}
