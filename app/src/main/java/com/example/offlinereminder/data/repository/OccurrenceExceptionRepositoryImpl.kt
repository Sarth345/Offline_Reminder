package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.OccurrenceExceptionDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class OccurrenceExceptionRepositoryImpl(
    private val occurrenceExceptionDao: OccurrenceExceptionDao
) : OccurrenceExceptionRepository {

    override fun getExceptionsForEvent(eventId: String): Flow<List<OccurrenceException>> =
        occurrenceExceptionDao.getExceptionsForEventFlow(eventId)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun getExceptionByOccurrenceId(occurrenceId: String): OccurrenceException? =
        occurrenceExceptionDao.getByOccurrenceId(occurrenceId)?.toDomain()

    override fun getExceptionByOccurrenceIdFlow(occurrenceId: String): Flow<OccurrenceException?> =
        occurrenceExceptionDao.getByOccurrenceIdFlow(occurrenceId).map { it?.toDomain() }

    override suspend fun getExceptionsInRange(
        startLdt: LocalDateTime,
        endLdt: LocalDateTime
    ): List<OccurrenceException> =
        occurrenceExceptionDao.getExceptionsInRange(startLdt, endLdt).map { it.toDomain() }

    override suspend fun getAllExceptions(): List<OccurrenceException> =
        occurrenceExceptionDao.getAll().map { it.toDomain() }

    override suspend fun saveException(exception: OccurrenceException): Result<Unit> =
        Result.runCatching {
            occurrenceExceptionDao.insert(exception.toEntity())
        }

    override suspend fun deleteException(id: String): Result<Unit> =
        Result.runCatching {
            occurrenceExceptionDao.deleteById(id)
        }

    override suspend fun deleteByOccurrenceId(occurrenceId: String): Result<Unit> =
        Result.runCatching {
            occurrenceExceptionDao.deleteByOccurrenceId(occurrenceId)
        }
}
