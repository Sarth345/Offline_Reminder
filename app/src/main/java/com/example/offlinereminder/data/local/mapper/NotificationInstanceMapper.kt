package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.NotificationInstanceEntity
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import java.time.ZoneId

fun NotificationInstanceEntity.toDomain(): NotificationInstance = NotificationInstance(
    id = id,
    eventId = eventId,
    occurrenceOriginalStartDateTime = occurrenceOriginalStartDateTime,
    occurrenceOriginalZoneId = ZoneId.of(occurrenceOriginalZoneId),
    scheduleId = scheduleId,
    triggerEpochMillis = triggerEpochMillis,
    status = runCatching { NotificationInstanceStatus.valueOf(status) }.getOrDefault(NotificationInstanceStatus.SCHEDULED),
    snoozeCount = snoozeCount,
    snoozeUntil = snoozeUntil,
    requestCode = requestCode
)

fun NotificationInstance.toEntity(): NotificationInstanceEntity = NotificationInstanceEntity(
    id = id,
    eventId = eventId,
    occurrenceOriginalStartDateTime = occurrenceOriginalStartDateTime,
    occurrenceOriginalZoneId = occurrenceOriginalZoneId.id,
    scheduleId = scheduleId,
    triggerEpochMillis = triggerEpochMillis,
    status = status.name,
    snoozeCount = snoozeCount,
    snoozeUntil = snoozeUntil,
    requestCode = requestCode
)
