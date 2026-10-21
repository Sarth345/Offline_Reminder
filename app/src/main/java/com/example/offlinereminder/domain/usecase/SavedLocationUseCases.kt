package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.SavedLocation
import com.example.offlinereminder.domain.repository.SavedLocationRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Retrieves all saved locations as a reactive stream.
 */
class GetSavedLocationsUseCase(
    private val repository: SavedLocationRepository
) {
    operator fun invoke(): Flow<List<SavedLocation>> = repository.getAllLocationsFlow()

    fun getFavorites(): Flow<List<SavedLocation>> = repository.getFavoriteLocationsFlow()

    fun search(query: String): Flow<List<SavedLocation>> = repository.searchLocationsFlow(query)
}

/**
 * Saves or updates a local saved location.
 */
class SaveSavedLocationUseCase(
    private val repository: SavedLocationRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        id: String? = null,
        name: String,
        address: String = "",
        building: String = "",
        room: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        notes: String = "",
        isFavorite: Boolean = false
    ): Result<SavedLocation> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.Failure(IllegalArgumentException("Location name cannot be blank"))
        }

        val now = timeProvider.nowInstant()
        val locationId = id ?: UUID.randomUUID().toString()
        val existing = repository.getLocationById(locationId)

        val location = SavedLocation(
            id = locationId,
            name = trimmedName,
            address = address.trim(),
            building = building.trim(),
            room = room.trim(),
            latitude = latitude,
            longitude = longitude,
            notes = notes.trim(),
            isFavorite = isFavorite,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        return when (val result = repository.saveLocation(location)) {
            is Result.Success -> Result.Success(location)
            is Result.Failure -> result
        }
    }
}

/**
 * Deletes a saved location by ID.
 */
class DeleteSavedLocationUseCase(
    private val repository: SavedLocationRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteLocation(id)
}

/**
 * Toggles the favorite status of a saved location.
 */
class ToggleSavedLocationFavoriteUseCase(
    private val repository: SavedLocationRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.toggleFavorite(id)
}
