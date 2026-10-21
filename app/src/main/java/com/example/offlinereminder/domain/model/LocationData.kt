package com.example.offlinereminder.domain.model

/**
 * Structured local location information for an event.
 * Operates purely offline without network geocoding APIs.
 */
data class LocationData(
    val id: String? = null,
    val placeName: String = "",
    val addressText: String = "",
    val building: String = "",
    val room: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = "",
    val isSavedLocation: Boolean = false
) {
    val name: String get() = placeName
    val address: String get() = addressText

    val isNotEmpty: Boolean
        get() = placeName.isNotBlank() ||
                addressText.isNotBlank() ||
                building.isNotBlank() ||
                room.isNotBlank() ||
                notes.isNotBlank() ||
                (latitude != null && longitude != null)
}
