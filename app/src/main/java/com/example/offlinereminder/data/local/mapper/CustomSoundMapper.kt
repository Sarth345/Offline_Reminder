package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.CustomSoundEntity
import com.example.offlinereminder.domain.model.CustomSoundMetadata

fun CustomSoundEntity.toDomain(): CustomSoundMetadata = CustomSoundMetadata(
    id = id,
    title = title,
    fileName = fileName,
    mimeType = mimeType,
    fileSizeBytes = fileSizeBytes,
    durationMillis = durationMillis,
    addedAt = addedAt
)

fun CustomSoundMetadata.toEntity(): CustomSoundEntity = CustomSoundEntity(
    id = id,
    title = title,
    fileName = fileName,
    relativePath = "sounds/$fileName",
    mimeType = mimeType,
    fileSizeBytes = fileSizeBytes,
    durationMillis = durationMillis,
    addedAt = addedAt
)
