package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.SavedLocationEntity
import com.example.offlinereminder.domain.model.SavedLocation

fun SavedLocationEntity.toDomain(): SavedLocation = SavedLocation(
    id = id,
    name = name,
    address = address,
    building = building,
    room = room,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SavedLocation.toEntity(): SavedLocationEntity = SavedLocationEntity(
    id = id,
    name = name,
    address = address,
    building = building,
    room = room,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt
)
