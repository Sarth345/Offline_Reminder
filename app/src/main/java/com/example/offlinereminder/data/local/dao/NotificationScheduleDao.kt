package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: NotificationScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<NotificationScheduleEntity>)

    @Update
    suspend fun update(schedule: NotificationScheduleEntity)

    @Delete
    suspend fun delete(schedule: NotificationScheduleEntity)

    @Query("DELETE FROM notification_schedules WHERE event_id = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("SELECT * FROM notification_schedules WHERE event_id = :eventId")
    suspend fun getSchedulesForEvent(eventId: String): List<NotificationScheduleEntity>

    @Query("SELECT * FROM notification_schedules WHERE id = :id")
    suspend fun getById(id: String): NotificationScheduleEntity?

    @Query("SELECT * FROM notification_schedules WHERE event_id = :eventId")
    fun getSchedulesForEventFlow(eventId: String): Flow<List<NotificationScheduleEntity>>

    @Query("SELECT * FROM notification_schedules")
    suspend fun getAll(): List<NotificationScheduleEntity>

    @Query("UPDATE notification_schedules SET custom_sound_id = NULL, sound_source = 'DEFAULT' WHERE custom_sound_id = :soundId")
    suspend fun detachCustomSound(soundId: String)

    @Query("UPDATE notification_schedules SET profile_id = NULL WHERE profile_id = :profileId")
    suspend fun detachProfile(profileId: String)

    @Query("DELETE FROM notification_schedules")
    suspend fun deleteAll()
}
