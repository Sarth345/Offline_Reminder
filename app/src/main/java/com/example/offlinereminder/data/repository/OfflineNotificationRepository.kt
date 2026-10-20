package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.NotificationInstanceDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Offline-first repository implementation for persisting notification instances via Room.
 */
class OfflineNotificationRepository(
    private val notificationInstanceDao: NotificationInstanceDao
) : NotificationRepository {

    override fun getInstancesForEventFlow(eventId: String): Flow<List<NotificationInstance>> =
        notificationInstanceDao.getInstancesForEventFlow(eventId).map { list -> list.map { it.toDomain() } }

    override fun getAllScheduledFlow(): Flow<List<NotificationInstance>> =
        notificationInstanceDao.getAllScheduledFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getInstanceById(id: String): NotificationInstance? =
        notificationInstanceDao.getById(id)?.toDomain()

    override suspend fun getInstancesForEvent(eventId: String): List<NotificationInstance> =
        notificationInstanceDao.getInstancesForEvent(eventId).map { it.toDomain() }

    override suspend fun getAllActiveScheduled(): List<NotificationInstance> =
        notificationInstanceDao.getAllActiveScheduled().map { it.toDomain() }

    override suspend fun saveInstance(instance: NotificationInstance): Result<Unit> = Result.runCatching {
        notificationInstanceDao.insert(instance.toEntity())
    }

    override suspend fun saveInstances(instances: List<NotificationInstance>): Result<Unit> = Result.runCatching {
        notificationInstanceDao.insertAll(instances.map { it.toEntity() })
    }

    override suspend fun updateStatus(id: String, status: NotificationInstanceStatus): Result<Unit> = Result.runCatching {
        notificationInstanceDao.updateStatus(id, status.name)
    }

    override suspend fun snooze(id: String, snoozeUntil: Instant, newTriggerMillis: Long): Result<Unit> = Result.runCatching {
        notificationInstanceDao.snooze(id, snoozeUntil, newTriggerMillis)
    }

    override suspend fun deleteById(id: String): Result<Unit> = Result.runCatching {
        notificationInstanceDao.deleteById(id)
    }

    override suspend fun deleteByEventId(eventId: String): Result<Unit> = Result.runCatching {
        notificationInstanceDao.deleteByEventId(eventId)
    }
}
