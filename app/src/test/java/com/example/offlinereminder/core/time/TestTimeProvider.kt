package com.example.offlinereminder.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Deterministic test implementation of [TimeProvider].
 */
class TestTimeProvider(
    var currentInstant: Instant = Instant.parse("2026-10-20T10:00:00Z"),
    var currentZoneId: ZoneId = ZoneId.of("UTC")
) : TimeProvider {
    override fun nowInstant(): Instant = currentInstant

    override fun nowLocalDateTime(zoneId: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(currentInstant, zoneId)

    override fun nowLocalDate(zoneId: ZoneId): LocalDate =
        nowLocalDateTime(zoneId).toLocalDate()

    override fun nowLocalTime(zoneId: ZoneId): LocalTime =
        nowLocalDateTime(zoneId).toLocalTime()

    override fun systemZoneId(): ZoneId = currentZoneId
}
