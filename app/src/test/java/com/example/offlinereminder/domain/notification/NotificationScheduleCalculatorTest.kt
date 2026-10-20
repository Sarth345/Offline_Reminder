package com.example.offlinereminder.domain.notification

import com.example.offlinereminder.core.time.DateTimeUtils
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NotificationScheduleCalculatorTest {

    private val utcZone = ZoneId.of("UTC")
    private val nyZone = ZoneId.of("America/New_York")

    private fun createOccurrence(
        startLdt: LocalDateTime,
        zone: ZoneId = utcZone,
        endLdt: LocalDateTime? = null
    ): EventOccurrence = EventOccurrence(
        eventId = "event-1",
        originalStartDateTime = startLdt,
        zoneId = zone,
        defaultEndDateTime = endLdt
    )

    @Test
    fun `1 calculate AT_START offset triggers at exact occurrence start`() {
        val startLdt = LocalDateTime.of(2026, 10, 25, 14, 0)
        val occ = createOccurrence(startLdt)
        val schedule = NotificationSchedule.atStart()

        val calc = NotificationScheduleCalculator.calculate(occ, schedule)

        assertEquals(startLdt, calc.nominalDateTime)
        assertEquals(startLdt, calc.deliveryDateTime)
        assertEquals(DateTimeUtils.toEpochMillis(startLdt, utcZone), calc.triggerEpochMillis)
        assertFalse(calc.isSilent)
    }

    @Test
    fun `2 calculate BEFORE_START offset (30 mins before)`() {
        val startLdt = LocalDateTime.of(2026, 10, 25, 14, 0)
        val occ = createOccurrence(startLdt)
        val schedule = NotificationSchedule.beforeStart(Duration.ofMinutes(30))

        val calc = NotificationScheduleCalculator.calculate(occ, schedule)

        val expected = LocalDateTime.of(2026, 10, 25, 13, 30)
        assertEquals(expected, calc.nominalDateTime)
        assertEquals(expected, calc.deliveryDateTime)
        assertEquals(DateTimeUtils.toEpochMillis(expected, utcZone), calc.triggerEpochMillis)
    }

    @Test
    fun `3 calculate BEFORE_END and AT_END offsets with range`() {
        val startLdt = LocalDateTime.of(2026, 10, 25, 10, 0)
        val endLdt = LocalDateTime.of(2026, 10, 25, 12, 0)
        val occ = createOccurrence(startLdt, endLdt = endLdt)

        val scheduleBeforeEnd = NotificationSchedule.beforeEnd(Duration.ofMinutes(15))
        val calcBeforeEnd = NotificationScheduleCalculator.calculate(occ, scheduleBeforeEnd)
        assertEquals(LocalDateTime.of(2026, 10, 25, 11, 45), calcBeforeEnd.nominalDateTime)

        val scheduleAtEnd = NotificationSchedule.atEnd()
        val calcAtEnd = NotificationScheduleCalculator.calculate(occ, scheduleAtEnd)
        assertEquals(endLdt, calcAtEnd.nominalDateTime)
    }

    @Test
    fun `4 calculate with quiet hours DELAY overnight shifts trigger to end of quiet hours`() {
        // Event at 02:00 AM (during quiet hours 22:00 to 07:00)
        val startLdt = LocalDateTime.of(2026, 10, 25, 2, 0)
        val occ = createOccurrence(startLdt)
        val schedule = NotificationSchedule.atStart()

        val quietHours = QuietHours(
            enabled = true,
            startLocalTime = LocalTime.of(22, 0),
            endLocalTime = LocalTime.of(7, 0),
            policy = QuietHoursPolicy.DELAY
        )

        val calc = NotificationScheduleCalculator.calculate(occ, schedule, quietHours)

        assertEquals(startLdt, calc.nominalDateTime)
        // Delayed to 07:00 AM on the same day
        val expectedDelivery = LocalDateTime.of(2026, 10, 25, 7, 0)
        assertEquals(expectedDelivery, calc.deliveryDateTime)
        assertEquals(DateTimeUtils.toEpochMillis(expectedDelivery, utcZone), calc.triggerEpochMillis)
    }

    @Test
    fun `5 calculate with quiet hours DELAY before midnight shifts trigger to tomorrow morning`() {
        // Event at 23:30 PM (before midnight in quiet hours 22:00 to 07:00)
        val startLdt = LocalDateTime.of(2026, 10, 25, 23, 30)
        val occ = createOccurrence(startLdt)
        val schedule = NotificationSchedule.atStart()

        val quietHours = QuietHours(
            enabled = true,
            startLocalTime = LocalTime.of(22, 0),
            endLocalTime = LocalTime.of(7, 0),
            policy = QuietHoursPolicy.DELAY
        )

        val calc = NotificationScheduleCalculator.calculate(occ, schedule, quietHours)

        assertEquals(startLdt, calc.nominalDateTime)
        // Delayed to tomorrow morning at 07:00 AM
        val expectedDelivery = LocalDateTime.of(2026, 10, 26, 7, 0)
        assertEquals(expectedDelivery, calc.deliveryDateTime)
    }

    @Test
    fun `6 calculate with quiet hours SILENT policy flags notification as silent without delaying`() {
        val startLdt = LocalDateTime.of(2026, 10, 25, 23, 30)
        val occ = createOccurrence(startLdt)
        val schedule = NotificationSchedule.atStart()

        val quietHours = QuietHours(
            enabled = true,
            startLocalTime = LocalTime.of(22, 0),
            endLocalTime = LocalTime.of(7, 0),
            policy = QuietHoursPolicy.SILENT
        )

        val calc = NotificationScheduleCalculator.calculate(occ, schedule, quietHours)

        assertEquals(startLdt, calc.nominalDateTime)
        assertEquals(startLdt, calc.deliveryDateTime) // Not delayed
        assertTrue(calc.isSilent) // Flagged silent
    }

    @Test
    fun `7 calculate with quiet hours ALLOW_CRITICAL delivers critical priority on time`() {
        val startLdt = LocalDateTime.of(2026, 10, 25, 23, 30)
        val occ = createOccurrence(startLdt)
        val schedule = NotificationSchedule.atStart()

        val quietHours = QuietHours(
            enabled = true,
            startLocalTime = LocalTime.of(22, 0),
            endLocalTime = LocalTime.of(7, 0),
            policy = QuietHoursPolicy.ALLOW_CRITICAL,
            allowCritical = true
        )

        // Critical event -> bypasses quiet hours
        val calcCritical = NotificationScheduleCalculator.calculate(
            occ, schedule, quietHours, priority = Priority.CRITICAL
        )
        assertEquals(startLdt, calcCritical.deliveryDateTime)
        assertFalse(calcCritical.isSilent)

        // Normal event -> delayed to tomorrow 07:00
        val calcNormal = NotificationScheduleCalculator.calculate(
            occ, schedule, quietHours, priority = Priority.NORMAL
        )
        assertEquals(LocalDateTime.of(2026, 10, 26, 7, 0), calcNormal.deliveryDateTime)
    }

    @Test
    fun `8 calculate across DST transition calculates accurate UTC epoch millis`() {
        // America/New_York jumps forward on March 8, 2026 (2:00 -> 3:00)
        // Notification at 09:00 AM on March 9, 2026 in America/New_York
        val startLdt = LocalDateTime.of(2026, 3, 9, 9, 0)
        val occ = createOccurrence(startLdt, zone = nyZone)
        val schedule = NotificationSchedule.atStart()

        val calc = NotificationScheduleCalculator.calculate(occ, schedule)

        assertEquals(nyZone, occ.effectiveZoneId)
        // At March 9, offset is -04:00 (EDT)
        val zdt = startLdt.atZone(nyZone)
        assertEquals("-04:00", zdt.offset.id)
        assertEquals(zdt.toInstant().toEpochMilli(), calc.triggerEpochMillis)
    }

    @Test
    fun `9 calculate preserves occurrence identity and deterministic requestCode`() {
        val startLdt = LocalDateTime.of(2026, 10, 25, 14, 0)
        val occ = createOccurrence(startLdt)
        val schedule = NotificationSchedule(id = "sched-1")

        val calc = NotificationScheduleCalculator.calculate(occ, schedule)

        val expectedInstanceId = "${occ.occurrenceId}_sched-1"
        assertEquals(expectedInstanceId, calc.instanceId)
        assertEquals(expectedInstanceId.hashCode(), calc.requestCode)
        assertEquals(occ.originalStartDateTime, calc.occurrenceOriginalStartDateTime)
        assertEquals(occ.zoneId, calc.occurrenceOriginalZoneId)
    }
}
