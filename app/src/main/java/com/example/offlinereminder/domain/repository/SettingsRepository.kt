package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin repository interface for observing and persisting application settings.
 */
interface SettingsRepository {
    /**
     * Observes continuous updates to user preferences.
     */
    fun getSettingsFlow(): Flow<AppSettings>

    /**
     * Retrieves the current snapshot of user preferences.
     */
    suspend fun getSettings(): AppSettings

    /**
     * Mutates user preferences using an atomic transformation.
     */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): Result<AppSettings>

    /**
     * Resets all preferences back to default values.
     */
    suspend fun resetSettings(): Result<AppSettings>
}
