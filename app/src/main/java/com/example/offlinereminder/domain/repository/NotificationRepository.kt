package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Domain repository abstraction for persisting and querying scheduled notification instances.
 */
interface NotificationRepository {

    fun getInstancesForEventFlow(eventId: String): Flow<List<NotificationInstance>>

    fun getAllScheduledFlow(): Flow<List<NotificationInstance>>

    suspend fun getInstanceById(id: String): NotificationInstance?

    suspend fun getInstancesForEvent(eventId: String): List<NotificationInstance>

    suspend fun getAllActiveScheduled(): List<NotificationInstance>

    suspend fun saveInstance(instance: NotificationInstance): Result<Unit>

    suspend fun saveInstances(instances: List<NotificationInstance>): Result<Unit>

    suspend fun updateStatus(id: String, status: NotificationInstanceStatus): Result<Unit>

    suspend fun snooze(id: String, snoozeUntil: Instant, newTriggerMillis: Long): Result<Unit>

    suspend fun deleteById(id: String): Result<Unit>

    suspend fun deleteByEventId(eventId: String): Result<Unit>
}
