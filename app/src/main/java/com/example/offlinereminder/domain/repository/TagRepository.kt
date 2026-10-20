package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin repository interface for Tag persistence and association.
 */
interface TagRepository {
    fun getAllTags(): Flow<List<Tag>>
    fun searchTags(query: String): Flow<List<Tag>>
    suspend fun getTag(id: String): Tag?
    suspend fun getTagByNormalizedName(name: String): Tag?
    fun getTagsForEvent(eventId: String): Flow<List<Tag>>
    fun getActiveEventsForTag(tagId: String): Flow<List<Event>>
    suspend fun saveTag(tag: Tag): Result<Unit>
    suspend fun deleteTag(id: String): Result<Unit>
    suspend fun addTagToEvent(eventId: String, tagId: String): Result<Unit>
    suspend fun removeTagFromEvent(eventId: String, tagId: String): Result<Unit>
}
