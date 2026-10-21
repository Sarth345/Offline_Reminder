package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import java.time.Duration

fun NotificationScheduleEntity.toDomain(): NotificationSchedule {
    val effectiveSound = if (soundSource == SoundSourceType.CUSTOM && customSoundId.isNullOrBlank()) {
        SoundReference(SoundSourceType.DEFAULT)
    } else {
        SoundReference(
            type = soundSource,
            customSoundId = customSoundId,
            systemUriString = systemSoundUri
        )
    }
    return NotificationSchedule(
        id = id,
        offsetDuration = Duration.ofMillis(offsetDurationMillis),
        triggerType = triggerType,
        profileId = profileId,
        deliveryType = deliveryType,
        sound = effectiveSound
    )
}

fun NotificationSchedule.toEntity(eventId: String): NotificationScheduleEntity {
    val effectiveType = if (sound.type == SoundSourceType.CUSTOM && sound.customSoundId.isNullOrBlank()) {
        SoundSourceType.DEFAULT
    } else {
        sound.type
    }
    val effectiveCustomSoundId = if (effectiveType == SoundSourceType.CUSTOM) sound.customSoundId else null

    return NotificationScheduleEntity(
        id = id,
        eventId = eventId,
        offsetDurationMillis = offsetDuration.toMillis(),
        triggerType = triggerType,
        profileId = profileId,
        deliveryType = deliveryType,
        soundSource = effectiveType,
        customSoundId = effectiveCustomSoundId,
        systemSoundUri = sound.systemUriString
    )
}
