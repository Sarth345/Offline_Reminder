package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offlinereminder.data.local.entity.EventRelationshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventRelationshipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relationship: EventRelationshipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(relationships: List<EventRelationshipEntity>)

    @Delete
    suspend fun delete(relationship: EventRelationshipEntity)

    @Query("DELETE FROM event_relationships WHERE source_event_id = :sourceEventId AND target_event_id = :targetEventId")
    suspend fun deleteRelationship(sourceEventId: String, targetEventId: String)

    @Query("DELETE FROM event_relationships WHERE source_event_id = :eventId OR target_event_id = :eventId")
    suspend fun deleteAllForEvent(eventId: String)

    @Query("SELECT * FROM event_relationships WHERE source_event_id = :sourceEventId")
    fun getRelationshipsForSourceFlow(sourceEventId: String): Flow<List<EventRelationshipEntity>>

    @Query("SELECT * FROM event_relationships WHERE target_event_id = :targetEventId")
    fun getRelationshipsForTargetFlow(targetEventId: String): Flow<List<EventRelationshipEntity>>
}
