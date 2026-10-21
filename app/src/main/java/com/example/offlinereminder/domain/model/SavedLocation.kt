package com.example.offlinereminder.domain.model

import java.time.Instant

/**
 * Reusable user-saved local location.
 * Stored locally with stable ID and zero network dependencies.
 */
data class SavedLocation(
    val id: String,
    val name: String,
    val address: String = "",
    val building: String = "",
    val room: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toLocationData(): LocationData = LocationData(
        id = id,
        placeName = name,
        addressText = address,
        building = building,
        room = room,
        latitude = latitude,
        longitude = longitude,
        notes = notes,
        isSavedLocation = true
    )
}
