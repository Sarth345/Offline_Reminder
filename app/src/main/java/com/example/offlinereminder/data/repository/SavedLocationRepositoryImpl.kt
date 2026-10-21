package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.SavedLocationDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.SavedLocation
import com.example.offlinereminder.domain.repository.SavedLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete implementation of [SavedLocationRepository] using Room.
 */
class SavedLocationRepositoryImpl(
    private val savedLocationDao: SavedLocationDao
) : SavedLocationRepository {

    override fun getAllLocationsFlow(): Flow<List<SavedLocation>> =
        savedLocationDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override fun getFavoriteLocationsFlow(): Flow<List<SavedLocation>> =
        savedLocationDao.getFavoritesFlow().map { list -> list.map { it.toDomain() } }

    override fun searchLocationsFlow(query: String): Flow<List<SavedLocation>> =
        savedLocationDao.searchFlow(query).map { list -> list.map { it.toDomain() } }

    override suspend fun getLocationById(id: String): SavedLocation? =
        savedLocationDao.getById(id)?.toDomain()

    override suspend fun getLocationByName(name: String): SavedLocation? =
        savedLocationDao.getByName(name)?.toDomain()

    override suspend fun saveLocation(location: SavedLocation): Result<Unit> = Result.runCatching {
        savedLocationDao.insert(location.toEntity())
    }

    override suspend fun deleteLocation(id: String): Result<Unit> = Result.runCatching {
        savedLocationDao.deleteById(id)
    }

    override suspend fun toggleFavorite(id: String): Result<Unit> = Result.runCatching {
        savedLocationDao.toggleFavorite(id)
    }
}
