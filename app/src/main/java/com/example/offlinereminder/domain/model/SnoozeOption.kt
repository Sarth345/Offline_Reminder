package com.example.offlinereminder.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class StandardSnooze(val duration: Duration) {
    MINUTES_5(Duration.ofMinutes(5)),
    MINUTES_10(Duration.ofMinutes(10)),
    MINUTES_15(Duration.ofMinutes(15)),
    MINUTES_30(Duration.ofMinutes(30)),
    HOURS_1(Duration.ofHours(1)),
    HOURS_2(Duration.ofHours(2));

    fun calculateSnoozeInstant(now: Instant): Instant = now.plus(duration)
}

/**
 * Snooze calculator accommodating standard offsets, custom durations, and "Tomorrow".
 */
object SnoozeCalculator {

    val DEFAULT_SNOOZE_DURATION: Duration = Duration.ofMinutes(10)

    fun calculateTomorrow(
        today: LocalDate,
        targetTime: LocalTime = LocalTime.of(9, 0),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Instant {
        return today.plusDays(1).atTime(targetTime).atZone(zoneId).toInstant()
    }

    fun calculateCustom(
        now: Instant,
        duration: Duration
    ): Instant {
        return now.plus(duration)
    }
}
