package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.NotificationProfile
import kotlinx.coroutines.flow.Flow

interface NotificationProfileRepository {
    fun getAllProfiles(): Flow<List<NotificationProfile>>
    suspend fun getProfileById(id: String): NotificationProfile?
    suspend fun saveProfile(profile: NotificationProfile): Result<Unit>
    suspend fun deleteProfile(id: String): Result<Unit>
    suspend fun getDefaultProfile(): NotificationProfile?
}
