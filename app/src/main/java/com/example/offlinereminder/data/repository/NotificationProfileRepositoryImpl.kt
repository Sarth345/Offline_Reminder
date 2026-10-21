package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.NotificationProfileDao
import com.example.offlinereminder.data.local.dao.NotificationScheduleDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.repository.NotificationProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationProfileRepositoryImpl(
    private val notificationProfileDao: NotificationProfileDao,
    private val notificationScheduleDao: NotificationScheduleDao? = null
) : NotificationProfileRepository {

    override fun getAllProfiles(): Flow<List<NotificationProfile>> =
        notificationProfileDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfileById(id: String): NotificationProfile? =
        notificationProfileDao.getById(id)?.toDomain()

    override suspend fun saveProfile(profile: NotificationProfile): Result<Unit> = Result.runCatching {
        notificationProfileDao.insert(profile.toEntity())
    }

    override suspend fun deleteProfile(id: String): Result<Unit> = Result.runCatching {
        notificationScheduleDao?.detachProfile(id)
        notificationProfileDao.deleteById(id)
    }

    override suspend fun getDefaultProfile(): NotificationProfile? =
        notificationProfileDao.getDefaultProfile()?.toDomain()
}
