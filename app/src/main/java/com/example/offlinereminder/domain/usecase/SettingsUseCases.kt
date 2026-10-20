package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe or query application settings.
 */
class GetSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<AppSettings> = settingsRepository.getSettingsFlow()

    suspend fun get(): AppSettings = settingsRepository.getSettings()
}

/**
 * Use case to update application settings atomically.
 */
class UpdateSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(transform: (AppSettings) -> AppSettings): Result<AppSettings> {
        return settingsRepository.updateSettings(transform)
    }
}

/**
 * Use case to reset application settings back to defaults.
 */
class ResetSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(): Result<AppSettings> {
        return settingsRepository.resetSettings()
    }
}
