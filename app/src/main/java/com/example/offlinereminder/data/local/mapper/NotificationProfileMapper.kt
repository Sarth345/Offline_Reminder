package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.NotificationProfileEntity
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType

fun NotificationProfileEntity.toDomain(): NotificationProfile {
    val effectiveSound = if (soundSource == SoundSourceType.CUSTOM && customSoundId.isNullOrBlank()) {
        SoundReference(SoundSourceType.DEFAULT)
    } else {
        SoundReference(
            type = soundSource,
            customSoundId = customSoundId,
            systemUriString = systemSoundUri
        )
    }
    return NotificationProfile(
        id = id,
        name = name,
        deliveryType = deliveryType,
        sound = effectiveSound,
        vibrationEnabled = vibrationEnabled,
        defaultSnoozeDurationMinutes = defaultSnoozeDurationMinutes,
        isDefault = isDefault
    )
}

fun NotificationProfile.toEntity(): NotificationProfileEntity {
    val effectiveType = if (sound.type == SoundSourceType.CUSTOM && sound.customSoundId.isNullOrBlank()) {
        SoundSourceType.DEFAULT
    } else {
        sound.type
    }
    val effectiveCustomSoundId = if (effectiveType == SoundSourceType.CUSTOM) sound.customSoundId else null

    return NotificationProfileEntity(
        id = id,
        name = name,
        deliveryType = deliveryType,
        soundSource = effectiveType,
        customSoundId = effectiveCustomSoundId,
        systemSoundUri = sound.systemUriString,
        vibrationEnabled = vibrationEnabled,
        defaultSnoozeDurationMinutes = defaultSnoozeDurationMinutes,
        isDefault = isDefault
    )
}
