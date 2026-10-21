package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.NotificationProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: NotificationProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<NotificationProfileEntity>)

    @Update
    suspend fun update(profile: NotificationProfileEntity)

    @Delete
    suspend fun delete(profile: NotificationProfileEntity)

    @Query("DELETE FROM notification_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM notification_profiles WHERE id = :id")
    suspend fun getById(id: String): NotificationProfileEntity?

    @Query("SELECT * FROM notification_profiles WHERE id = :id")
    fun getByIdFlow(id: String): Flow<NotificationProfileEntity?>

    @Query("SELECT * FROM notification_profiles ORDER BY name ASC")
    fun getAllFlow(): Flow<List<NotificationProfileEntity>>

    @Query("SELECT * FROM notification_profiles ORDER BY name ASC")
    suspend fun getAll(): List<NotificationProfileEntity>

    @Query("SELECT * FROM notification_profiles WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultProfile(): NotificationProfileEntity?

    @Query("UPDATE notification_profiles SET custom_sound_id = NULL, sound_source = 'DEFAULT' WHERE custom_sound_id = :soundId")
    suspend fun detachCustomSound(soundId: String)

    @Query("DELETE FROM notification_profiles")
    suspend fun deleteAll()
}
