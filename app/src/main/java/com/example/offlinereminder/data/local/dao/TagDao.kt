package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventTagCrossRef
import com.example.offlinereminder.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity)

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllFlow(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE normalized_name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchTagsFlow(query: String): Flow<List<TagEntity>>

    // Cross reference operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: EventTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<EventTagCrossRef>)

    @Delete
    suspend fun deleteCrossRef(crossRef: EventTagCrossRef)

    @Query("DELETE FROM event_tag_cross_ref WHERE event_id = :eventId AND tag_id = :tagId")
    suspend fun removeTagFromEvent(eventId: String, tagId: String)

    @Query("DELETE FROM event_tag_cross_ref WHERE event_id = :eventId")
    suspend fun removeAllTagsFromEvent(eventId: String)

    @Query("SELECT tag_id FROM event_tag_cross_ref WHERE event_id = :eventId")
    suspend fun getTagIdsForEvent(eventId: String): List<String>

    @Query("SELECT tag_id FROM event_tag_cross_ref WHERE event_id = :eventId")
    fun getTagIdsForEventFlow(eventId: String): Flow<List<String>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN event_tag_cross_ref x ON t.id = x.tag_id
        WHERE x.event_id = :eventId
        ORDER BY t.name ASC
    """)
    fun getTagsForEventFlow(eventId: String): Flow<List<TagEntity>>

    @Query("""
        SELECT e.* FROM events e
        INNER JOIN event_tag_cross_ref x ON e.id = x.event_id
        WHERE x.tag_id = :tagId AND e.lifecycle_status = 'ACTIVE'
        ORDER BY e.start_date ASC
    """)
    fun getActiveEventsForTagFlow(tagId: String): Flow<List<EventEntity>>

    @androidx.room.Transaction
    @Query("""
        SELECT e.* FROM events e
        INNER JOIN event_tag_cross_ref x ON e.id = x.event_id
        WHERE x.tag_id = :tagId AND e.lifecycle_status = 'ACTIVE'
        ORDER BY e.start_date ASC
    """)
    fun getActiveEventsWithDetailsForTagFlow(tagId: String): Flow<List<com.example.offlinereminder.data.local.relation.EventWithDetails>>

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int
}
