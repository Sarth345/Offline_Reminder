package com.example.offlinereminder.domain.model

import java.time.LocalDateTime
import java.time.LocalTime

enum class QuietHoursPolicy {
    DELAY,
    SILENT,
    ALLOW_CRITICAL
}

/**
 * Local quiet-hours policy definition.
 * Governs how notification alarms and delivery are handled during user-defined rest periods.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val startLocalTime: LocalTime = LocalTime.of(22, 0),
    val endLocalTime: LocalTime = LocalTime.of(7, 0),
    val policy: QuietHoursPolicy = QuietHoursPolicy.DELAY,
    val allowCritical: Boolean = true
) {

    /**
     * Determines whether a given [localTime] falls inside the quiet hours period.
     * Accurately supports both same-day windows (e.g. 13:00..15:00) and overnight windows (e.g. 22:00..07:00).
     */
    fun isInQuietHours(localTime: LocalTime): Boolean {
        if (!enabled) return false
        return if (startLocalTime.isBefore(endLocalTime)) {
            !localTime.isBefore(startLocalTime) && localTime.isBefore(endLocalTime)
        } else {
            !localTime.isBefore(startLocalTime) || localTime.isBefore(endLocalTime)
        }
    }

    /**
     * Calculates the next valid delivery date-time when [dateTime] falls within quiet hours.
     * Returns the original [dateTime] if it does not fall within quiet hours or quiet hours is disabled.
     */
    fun nextValidDeliveryTime(dateTime: LocalDateTime): LocalDateTime {
        if (!enabled || !isInQuietHours(dateTime.toLocalTime())) {
            return dateTime
        }

        val date = dateTime.toLocalDate()
        return if (startLocalTime.isBefore(endLocalTime)) {
            // Same-day quiet hours window
            date.atTime(endLocalTime)
        } else {
            // Overnight quiet hours window
            if (!dateTime.toLocalTime().isBefore(startLocalTime)) {
                // Before midnight, ends tomorrow at endLocalTime
                date.plusDays(1).atTime(endLocalTime)
            } else {
                // After midnight, ends today at endLocalTime
                date.atTime(endLocalTime)
            }
        }
    }
}
