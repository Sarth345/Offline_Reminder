package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.domain.model.EventGroup

fun EventGroupEntity.toDomain(): EventGroup = EventGroup(
    id = id,
    name = name,
    description = description,
    color = color,
    icon = icon,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun EventGroup.toEntity(): EventGroupEntity = EventGroupEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    icon = icon,
    createdAt = createdAt,
    updatedAt = updatedAt
)
