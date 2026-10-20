package com.example.offlinereminder.domain.model

/**
 * Structured local location information for an event.
 * Operates purely offline without network geocoding APIs.
 */
data class LocationData(
    val placeName: String = "",
    val addressText: String = "",
    val building: String = "",
    val room: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val isNotEmpty: Boolean
        get() = placeName.isNotBlank() ||
                addressText.isNotBlank() ||
                building.isNotBlank() ||
                room.isNotBlank() ||
                (latitude != null && longitude != null)
}
