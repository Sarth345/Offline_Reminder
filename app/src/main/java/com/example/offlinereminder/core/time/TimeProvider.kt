package com.example.offlinereminder.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Abstraction for system time queries, allowing deterministic mocking in unit tests.
 */
interface TimeProvider {
    fun nowInstant(): Instant
    fun nowLocalDateTime(zoneId: ZoneId = systemZoneId()): LocalDateTime
    fun nowLocalDate(zoneId: ZoneId = systemZoneId()): LocalDate
    fun nowLocalTime(zoneId: ZoneId = systemZoneId()): LocalTime
    fun systemZoneId(): ZoneId
}

/**
 * Default production implementation using java.time system clock.
 */
class SystemTimeProvider : TimeProvider {
    override fun nowInstant(): Instant = Instant.now()

    override fun nowLocalDateTime(zoneId: ZoneId): LocalDateTime =
        LocalDateTime.now(zoneId)

    override fun nowLocalDate(zoneId: ZoneId): LocalDate =
        LocalDate.now(zoneId)

    override fun nowLocalTime(zoneId: ZoneId): LocalTime =
        LocalTime.now(zoneId)

    override fun systemZoneId(): ZoneId = ZoneId.systemDefault()
}
