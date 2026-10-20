package com.example.offlinereminder.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DateTimeUtilsTest {

    @Test
    fun `clampDayOfMonth clamps 31st to 30th on 30-day months`() {
        val april31Clamped = DateTimeUtils.clampDayOfMonth(2026, 4, 31)
        assertEquals(LocalDate.of(2026, 4, 30), april31Clamped)

        val june31Clamped = DateTimeUtils.clampDayOfMonth(2026, 6, 31)
        assertEquals(LocalDate.of(2026, 6, 30), june31Clamped)
    }

    @Test
    fun `clampDayOfMonth clamps 31st to 28th on standard February`() {
        val feb31Clamped = DateTimeUtils.clampDayOfMonth(2026, 2, 31)
        assertEquals(LocalDate.of(2026, 2, 28), feb31Clamped)
    }

    @Test
    fun `clampDayOfMonth clamps 31st to 29th on leap year February`() {
        val feb31LeapClamped = DateTimeUtils.clampDayOfMonth(2028, 2, 31)
        assertEquals(LocalDate.of(2028, 2, 29), feb31LeapClamped)
    }

    @Test
    fun `clampDayOfMonth preserves valid dates`() {
        val jan31 = DateTimeUtils.clampDayOfMonth(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 1, 31), jan31)

        val oct15 = DateTimeUtils.clampDayOfMonth(2026, 10, 15)
        assertEquals(LocalDate.of(2026, 10, 15), oct15)
    }

    @Test
    fun `clampFeb29Yearly returns Feb 28 in non-leap years`() {
        assertEquals(LocalDate.of(2025, 2, 28), DateTimeUtils.clampFeb29Yearly(2025))
        assertEquals(LocalDate.of(2026, 2, 28), DateTimeUtils.clampFeb29Yearly(2026))
        assertEquals(LocalDate.of(2027, 2, 28), DateTimeUtils.clampFeb29Yearly(2027))
    }

    @Test
    fun `clampFeb29Yearly returns Feb 29 in leap years`() {
        assertEquals(LocalDate.of(2024, 2, 29), DateTimeUtils.clampFeb29Yearly(2024))
        assertEquals(LocalDate.of(2028, 2, 29), DateTimeUtils.clampFeb29Yearly(2028))
    }

    @Test
    fun `toEpochMillis and fromEpochMillis round-trip cleanly`() {
        val original = LocalDateTime.of(2026, 10, 25, 14, 30, 0)
        val zone = ZoneId.of("UTC")
        val epochMillis = DateTimeUtils.toEpochMillis(original, zone)
        val recovered = DateTimeUtils.fromEpochMillis(epochMillis, zone)

        assertEquals(original, recovered)
    }

    @Test
    fun `calculateCountdown calculates future difference correctly`() {
        val now = LocalDateTime.of(2026, 10, 20, 10, 0, 0)
        val target = LocalDateTime.of(2026, 10, 23, 17, 30, 15)

        val countdown = DateTimeUtils.calculateCountdown(from = now, to = target)

        assertFalse(countdown.isPast)
        assertEquals(3, countdown.days)
        assertEquals(7, countdown.hours)
        assertEquals(30, countdown.minutes)
        assertEquals(15, countdown.seconds)
    }

    @Test
    fun `calculateCountdown calculates past difference correctly`() {
        val target = LocalDateTime.of(2026, 10, 18, 8, 0, 0)
        val now = LocalDateTime.of(2026, 10, 20, 10, 0, 0)

        val countdown = DateTimeUtils.calculateCountdown(from = now, to = target)

        assertTrue(countdown.isPast)
        assertEquals(2, countdown.days)
        assertEquals(2, countdown.hours)
        assertEquals(0, countdown.minutes)
        assertEquals(0, countdown.seconds)
    }
}
