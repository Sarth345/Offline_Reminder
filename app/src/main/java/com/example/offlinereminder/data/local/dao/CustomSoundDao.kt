package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.CustomSoundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSoundDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sound: CustomSoundEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sounds: List<CustomSoundEntity>)

    @Update
    suspend fun update(sound: CustomSoundEntity)

    @Delete
    suspend fun delete(sound: CustomSoundEntity)

    @Query("DELETE FROM custom_sounds WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM custom_sounds WHERE id = :id")
    suspend fun getById(id: String): CustomSoundEntity?

    @Query("SELECT * FROM custom_sounds WHERE id = :id")
    fun getByIdSync(id: String): CustomSoundEntity?

    @Query("SELECT * FROM custom_sounds WHERE id = :id")
    fun getByIdFlow(id: String): Flow<CustomSoundEntity?>

    @Query("SELECT * FROM custom_sounds ORDER BY title ASC")
    fun getAllFlow(): Flow<List<CustomSoundEntity>>

    @Query("SELECT * FROM custom_sounds ORDER BY title ASC")
    suspend fun getAll(): List<CustomSoundEntity>

    @Query("DELETE FROM custom_sounds")
    suspend fun deleteAll()
}
