package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.NotificationInstanceEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface NotificationInstanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instance: NotificationInstanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(instances: List<NotificationInstanceEntity>)

    @Update
    suspend fun update(instance: NotificationInstanceEntity)

    @Delete
    suspend fun delete(instance: NotificationInstanceEntity)

    @Query("DELETE FROM notification_instances WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notification_instances WHERE event_id = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("SELECT * FROM notification_instances WHERE id = :id")
    suspend fun getById(id: String): NotificationInstanceEntity?

    @Query("SELECT * FROM notification_instances WHERE event_id = :eventId")
    fun getInstancesForEventFlow(eventId: String): Flow<List<NotificationInstanceEntity>>

    @Query("SELECT * FROM notification_instances WHERE status = 'SCHEDULED' AND trigger_epoch_millis <= :nowMillis ORDER BY trigger_epoch_millis ASC")
    suspend fun getPendingDueInstances(nowMillis: Long): List<NotificationInstanceEntity>

    @Query("SELECT * FROM notification_instances WHERE status = 'SCHEDULED' ORDER BY trigger_epoch_millis ASC")
    fun getAllScheduledFlow(): Flow<List<NotificationInstanceEntity>>

    @Query("UPDATE notification_instances SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("""
        UPDATE notification_instances 
        SET status = 'SNOOZED', 
            snooze_count = snooze_count + 1, 
            snooze_until = :snoozeUntil, 
            trigger_epoch_millis = :newTriggerMillis 
        WHERE id = :id
    """)
    suspend fun snooze(id: String, snoozeUntil: Instant, newTriggerMillis: Long)

    @Query("SELECT COUNT(*) FROM notification_instances WHERE status = 'SCHEDULED'")
    suspend fun countScheduled(): Int
}
