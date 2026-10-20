package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.TagDao
import com.example.offlinereminder.data.local.entity.EventTagCrossRef
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl(
    private val tagDao: TagDao
) : TagRepository {

    override fun getAllTags(): Flow<List<Tag>> =
        tagDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override fun searchTags(query: String): Flow<List<Tag>> =
        tagDao.searchTagsFlow(Tag.normalize(query)).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTag(id: String): Tag? =
        tagDao.getById(id)?.toDomain()

    override suspend fun getTagByNormalizedName(name: String): Tag? =
        tagDao.getByNormalizedName(Tag.normalize(name))?.toDomain()

    override fun getTagsForEvent(eventId: String): Flow<List<Tag>> =
        tagDao.getTagsForEventFlow(eventId).map { entities -> entities.map { it.toDomain() } }

    override fun getActiveEventsForTag(tagId: String): Flow<List<Event>> =
        tagDao.getActiveEventsWithDetailsForTagFlow(tagId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun saveTag(tag: Tag): Result<Unit> =
        Result.runCatching {
            tagDao.insert(tag.toEntity())
        }

    override suspend fun deleteTag(id: String): Result<Unit> =
        Result.runCatching {
            tagDao.deleteById(id)
        }

    override suspend fun addTagToEvent(eventId: String, tagId: String): Result<Unit> =
        Result.runCatching {
            tagDao.insertCrossRef(EventTagCrossRef(eventId = eventId, tagId = tagId))
        }

    override suspend fun removeTagFromEvent(eventId: String, tagId: String): Result<Unit> =
        Result.runCatching {
            tagDao.removeTagFromEvent(eventId = eventId, tagId = tagId)
        }
}
