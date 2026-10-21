package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.repository.NotificationProfileRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CreateNotificationProfileUseCase(
    private val notificationProfileRepository: NotificationProfileRepository
) {
    suspend operator fun invoke(
        name: String,
        deliveryType: com.example.offlinereminder.domain.model.ReminderDeliveryType = com.example.offlinereminder.domain.model.ReminderDeliveryType.NOTIFICATION,
        sound: com.example.offlinereminder.domain.model.SoundReference = com.example.offlinereminder.domain.model.SoundReference(SoundSourceType.DEFAULT),
        vibrationEnabled: Boolean = true,
        defaultSnoozeDurationMinutes: Int = 10,
        isDefault: Boolean = false,
        id: String = UUID.randomUUID().toString()
    ): Result<NotificationProfile> = Result.runCatching {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Profile name cannot be blank" }
        require(defaultSnoozeDurationMinutes > 0) { "Default snooze duration must be positive" }
        if (sound.type == SoundSourceType.CUSTOM) {
            require(!sound.customSoundId.isNullOrBlank()) { "Custom sound ID must be provided when sound source is CUSTOM" }
        }

        val profile = NotificationProfile(
            id = id,
            name = trimmedName,
            deliveryType = deliveryType,
            sound = sound,
            vibrationEnabled = vibrationEnabled,
            defaultSnoozeDurationMinutes = defaultSnoozeDurationMinutes,
            isDefault = isDefault
        )

        val saveRes = notificationProfileRepository.saveProfile(profile)
        if (saveRes.isFailure) throw (saveRes as Result.Failure).error

        if (isDefault) {
            notificationProfileRepository.setDefaultProfile(profile.id)
        }
        profile
    }
}

class UpdateNotificationProfileUseCase(
    private val notificationProfileRepository: NotificationProfileRepository
) {
    suspend operator fun invoke(profile: NotificationProfile): Result<Unit> = Result.runCatching {
        val trimmedName = profile.name.trim()
        require(trimmedName.isNotBlank()) { "Profile name cannot be blank" }
        require(profile.defaultSnoozeDurationMinutes > 0) { "Default snooze duration must be positive" }
        if (profile.sound.type == SoundSourceType.CUSTOM) {
            require(!profile.sound.customSoundId.isNullOrBlank()) { "Custom sound ID must be provided when sound source is CUSTOM" }
        }

        val updated = profile.copy(name = trimmedName)
        val saveRes = notificationProfileRepository.saveProfile(updated)
        if (saveRes.isFailure) throw (saveRes as Result.Failure).error

        if (profile.isDefault) {
            notificationProfileRepository.setDefaultProfile(profile.id)
        }
    }
}

class GetNotificationProfilesUseCase(
    private val notificationProfileRepository: NotificationProfileRepository
) {
    operator fun invoke(): Flow<List<NotificationProfile>> =
        notificationProfileRepository.getAllProfiles()
}

class GetNotificationProfileUseCase(
    private val notificationProfileRepository: NotificationProfileRepository
) {
    suspend operator fun invoke(id: String): NotificationProfile? =
        notificationProfileRepository.getProfileById(id)

    suspend fun getDefault(): NotificationProfile? =
        notificationProfileRepository.getDefaultProfile()
}

class DeleteNotificationProfileUseCase(
    private val notificationProfileRepository: NotificationProfileRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        notificationProfileRepository.deleteProfile(id)
}

class SetDefaultNotificationProfileUseCase(
    private val notificationProfileRepository: NotificationProfileRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = Result.runCatching {
        val profile = notificationProfileRepository.getProfileById(id)
            ?: throw IllegalArgumentException("Profile not found: $id")
        notificationProfileRepository.setDefaultProfile(profile.id)
    }
}
