package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.relation.EventWithDetails
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    fun getByIdFlow(id: String): Flow<EventEntity?>

    // Transactional relation queries
    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventWithDetailsById(id: String): EventWithDetails?

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    fun getEventWithDetailsByIdFlow(id: String): Flow<EventWithDetails?>

    @Transaction
    @Query("SELECT * FROM events WHERE lifecycle_status = 'ACTIVE' ORDER BY start_date ASC, start_time ASC")
    fun getAllActiveWithDetailsFlow(): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM events ORDER BY start_date ASC, start_time ASC")
    fun getAllWithDetailsFlow(): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM events WHERE lifecycle_status = :status ORDER BY start_date ASC, start_time ASC")
    fun getEventsByLifecycleStatusWithDetailsFlow(status: EventLifecycleStatus): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM events WHERE lifecycle_status = 'ACTIVE' AND is_favorite = 1 ORDER BY start_date ASC")
    fun getFavoriteEventsWithDetailsFlow(): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM events WHERE lifecycle_status = 'ACTIVE' AND category_id = :categoryId ORDER BY start_date ASC")
    fun getActiveEventsByCategoryWithDetailsFlow(categoryId: String): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM events WHERE lifecycle_status = 'ACTIVE' AND event_type_id = :eventTypeId ORDER BY start_date ASC")
    fun getActiveEventsByEventTypeWithDetailsFlow(eventTypeId: String): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM events WHERE lifecycle_status = 'ACTIVE' AND parent_event_id = :parentEventId ORDER BY start_date ASC")
    fun getSubEventsWithDetailsFlow(parentEventId: String): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM events WHERE lifecycle_status = 'ACTIVE' AND start_date >= :startDate AND start_date <= :endDate ORDER BY start_date ASC")
    fun getEventsInDateRangeWithDetailsFlow(startDate: LocalDate, endDate: LocalDate): Flow<List<EventWithDetails>>

    @Query("""
        SELECT * FROM events 
        WHERE lifecycle_status = 'ACTIVE' 
          AND (has_recurrence = 1 OR (start_date <= :rangeEnd AND (end_date IS NULL OR end_date >= :rangeStart)))
        ORDER BY start_date ASC
    """)
    suspend fun getEventsForCalendarProjection(rangeStart: LocalDate, rangeEnd: LocalDate): List<EventEntity>

    // Lifecycle state management
    @Query("UPDATE events SET lifecycle_status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateLifecycleStatus(id: String, status: EventLifecycleStatus, updatedAt: Instant)

    @Query("UPDATE events SET lifecycle_status = 'TRASHED', deleted_at = :deletedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, deletedAt: Instant, updatedAt: Instant)

    @Query("UPDATE events SET lifecycle_status = 'ACTIVE', deleted_at = NULL, updated_at = :updatedAt WHERE id = :id")
    suspend fun restoreFromTrash(id: String, updatedAt: Instant)

    @Query("UPDATE events SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean, updatedAt: Instant)

    @Query("SELECT COUNT(*) FROM events WHERE lifecycle_status = 'ACTIVE'")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM events WHERE lifecycle_status = 'TRASHED'")
    suspend fun countTrashed(): Int
}
