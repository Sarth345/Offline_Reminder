package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offlinereminder.data.local.entity.EventDependencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDependencyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dependency: EventDependencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dependencies: List<EventDependencyEntity>)

    @Delete
    suspend fun delete(dependency: EventDependencyEntity)

    @Query("DELETE FROM event_dependencies WHERE source_event_id = :sourceEventId AND target_event_id = :targetEventId")
    suspend fun deleteDependency(sourceEventId: String, targetEventId: String)

    @Query("DELETE FROM event_dependencies WHERE source_event_id = :eventId OR target_event_id = :eventId")
    suspend fun deleteAllForEvent(eventId: String)

    @Query("SELECT * FROM event_dependencies WHERE source_event_id = :sourceEventId")
    fun getDependenciesForSourceFlow(sourceEventId: String): Flow<List<EventDependencyEntity>>

    @Query("SELECT * FROM event_dependencies WHERE target_event_id = :targetEventId")
    fun getDependentsForTargetFlow(targetEventId: String): Flow<List<EventDependencyEntity>>
}
