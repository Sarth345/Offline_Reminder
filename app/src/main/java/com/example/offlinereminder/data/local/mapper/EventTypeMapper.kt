package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.domain.model.EventType

fun EventTypeEntity.toDomain(): EventType = EventType(
    id = id,
    name = name,
    key = key,
    categoryId = categoryId,
    defaultColor = defaultColor,
    defaultIcon = defaultIcon,
    aliases = aliases,
    isCustom = isCustom,
    isPinned = isPinned,
    createdAt = createdAt
)

fun EventType.toEntity(): EventTypeEntity = EventTypeEntity(
    id = id,
    name = name,
    key = key,
    categoryId = categoryId,
    defaultColor = defaultColor,
    defaultIcon = defaultIcon,
    aliases = aliases,
    isCustom = isCustom,
    isPinned = isPinned,
    createdAt = createdAt
)
