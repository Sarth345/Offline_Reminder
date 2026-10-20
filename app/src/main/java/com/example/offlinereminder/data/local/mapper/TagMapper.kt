package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.TagEntity
import com.example.offlinereminder.domain.model.Tag

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    normalizedName = normalizedName,
    color = color
)

fun Tag.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    color = color
)
