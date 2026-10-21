package com.example.offlinereminder.domain.quickadd

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class QuickAddParserTest {

    private val zone = ZoneId.of("UTC")
    // Base time: Wednesday 2026-10-21 at 10:00 UTC
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
        currentZoneId = zone
    )

    private lateinit var parser: QuickAddParser

    @Before
    fun setUp() {
        parser = QuickAddParser(timeProvider)
    }

    @Test
    fun parse_simpleTitleOnly_defaultsCorrectly() {
        val result = parser.parse("Buy organic milk")

        assertEquals("Buy organic milk", result.title)
        assertTrue(result.isValid)
        assertTrue(result.isAllDay)
        assertNull(result.time)
        assertEquals(Priority.NORMAL, result.priority)
        assertNull(result.categoryName)
        assertTrue(result.groupNames.isEmpty())
        assertTrue(result.tagNames.isEmpty())
        assertNull(result.reminderMinutesBefore)
        assertNull(result.recurrenceRule)
    }

    @Test
    fun parse_relativeDates_todayAndTomorrow() {
        val todayResult = parser.parse("Doctor appointment today")
        assertEquals("Doctor appointment", todayResult.title)
        assertEquals(LocalDate.of(2026, 10, 21), todayResult.date)

        val tomorrowResult = parser.parse("Call accountant tomorrow")
        assertEquals("Call accountant", tomorrowResult.title)
        assertEquals(LocalDate.of(2026, 10, 22), tomorrowResult.date)

        val tmrwResult = parser.parse("Team sync tmrw")
        assertEquals("Team sync", tmrwResult.title)
        assertEquals(LocalDate.of(2026, 10, 22), tmrwResult.date)

        val dayAfterTomorrowResult = parser.parse("Submit report day after tomorrow")
        assertEquals("Submit report", dayAfterTomorrowResult.title)
        assertEquals(LocalDate.of(2026, 10, 23), dayAfterTomorrowResult.date)
    }

    @Test
    fun parse_daysOfWeek() {
        // Today is Wednesday 2026-10-21
        // Friday is 2026-10-23
        val fridayResult = parser.parse("Pay invoices on Friday")
        assertEquals("Pay invoices", fridayResult.title)
        assertEquals(LocalDate.of(2026, 10, 23), fridayResult.date)

        // Next Monday is 2026-10-26
        val mondayResult = parser.parse("Sprint planning Monday")
        assertEquals("Sprint planning", mondayResult.title)
        assertEquals(LocalDate.of(2026, 10, 26), mondayResult.date)
    }

    @Test
    fun parse_standardIsoDate() {
        val result = parser.parse("Annual medical exam 2026-11-15")
        assertEquals("Annual medical exam", result.title)
        assertEquals(LocalDate.of(2026, 11, 15), result.date)
    }

    @Test
    fun parse_namedMonthDate() {
        val result = parser.parse("Conference Nov 12")
        assertEquals("Conference", result.title)
        assertEquals(LocalDate.of(2026, 11, 12), result.date)

        val result2 = parser.parse("Birthday 15th December")
        assertEquals("Birthday", result2.title)
        assertEquals(LocalDate.of(2026, 12, 15), result2.date)
    }

    @Test
    fun parse_timeExpressions_12HourAnd24Hour() {
        val pmResult = parser.parse("Client demo tomorrow at 3pm")
        assertEquals("Client demo", pmResult.title)
        assertEquals(LocalDate.of(2026, 10, 22), pmResult.date)
        assertEquals(LocalTime.of(15, 0), pmResult.time)
        assertFalse(pmResult.isAllDay)

        val amResult = parser.parse("Morning coffee 9:30am")
        assertEquals("Morning coffee", amResult.title)
        assertEquals(LocalTime.of(9, 30), amResult.time)

        val h24Result = parser.parse("Release deploy 14:00")
        assertEquals("Release deploy", h24Result.title)
        assertEquals(LocalTime.of(14, 0), h24Result.time)

        val noonResult = parser.parse("Lunch noon")
        assertEquals("Lunch", noonResult.title)
        assertEquals(LocalTime.of(12, 0), noonResult.time)
    }

    @Test
    fun parse_priorityExpressions() {
        assertEquals(Priority.CRITICAL, parser.parse("Server outage !critical").priority)
        assertEquals(Priority.CRITICAL, parser.parse("Server outage !p1").priority)
        assertEquals(Priority.HIGH, parser.parse("Dentist !high").priority)
        assertEquals(Priority.HIGH, parser.parse("Dentist !p2").priority)
        assertEquals(Priority.NORMAL, parser.parse("Clean desk !normal").priority)
        assertEquals(Priority.LOW, parser.parse("Water plants !low").priority)
        assertEquals(Priority.LOW, parser.parse("Water plants !p4").priority)
    }

    @Test
    fun parse_categoryGroupAndTagExpressions() {
        val result = parser.parse("Code review #Work @ProjectAlpha +urgent")
        assertEquals("Code review", result.title)
        assertEquals("Work", result.categoryName)
        assertEquals(listOf("ProjectAlpha"), result.groupNames)
        assertEquals(listOf("urgent"), result.tagNames)

        val prefixResult = parser.parse("Taxes cat:Finance grp:Household tag:tax")
        assertEquals("Taxes", prefixResult.title)
        assertEquals("Finance", prefixResult.categoryName)
        assertEquals(listOf("Household"), prefixResult.groupNames)
        assertEquals(listOf("tax"), prefixResult.tagNames)
    }

    @Test
    fun parse_reminderExpressions() {
        val rem15 = parser.parse("Flight check-in rem:15m")
        assertEquals("Flight check-in", rem15.title)
        assertEquals(15, rem15.reminderMinutesBefore)

        val rem1h = parser.parse("Interview reminder:1h")
        assertEquals("Interview", rem1h.title)
        assertEquals(60, rem1h.reminderMinutesBefore)

        val rem1d = parser.parse("Rent rem:1d")
        assertEquals("Rent", rem1d.title)
        assertEquals(1440, rem1d.reminderMinutesBefore)

        val remAtTime = parser.parse("Alarm rem:at-time")
        assertEquals("Alarm", remAtTime.title)
        assertEquals(0, remAtTime.reminderMinutesBefore)

        val remZeroM = parser.parse("Meeting rem:0m")
        assertEquals("Meeting", remZeroM.title)
        assertEquals(0, remZeroM.reminderMinutesBefore)

        val remInvalid = parser.parse("Task with rem:invalid")
        assertEquals(null, remInvalid.reminderMinutesBefore)
    }

    @Test
    fun parse_recurrenceExpressions() {
        val daily = parser.parse("Standup daily 10am")
        assertEquals("Standup", daily.title)
        assertNotNull(daily.recurrenceRule)
        assertEquals(RecurrenceFrequency.DAILY, daily.recurrenceRule?.frequency)

        val weekly = parser.parse("Weekly review every week")
        assertEquals("Weekly review", weekly.title)
        assertEquals(RecurrenceFrequency.WEEKLY, weekly.recurrenceRule?.frequency)

        val monthly = parser.parse("Rent monthly")
        assertEquals("Rent", monthly.title)
        assertEquals(RecurrenceFrequency.MONTHLY, monthly.recurrenceRule?.frequency)

        val yearly = parser.parse("Anniversary yearly")
        assertEquals("Anniversary", yearly.title)
        assertEquals(RecurrenceFrequency.YEARLY, yearly.recurrenceRule?.frequency)
        assertEquals(1, yearly.recurrenceRule?.interval)

        val weekdays = parser.parse("School run weekdays 8am")
        assertEquals("School run", weekdays.title)
        assertEquals(RecurrenceFrequency.WEEKLY, weekdays.recurrenceRule?.frequency)
        assertEquals(5, weekdays.recurrenceRule?.daysOfWeek?.size)

        val weekends = parser.parse("Hiking weekends")
        assertEquals("Hiking", weekends.title)
        assertEquals(RecurrenceFrequency.WEEKLY, weekends.recurrenceRule?.frequency)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), weekends.recurrenceRule?.daysOfWeek)

        val everyFriday = parser.parse("Team retro every Friday at 4pm")
        assertEquals("Team retro", everyFriday.title)
        assertEquals(RecurrenceFrequency.WEEKLY, everyFriday.recurrenceRule?.frequency)
        assertEquals(setOf(DayOfWeek.FRIDAY), everyFriday.recurrenceRule?.daysOfWeek)

        val every2Weeks = parser.parse("Paycheck every 2 weeks")
        assertEquals("Paycheck", every2Weeks.title)
        assertEquals(RecurrenceFrequency.WEEKLY, every2Weeks.recurrenceRule?.frequency)
        assertEquals(2, every2Weeks.recurrenceRule?.interval)
    }

    @Test
    fun parse_combinedRichExpression() {
        val input = "Quarterly board presentation next Monday 2pm !critical #Work @Executive +board rem:30m"
        val result = parser.parse(input)

        assertEquals("Quarterly board presentation", result.title)
        assertEquals(LocalDate.of(2026, 10, 26), result.date)
        assertEquals(LocalTime.of(14, 0), result.time)
        assertEquals(Priority.CRITICAL, result.priority)
        assertEquals("Work", result.categoryName)
        assertEquals(listOf("Executive"), result.groupNames)
        assertEquals(listOf("board"), result.tagNames)
        assertEquals(30, result.reminderMinutesBefore)
        assertTrue(result.isValid)
    }

    @Test
    fun parse_ambiguousInput_recordsNotice() {
        val result = parser.parse("Dentist 05/06")
        assertTrue(result.ambiguities.isNotEmpty())
        assertTrue(result.ambiguities.first().contains("MM/DD"))
    }

    @Test
    fun parse_invalidInput_emptyOrTokensOnly() {
        val emptyResult = parser.parse("   ")
        assertFalse(emptyResult.isValid)
        assertTrue(emptyResult.validationErrors.isNotEmpty())

        val tokensOnlyResult = parser.parse("!high #Work @Trip 3pm")
        assertFalse(tokensOnlyResult.isValid)
        assertTrue(tokensOnlyResult.validationErrors.isNotEmpty())
    }

    @Test
    fun parse_unparsedTokens_capturedGracefully() {
        val result = parser.parse("Test reminder !unknownToken")
        assertEquals("Test reminder", result.title)
        assertTrue(result.unparsedTokens.contains("!unknowntoken"))
    }
}
