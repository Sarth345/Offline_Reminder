package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.domain.model.Category

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    color = color,
    icon = icon,
    isCustom = isCustom,
    sortOrder = sortOrder
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    isCustom = isCustom,
    sortOrder = sortOrder
)
