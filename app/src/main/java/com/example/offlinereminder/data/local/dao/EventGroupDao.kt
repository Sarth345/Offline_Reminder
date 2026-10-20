package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.data.local.relation.EventGroupWithEvents
import kotlinx.coroutines.flow.Flow

@Dao
interface EventGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: EventGroupEntity)

    @Update
    suspend fun update(group: EventGroupEntity)

    @Delete
    suspend fun delete(group: EventGroupEntity)

    @Query("DELETE FROM event_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM event_groups WHERE id = :id")
    suspend fun getById(id: String): EventGroupEntity?

    @Query("SELECT * FROM event_groups WHERE id = :id")
    fun getByIdFlow(id: String): Flow<EventGroupEntity?>

    @Query("SELECT * FROM event_groups ORDER BY name ASC")
    fun getAllFlow(): Flow<List<EventGroupEntity>>

    @Query("SELECT * FROM event_groups WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchByNameFlow(query: String): Flow<List<EventGroupEntity>>

    // Cross-reference operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: EventGroupCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<EventGroupCrossRef>)

    @Delete
    suspend fun deleteCrossRef(crossRef: EventGroupCrossRef)

    @Query("DELETE FROM event_group_cross_ref WHERE event_id = :eventId AND group_id = :groupId")
    suspend fun removeEventFromGroup(eventId: String, groupId: String)

    @Query("DELETE FROM event_group_cross_ref WHERE event_id = :eventId")
    suspend fun removeAllGroupsFromEvent(eventId: String)

    @Query("SELECT group_id FROM event_group_cross_ref WHERE event_id = :eventId")
    suspend fun getGroupIdsForEvent(eventId: String): List<String>

    @Query("SELECT group_id FROM event_group_cross_ref WHERE event_id = :eventId")
    fun getGroupIdsForEventFlow(eventId: String): Flow<List<String>>

    @Query("""
        SELECT eg.* FROM event_groups eg
        INNER JOIN event_group_cross_ref x ON eg.id = x.group_id
        WHERE x.event_id = :eventId
        ORDER BY eg.name ASC
    """)
    fun getGroupsForEventFlow(eventId: String): Flow<List<EventGroupEntity>>

    @Query("""
        SELECT e.* FROM events e
        INNER JOIN event_group_cross_ref x ON e.id = x.event_id
        WHERE x.group_id = :groupId AND e.lifecycle_status = 'ACTIVE'
        ORDER BY e.start_date ASC
    """)
    fun getActiveEventsForGroupFlow(groupId: String): Flow<List<EventEntity>>

    @Transaction
    @Query("""
        SELECT e.* FROM events e
        INNER JOIN event_group_cross_ref x ON e.id = x.event_id
        WHERE x.group_id = :groupId AND e.lifecycle_status = 'ACTIVE'
        ORDER BY e.start_date ASC
    """)
    fun getActiveEventsWithDetailsForGroupFlow(groupId: String): Flow<List<com.example.offlinereminder.data.local.relation.EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM event_groups WHERE id = :groupId")
    fun getGroupWithEventsFlow(groupId: String): Flow<EventGroupWithEvents?>

    @Query("SELECT COUNT(*) FROM event_groups")
    suspend fun count(): Int
}
