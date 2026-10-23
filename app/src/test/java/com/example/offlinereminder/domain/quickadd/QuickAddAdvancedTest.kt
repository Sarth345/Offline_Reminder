package com.example.offlinereminder.domain.quickadd

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class QuickAddAdvancedTest {

    private lateinit var parser: QuickAddParser

    @Before
    fun setUp() {
        val testTimeProvider = TestTimeProvider(
            currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
            currentZoneId = ZoneId.of("UTC")
        )
        parser = QuickAddParser(testTimeProvider)
    }

    @Test
    fun parse_timeRange_extractsStartAndEnd() {
        val draft = parser.parse("Project Sync from 2pm to 4pm")
        assertEquals("Project Sync", draft.title)
        assertEquals(LocalTime.of(14, 0), draft.time)
        assertEquals(LocalTime.of(16, 0), draft.endTime)
        assertTrue(draft.isTimeRange)
    }

    @Test
    fun parse_naturalDayparts_extractsApproximateTimes() {
        val morningDraft = parser.parse("Coffee chat tomorrow morning")
        assertEquals("Coffee chat", morningDraft.title)
        assertEquals(LocalDate.of(2026, 10, 22), morningDraft.date)
        assertEquals(LocalTime.of(9, 0), morningDraft.time)

        val eveningDraft = parser.parse("Team Dinner tonight")
        assertEquals("Team Dinner", eveningDraft.title)
        assertEquals(LocalDate.of(2026, 10, 21), eveningDraft.date)
        assertEquals(LocalTime.of(19, 0), eveningDraft.time)
    }

    @Test
    fun parse_multiDayRecurrence_extractsDaysOfWeek() {
        val draft = parser.parse("Gym workout every Mon Wed Fri at 7am")
        assertEquals("Gym workout", draft.title)
        assertEquals(LocalTime.of(7, 0), draft.time)
        assertNotNull(draft.recurrenceRule)
        val rule = draft.recurrenceRule!!
        assertEquals(RecurrenceFrequency.WEEKLY, rule.frequency)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), rule.daysOfWeek)
    }

    @Test
    fun parse_intervalRecurrence_extractsInterval() {
        val draft = parser.parse("Clean house every 2 weeks on Saturday")
        assertEquals("Clean house", draft.title)
        assertNotNull(draft.recurrenceRule)
        val rule = draft.recurrenceRule!!
        assertEquals(RecurrenceFrequency.WEEKLY, rule.frequency)
        assertEquals(2, rule.interval)
        assertEquals(setOf(DayOfWeek.SATURDAY), rule.daysOfWeek)
    }

    @Test
    fun parse_locationExtraction_extractsCleanLocation() {
        val draft = parser.parse("Dentist appointment tomorrow 3pm at Dental Care Clinic")
        assertEquals("Dentist appointment", draft.title)
        assertEquals(LocalDate.of(2026, 10, 22), draft.date)
        assertEquals(LocalTime.of(15, 0), draft.time)
        assertEquals("Dental Care Clinic", draft.locationName)
    }

    @Test
    fun parse_naturalReminder_extractsDaysMinutes() {
        val draft = parser.parse("Pay electric bill next Friday reminder 1 day before")
        assertEquals("Pay electric bill", draft.title)
        assertEquals(1440, draft.reminderMinutesBefore) // 1 day = 1440 mins
    }

    @Test
    fun parse_nextMonday_resolvesCorrectDate() {
        // Wednesday Oct 21, 2026 -> next Monday is Oct 26, 2026
        val draft = parser.parse("Team Standup next Monday at 10am")
        assertEquals("Team Standup", draft.title)
        assertEquals(LocalDate.of(2026, 10, 26), draft.date)
        assertEquals(LocalTime.of(10, 0), draft.time)
    }

    @Test
    fun parse_ambiguousDate_producesWarning() {
        // 03/04 is ambiguous between March 4 and April 3
        val draft = parser.parse("Review meeting on 03/04 at 2pm")
        assertEquals("Review meeting", draft.title)
        assertEquals(LocalTime.of(14, 0), draft.time)
        assertTrue(draft.ambiguities.isNotEmpty())
        assertTrue(draft.ambiguities.first().contains("ambiguous"))
    }
}
