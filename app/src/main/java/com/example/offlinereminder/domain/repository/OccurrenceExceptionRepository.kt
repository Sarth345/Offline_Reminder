package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.OccurrenceException
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Pure Kotlin repository interface for OccurrenceException persistence.
 */
interface OccurrenceExceptionRepository {
    fun getExceptionsForEvent(eventId: String): Flow<List<OccurrenceException>>
    suspend fun getExceptionByOccurrenceId(occurrenceId: String): OccurrenceException?
    fun getExceptionByOccurrenceIdFlow(occurrenceId: String): Flow<OccurrenceException?>
    suspend fun getExceptionsInRange(startLdt: LocalDateTime, endLdt: LocalDateTime): List<OccurrenceException>
    suspend fun getAllExceptions(): List<OccurrenceException>
    fun getAllExceptionsFlow(): Flow<List<OccurrenceException>>
    suspend fun saveException(exception: OccurrenceException): Result<Unit>
    suspend fun deleteException(id: String): Result<Unit>
    suspend fun deleteByOccurrenceId(occurrenceId: String): Result<Unit>
}
