package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.CustomSoundDao
import com.example.offlinereminder.data.local.dao.NotificationProfileDao
import com.example.offlinereminder.data.local.dao.NotificationScheduleDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.repository.CustomSoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CustomSoundRepositoryImpl(
    private val customSoundDao: CustomSoundDao,
    private val notificationScheduleDao: NotificationScheduleDao? = null,
    private val notificationProfileDao: NotificationProfileDao? = null
) : CustomSoundRepository {

    override fun getAllSounds(): Flow<List<CustomSoundMetadata>> =
        customSoundDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getSoundById(id: String): CustomSoundMetadata? =
        customSoundDao.getById(id)?.toDomain()

    override suspend fun saveSound(metadata: CustomSoundMetadata): Result<Unit> = Result.runCatching {
        customSoundDao.insert(metadata.toEntity())
    }

    override suspend fun deleteSound(id: String): Result<Unit> = Result.runCatching {
        notificationScheduleDao?.detachCustomSound(id)
        notificationProfileDao?.detachCustomSound(id)
        customSoundDao.deleteById(id)
    }
}
