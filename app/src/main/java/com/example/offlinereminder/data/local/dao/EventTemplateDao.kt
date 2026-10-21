package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.EventTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: EventTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<EventTemplateEntity>)

    @Update
    suspend fun update(template: EventTemplateEntity)

    @Delete
    suspend fun delete(template: EventTemplateEntity)

    @Query("DELETE FROM event_templates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM event_templates WHERE id = :id")
    suspend fun getById(id: String): EventTemplateEntity?

    @Query("SELECT * FROM event_templates WHERE id = :id")
    fun getByIdFlow(id: String): Flow<EventTemplateEntity?>

    @Query("SELECT * FROM event_templates ORDER BY name ASC")
    fun getAllFlow(): Flow<List<EventTemplateEntity>>

    @Query("SELECT * FROM event_templates ORDER BY name ASC")
    suspend fun getAll(): List<EventTemplateEntity>

    @Query("SELECT COUNT(*) FROM event_templates WHERE category_id = :categoryId")
    suspend fun countTemplatesByCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM event_templates WHERE event_type_id = :eventTypeId")
    suspend fun countTemplatesByEventType(eventTypeId: String): Int

    @Query("DELETE FROM event_templates")
    suspend fun deleteAll()
}
