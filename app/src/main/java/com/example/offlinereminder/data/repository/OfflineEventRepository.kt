package com.example.offlinereminder.data.repository

import androidx.room.withTransaction
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.data.local.mapper.toGroupCrossRefs
import com.example.offlinereminder.data.local.mapper.toTagCrossRefs
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDependency
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventRelationship
import com.example.offlinereminder.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class OfflineEventRepository(
    private val database: AppDatabase,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) : EventRepository {

    private val eventDao = database.eventDao()
    private val eventGroupDao = database.eventGroupDao()
    private val tagDao = database.tagDao()
    private val checklistDao = database.checklistDao()
    private val notificationScheduleDao = database.notificationScheduleDao()
    private val attachmentDao = database.attachmentDao()
    private val dependencyDao = database.eventDependencyDao()
    private val relationshipDao = database.eventRelationshipDao()

    override fun getEventById(id: String): Flow<Event?> =
        eventDao.getEventWithDetailsByIdFlow(id).map { it?.toDomain() }

    override suspend fun getEvent(id: String): Event? =
        eventDao.getEventWithDetailsById(id)?.toDomain()

    override fun getAllActiveEvents(): Flow<List<Event>> =
        eventDao.getAllActiveWithDetailsFlow().map { list -> list.map { it.toDomain() } }

    override fun getAllEvents(): Flow<List<Event>> =
        eventDao.getAllWithDetailsFlow().map { list -> list.map { it.toDomain() } }

    override fun getEventsByLifecycleStatus(status: EventLifecycleStatus): Flow<List<Event>> =
        eventDao.getEventsByLifecycleStatusWithDetailsFlow(status).map { list -> list.map { it.toDomain() } }

    override fun getFavoriteEvents(): Flow<List<Event>> =
        eventDao.getFavoriteEventsWithDetailsFlow().map { list -> list.map { it.toDomain() } }

    override fun getEventsByCategory(categoryId: String): Flow<List<Event>> =
        eventDao.getActiveEventsByCategoryWithDetailsFlow(categoryId).map { list -> list.map { it.toDomain() } }

    override fun getEventsByEventType(eventTypeId: String): Flow<List<Event>> =
        eventDao.getActiveEventsByEventTypeWithDetailsFlow(eventTypeId).map { list -> list.map { it.toDomain() } }

    override fun getSubEvents(parentEventId: String): Flow<List<Event>> =
        eventDao.getSubEventsWithDetailsFlow(parentEventId).map { list -> list.map { it.toDomain() } }

    override fun getEventsInDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Event>> =
        eventDao.getEventsInDateRangeWithDetailsFlow(startDate, endDate).map { list -> list.map { it.toDomain() } }

    override suspend fun saveEvent(event: Event): Result<Unit> = Result.runCatching {
        database.withTransaction {
            // 1. Insert or replace canonical event entity
            eventDao.insert(event.toEntity())

            // 2. Sync group associations
            eventGroupDao.removeAllGroupsFromEvent(event.id)
            if (event.groupIds.isNotEmpty()) {
                eventGroupDao.insertCrossRefs(event.toGroupCrossRefs())
            }

            // 3. Sync tag associations
            tagDao.removeAllTagsFromEvent(event.id)
            if (event.tagIds.isNotEmpty()) {
                tagDao.insertCrossRefs(event.toTagCrossRefs())
            }

            // 4. Sync notification schedules
            notificationScheduleDao.deleteByEventId(event.id)
            if (event.notificationSchedules.isNotEmpty()) {
                notificationScheduleDao.insertAll(event.notificationSchedules.map { it.toEntity(event.id) })
            }

            // 5. Sync checklist and its items
            checklistDao.deleteChecklistByEventId(event.id)
            event.checklist?.let { checklist ->
                checklistDao.insertChecklist(checklist.toEntity(event.id))
                if (checklist.items.isNotEmpty()) {
                    checklistDao.insertItems(checklist.items.map { it.toEntity(checklist.id) })
                }
            }

            // 6. Sync attachments
            attachmentDao.deleteByEventId(event.id)
            if (event.attachments.isNotEmpty()) {
                attachmentDao.insertAll(event.attachments.map { it.toEntity(event.id) })
            }
        }
    }

    override suspend fun deleteEvent(id: String): Result<Unit> = Result.runCatching {
        eventDao.deleteById(id)
    }

    override suspend fun moveToTrash(id: String): Result<Unit> = Result.runCatching {
        val now = timeProvider.nowInstant()
        eventDao.moveToTrash(id, now, now)
    }

    override suspend fun restoreFromTrash(id: String): Result<Unit> = Result.runCatching {
        val now = timeProvider.nowInstant()
        eventDao.restoreFromTrash(id, now)
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean): Result<Unit> = Result.runCatching {
        val now = timeProvider.nowInstant()
        eventDao.updateFavorite(id, isFavorite, now)
    }

    // Direct dependencies and relationships persistence
    suspend fun addDependency(dependency: EventDependency): Result<Unit> = Result.runCatching {
        dependencyDao.insert(dependency.toEntity())
    }

    suspend fun removeDependency(sourceEventId: String, targetEventId: String): Result<Unit> = Result.runCatching {
        dependencyDao.deleteDependency(sourceEventId, targetEventId)
    }

    fun getDependenciesForEvent(eventId: String): Flow<List<EventDependency>> =
        dependencyDao.getDependenciesForSourceFlow(eventId).map { list -> list.map { it.toDomain() } }

    fun getDependentsOfEvent(eventId: String): Flow<List<EventDependency>> =
        dependencyDao.getDependentsForTargetFlow(eventId).map { list -> list.map { it.toDomain() } }

    suspend fun addRelationship(relationship: EventRelationship): Result<Unit> = Result.runCatching {
        relationshipDao.insert(relationship.toEntity())
    }

    suspend fun removeRelationship(sourceEventId: String, targetEventId: String): Result<Unit> = Result.runCatching {
        relationshipDao.deleteRelationship(sourceEventId, targetEventId)
    }

    fun getRelationshipsForEvent(eventId: String): Flow<List<EventRelationship>> =
        relationshipDao.getRelationshipsForSourceFlow(eventId).map { list -> list.map { it.toDomain() } }
}
