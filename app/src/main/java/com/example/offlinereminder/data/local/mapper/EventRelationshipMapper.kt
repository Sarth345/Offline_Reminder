package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.EventRelationshipEntity
import com.example.offlinereminder.domain.model.EventRelationship

fun EventRelationshipEntity.toDomain(): EventRelationship = EventRelationship(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = type
)

fun EventRelationship.toEntity(): EventRelationshipEntity = EventRelationshipEntity(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = type
)
