package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.repository.CustomSoundRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.NotificationProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class AddCustomSoundUseCase(
    private val repository: CustomSoundRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        title: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        durationMillis: Long = 0L,
        id: String = UUID.randomUUID().toString()
    ): Result<CustomSoundMetadata> {
        if (title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Sound title cannot be blank"))
        }
        if (fileName.isBlank()) {
            return Result.Failure(IllegalArgumentException("File name cannot be blank"))
        }
        if (fileSizeBytes <= 0) {
            return Result.Failure(IllegalArgumentException("File size must be greater than 0"))
        }
        if (durationMillis < 0) {
            return Result.Failure(IllegalArgumentException("Duration cannot be negative"))
        }

        val metadata = CustomSoundMetadata(
            id = id,
            title = title.trim(),
            fileName = fileName.trim(),
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            durationMillis = durationMillis,
            addedAt = timeProvider.nowInstant()
        )

        return when (val saveResult = repository.saveSound(metadata)) {
            is Result.Success -> Result.Success(metadata)
            is Result.Failure -> saveResult
        }
    }
}

class UpdateCustomSoundUseCase(
    private val repository: CustomSoundRepository
) {
    suspend operator fun invoke(
        id: String,
        title: String
    ): Result<CustomSoundMetadata> {
        if (title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Sound title cannot be blank"))
        }
        val existing = repository.getSoundById(id)
            ?: return Result.Failure(IllegalArgumentException("Custom sound with id $id not found"))

        val updated = existing.copy(title = title.trim())
        return when (val saveResult = repository.saveSound(updated)) {
            is Result.Success -> Result.Success(updated)
            is Result.Failure -> saveResult
        }
    }
}

class GetCustomSoundsUseCase(
    private val repository: CustomSoundRepository
) {
    operator fun invoke(): Flow<List<CustomSoundMetadata>> = repository.getAllSounds()
}

class GetCustomSoundUseCase(
    private val repository: CustomSoundRepository
) {
    suspend operator fun invoke(id: String): CustomSoundMetadata? = repository.getSoundById(id)
}

class DeleteCustomSoundUseCase(
    private val repository: CustomSoundRepository,
    private val profileRepository: NotificationProfileRepository? = null,
    private val eventRepository: EventRepository? = null
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) {
            return Result.Failure(IllegalArgumentException("Sound id cannot be blank"))
        }
        if (profileRepository != null) {
            val profiles = profileRepository.getAllProfiles().first()
            val referencingProfile = profiles.find { it.sound.customSoundId == id }
            if (referencingProfile != null) {
                return Result.Failure(IllegalStateException("Cannot delete sound: referenced by profile '${referencingProfile.name}'"))
            }
        }
        if (eventRepository != null) {
            val events = eventRepository.getAllActiveEvents().first()
            val referencingEvent = events.find { event ->
                event.notificationSchedules.any { schedule -> schedule.sound.customSoundId == id }
            }
            if (referencingEvent != null) {
                return Result.Failure(IllegalStateException("Cannot delete sound: referenced by event '${referencingEvent.title}'"))
            }
        }
        return repository.deleteSound(id)
    }
}
