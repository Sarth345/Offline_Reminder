package com.example.offlinereminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class EventDateTimeTest {

    private val testZone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `DateOnly is all day and provides expected defaults`() {
        val date = LocalDate.of(2026, 10, 25)
        val eventDateTime = EventDateTime.DateOnly(date)

        assertTrue(eventDateTime.isAllDay)
        assertEquals(date, eventDateTime.startDate)
        assertEquals(LocalDateTime.of(2026, 10, 25, 9, 0), eventDateTime.startLocalDateTime(LocalTime.of(9, 0)))
    }

    @Test
    fun `DateTime has exact time and is not all day`() {
        val localDateTime = LocalDateTime.of(2026, 10, 25, 10, 30)
        val eventDateTime = EventDateTime.DateTime(localDateTime, testZone)

        assertFalse(eventDateTime.isAllDay)
        assertEquals(LocalDate.of(2026, 10, 25), eventDateTime.startDate)
        assertEquals(localDateTime, eventDateTime.startLocalDateTime())
    }

    @Test
    fun `DateRange validates end date not before start date`() {
        val start = LocalDate.of(2026, 10, 20)
        val end = LocalDate.of(2026, 10, 25)

        val validRange = EventDateTime.DateRange(start, end)
        assertTrue(validRange.isAllDay)
        assertEquals(start, validRange.startDate)

        try {
            EventDateTime.DateRange(startDate = end, endDate = start)
            fail("Expected IllegalArgumentException when end date is before start date")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be before") == true)
        }
    }

    @Test
    fun `DateTimeRange validates end date-time not before start date-time`() {
        val start = LocalDateTime.of(2026, 10, 20, 10, 0)
        val end = LocalDateTime.of(2026, 10, 20, 12, 0)

        val validRange = EventDateTime.DateTimeRange(start, end, testZone)
        assertFalse(validRange.isAllDay)
        assertEquals(LocalDate.of(2026, 10, 20), validRange.startDate)

        try {
            EventDateTime.DateTimeRange(startDateTime = end, endDateTime = start, zoneId = testZone)
            fail("Expected IllegalArgumentException when end date-time is before start date-time")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be before") == true)
        }
    }
}
