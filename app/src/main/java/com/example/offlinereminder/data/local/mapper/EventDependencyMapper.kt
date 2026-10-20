package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.EventDependencyEntity
import com.example.offlinereminder.domain.model.EventDependency

fun EventDependencyEntity.toDomain(): EventDependency = EventDependency(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = type
)

fun EventDependency.toEntity(): EventDependencyEntity = EventDependencyEntity(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = type
)
