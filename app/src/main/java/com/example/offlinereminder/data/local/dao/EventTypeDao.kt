package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTypeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(eventType: EventTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(eventTypes: List<EventTypeEntity>)

    @Upsert
    suspend fun upsert(eventType: EventTypeEntity)

    @Upsert
    suspend fun upsertAll(eventTypes: List<EventTypeEntity>)

    @Update
    suspend fun update(eventType: EventTypeEntity)

    @Delete
    suspend fun delete(eventType: EventTypeEntity)

    @Query("DELETE FROM event_types WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM event_types WHERE id = :id")
    suspend fun getById(id: String): EventTypeEntity?

    @Query("SELECT * FROM event_types WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): EventTypeEntity?

    @Query("SELECT * FROM event_types ORDER BY is_pinned DESC, name ASC")
    fun getAllFlow(): Flow<List<EventTypeEntity>>

    @Query("SELECT * FROM event_types WHERE category_id = :categoryId ORDER BY name ASC")
    fun getByCategoryFlow(categoryId: String): Flow<List<EventTypeEntity>>

    @Query("SELECT * FROM event_types WHERE name LIKE '%' || :query || '%' OR aliases LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchByNameOrAliasFlow(query: String): Flow<List<EventTypeEntity>>

    @Query("SELECT COUNT(*) FROM event_types")
    suspend fun count(): Int

    @Query("SELECT * FROM event_types ORDER BY is_pinned DESC, name ASC")
    suspend fun getAll(): List<EventTypeEntity>

    @Query("DELETE FROM event_types")
    suspend fun deleteAll()
}
