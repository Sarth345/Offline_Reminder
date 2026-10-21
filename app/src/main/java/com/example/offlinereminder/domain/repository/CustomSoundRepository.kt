package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import kotlinx.coroutines.flow.Flow

interface CustomSoundRepository {
    fun getAllSounds(): Flow<List<CustomSoundMetadata>>
    suspend fun getSoundById(id: String): CustomSoundMetadata?
    suspend fun saveSound(metadata: CustomSoundMetadata): Result<Unit>
    suspend fun deleteSound(id: String): Result<Unit>
}
