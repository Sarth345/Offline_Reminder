package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.OccurrenceExceptionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface OccurrenceExceptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exception: OccurrenceExceptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exceptions: List<OccurrenceExceptionEntity>)

    @Update
    suspend fun update(exception: OccurrenceExceptionEntity)

    @Delete
    suspend fun delete(exception: OccurrenceExceptionEntity)

    @Query("DELETE FROM occurrence_exceptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM occurrence_exceptions WHERE occurrence_id = :occurrenceId")
    suspend fun deleteByOccurrenceId(occurrenceId: String)

    @Query("DELETE FROM occurrence_exceptions WHERE event_id = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("SELECT * FROM occurrence_exceptions WHERE occurrence_id = :occurrenceId LIMIT 1")
    suspend fun getByOccurrenceId(occurrenceId: String): OccurrenceExceptionEntity?

    @Query("SELECT * FROM occurrence_exceptions WHERE occurrence_id = :occurrenceId LIMIT 1")
    fun getByOccurrenceIdFlow(occurrenceId: String): Flow<OccurrenceExceptionEntity?>

    @Query("SELECT * FROM occurrence_exceptions WHERE event_id = :eventId ORDER BY original_start_date_time ASC")
    suspend fun getExceptionsForEvent(eventId: String): List<OccurrenceExceptionEntity>

    @Query("SELECT * FROM occurrence_exceptions WHERE event_id = :eventId ORDER BY original_start_date_time ASC")
    fun getExceptionsForEventFlow(eventId: String): Flow<List<OccurrenceExceptionEntity>>

    @Query("""
        SELECT * FROM occurrence_exceptions 
        WHERE (original_start_date_time >= :startLdt AND original_start_date_time <= :endLdt)
           OR (rescheduled_start_date_time IS NOT NULL AND rescheduled_start_date_time >= :startLdt AND rescheduled_start_date_time <= :endLdt)
        ORDER BY original_start_date_time ASC
    """)
    suspend fun getExceptionsInRange(startLdt: LocalDateTime, endLdt: LocalDateTime): List<OccurrenceExceptionEntity>

    @Query("SELECT COUNT(*) FROM occurrence_exceptions")
    suspend fun count(): Int
}
