package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for managing user-saved local locations.
 * Completely decoupled from Android, Room, and network APIs.
 */
interface SavedLocationRepository {
    fun getAllLocationsFlow(): Flow<List<SavedLocation>>
    fun getFavoriteLocationsFlow(): Flow<List<SavedLocation>>
    fun searchLocationsFlow(query: String): Flow<List<SavedLocation>>
    suspend fun getLocationById(id: String): SavedLocation?
    suspend fun getLocationByName(name: String): SavedLocation?
    suspend fun saveLocation(location: SavedLocation): Result<Unit>
    suspend fun deleteLocation(id: String): Result<Unit>
    suspend fun toggleFavorite(id: String): Result<Unit>
}
