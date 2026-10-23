package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.SavedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<SavedLocationEntity>)

    @Update
    suspend fun update(location: SavedLocationEntity)

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getById(id: String): SavedLocationEntity?

    @Query("SELECT * FROM saved_locations WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): SavedLocationEntity?

    @Query("SELECT * FROM saved_locations ORDER BY is_favorite DESC, name ASC")
    fun getAllFlow(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations ORDER BY is_favorite DESC, name ASC")
    suspend fun getAll(): List<SavedLocationEntity>

    @Query("SELECT * FROM saved_locations WHERE is_favorite = 1 ORDER BY name ASC")
    fun getFavoritesFlow(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations WHERE name LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' ORDER BY is_favorite DESC, name ASC")
    fun searchFlow(query: String): Flow<List<SavedLocationEntity>>

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE saved_locations SET is_favorite = CASE WHEN is_favorite = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("DELETE FROM saved_locations")
    suspend fun deleteAll()
}
