package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.OccurrenceExceptionEntity
import com.example.offlinereminder.domain.model.OccurrenceException
import java.time.ZoneId

fun OccurrenceExceptionEntity.toDomain(): OccurrenceException = OccurrenceException(
    id = id,
    eventId = eventId,
    originalStartDateTime = originalStartDateTime,
    originalZoneId = ZoneId.of(originalZoneId),
    status = status,
    rescheduledStartDateTime = rescheduledStartDateTime,
    rescheduledEndDateTime = rescheduledEndDateTime,
    rescheduledZoneId = rescheduledZoneId?.let { ZoneId.of(it) },
    actualExecutionDateTime = actualExecutionDateTime,
    completedAt = completedAt,
    titleOverride = titleOverride,
    notesOverride = notesOverride
)

fun OccurrenceException.toEntity(): OccurrenceExceptionEntity = OccurrenceExceptionEntity(
    id = id,
    eventId = eventId,
    originalStartDateTime = originalStartDateTime,
    originalZoneId = originalZoneId.id,
    occurrenceId = occurrenceId,
    status = status,
    rescheduledStartDateTime = rescheduledStartDateTime,
    rescheduledEndDateTime = rescheduledEndDateTime,
    rescheduledZoneId = rescheduledZoneId?.id,
    actualExecutionDateTime = actualExecutionDateTime,
    completedAt = completedAt,
    titleOverride = titleOverride,
    notesOverride = notesOverride
)
