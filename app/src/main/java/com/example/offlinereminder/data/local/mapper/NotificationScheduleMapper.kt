package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.domain.model.NotificationSchedule
import java.time.Duration

fun NotificationScheduleEntity.toDomain(): NotificationSchedule = NotificationSchedule(
    id = id,
    offsetDuration = Duration.ofMillis(offsetDurationMillis),
    triggerType = triggerType,
    profileId = profileId
)

fun NotificationSchedule.toEntity(eventId: String): NotificationScheduleEntity = NotificationScheduleEntity(
    id = id,
    eventId = eventId,
    offsetDurationMillis = offsetDuration.toMillis(),
    triggerType = triggerType,
    profileId = profileId
)
