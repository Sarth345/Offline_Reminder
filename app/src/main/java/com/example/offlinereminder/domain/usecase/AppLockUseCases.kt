package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.AppLockSettings
import com.example.offlinereminder.domain.repository.SettingsRepository
import com.example.offlinereminder.domain.security.AppLockPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes the current AppLock settings as a reactive flow.
 */
class GetAppLockSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<AppLockSettings> =
        settingsRepository.getSettingsFlow().map { it.appLock }
}

/**
 * Updates the user's AppLock configuration.
 */
class UpdateAppLockSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        isEnabled: Boolean,
        timeoutMinutes: Int = 0,
        lockOnBackground: Boolean = true
    ): Result<AppLockSettings> = Result.runCatching {
        val updated = settingsRepository.updateSettings { current ->
            current.copy(
                appLock = AppLockSettings(
                    isEnabled = isEnabled,
                    timeoutMinutes = timeoutMinutes.coerceAtLeast(0),
                    lockOnBackground = lockOnBackground
                )
            )
        }.getOrThrow()
        updated.appLock
    }
}

/**
 * Evaluates whether app access must be locked on launch or foreground transition.
 */
class CheckAppLockRequiredUseCase(
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        lastActiveEpochMillis: Long,
        isAppReturningFromBackground: Boolean
    ): Boolean {
        val settings = settingsRepository.getSettings().appLock
        return AppLockPolicy.shouldLock(
            settings = settings,
            lastActiveEpochMillis = lastActiveEpochMillis,
            currentEpochMillis = timeProvider.nowInstant().toEpochMilli(),
            isAppReturningFromBackground = isAppReturningFromBackground
        )
    }
}
