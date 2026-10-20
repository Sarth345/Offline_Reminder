package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.AttachmentEntity
import com.example.offlinereminder.domain.model.AttachmentMetadata

fun AttachmentEntity.toDomain(): AttachmentMetadata = AttachmentMetadata(
    id = id,
    localUri = localUri,
    fileName = fileName,
    mimeType = mimeType,
    fileSizeBytes = fileSizeBytes,
    createdAt = createdAt
)

fun AttachmentMetadata.toEntity(eventId: String): AttachmentEntity = AttachmentEntity(
    id = id,
    eventId = eventId,
    localUri = localUri,
    fileName = fileName,
    mimeType = mimeType,
    fileSizeBytes = fileSizeBytes,
    createdAt = createdAt
)
