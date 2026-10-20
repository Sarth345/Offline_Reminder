package com.example.offlinereminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.model.DateFormatPreference
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.model.ThemeMode
import com.example.offlinereminder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Concrete Android DataStore implementation of [SettingsRepository].
 * Provides fully offline, persistent, atomic user preferences management.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_USE_24_HOUR = booleanPreferencesKey("use_24_hour_format")
        private val KEY_DATE_FORMAT = stringPreferencesKey("date_format")
        private val KEY_FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        private val KEY_DEFAULT_PRIORITY = stringPreferencesKey("default_event_priority")
        private val KEY_DEFAULT_DURATION = intPreferencesKey("default_event_duration_minutes")
        private val KEY_DEFAULT_SNOOZE = intPreferencesKey("default_snooze_duration_minutes")
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val KEY_QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val KEY_QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
        private val KEY_QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
        private val KEY_QUIET_HOURS_POLICY = stringPreferencesKey("quiet_hours_policy")
        private val KEY_QUIET_HOURS_ALLOW_CRITICAL = booleanPreferencesKey("quiet_hours_allow_critical")
    }

    override fun getSettingsFlow(): Flow<AppSettings> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                mapPreferencesToSettings(preferences)
            }
    }

    override suspend fun getSettings(): AppSettings {
        return getSettingsFlow().first()
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings): Result<AppSettings> = Result.runCatching {
        var updatedSettings: AppSettings? = null
        withRetry {
            dataStore.edit { preferences ->
                val current = mapPreferencesToSettings(preferences)
                val updated = transform(current)
                updatedSettings = updated

                preferences[KEY_THEME_MODE] = updated.themeMode.name
                preferences[KEY_USE_24_HOUR] = updated.use24HourFormat
                preferences[KEY_DATE_FORMAT] = updated.dateFormat.name
                preferences[KEY_FIRST_DAY_OF_WEEK] = updated.firstDayOfWeek.name
                preferences[KEY_DEFAULT_PRIORITY] = updated.defaultEventPriority.name
                preferences[KEY_DEFAULT_DURATION] = updated.defaultEventDurationMinutes
                preferences[KEY_DEFAULT_SNOOZE] = updated.defaultSnoozeDurationMinutes
                preferences[KEY_SOUND_ENABLED] = updated.soundEnabled
                preferences[KEY_VIBRATION_ENABLED] = updated.vibrationEnabled

                preferences[KEY_QUIET_HOURS_ENABLED] = updated.quietHours.enabled
                preferences[KEY_QUIET_HOURS_START] = updated.quietHours.startLocalTime.toString()
                preferences[KEY_QUIET_HOURS_END] = updated.quietHours.endLocalTime.toString()
                preferences[KEY_QUIET_HOURS_POLICY] = updated.quietHours.policy.name
                preferences[KEY_QUIET_HOURS_ALLOW_CRITICAL] = updated.quietHours.allowCritical
            }
        }
        updatedSettings ?: getSettings()
    }

    override suspend fun resetSettings(): Result<AppSettings> = Result.runCatching {
        withRetry {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
        AppSettings()
    }

    private suspend fun <T> withRetry(times: Int = 5, initialDelayMs: Long = 50, block: suspend () -> T): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: IOException) {
                kotlinx.coroutines.yield()
                try {
                    Thread.sleep(currentDelay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                currentDelay *= 2
            }
        }
        return block()
    }

    private fun mapPreferencesToSettings(preferences: Preferences): AppSettings {
        val themeMode = preferences[KEY_THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.SYSTEM

        val use24Hour = preferences[KEY_USE_24_HOUR] ?: false

        val dateFormat = preferences[KEY_DATE_FORMAT]?.let {
            runCatching { DateFormatPreference.valueOf(it) }.getOrNull()
        } ?: DateFormatPreference.SYSTEM_DEFAULT

        val firstDay = preferences[KEY_FIRST_DAY_OF_WEEK]?.let {
            runCatching { DayOfWeek.valueOf(it) }.getOrNull()
        } ?: DayOfWeek.MONDAY

        val priority = preferences[KEY_DEFAULT_PRIORITY]?.let {
            runCatching { Priority.valueOf(it) }.getOrNull()
        } ?: Priority.NORMAL

        val duration = preferences[KEY_DEFAULT_DURATION] ?: 60
        val snooze = preferences[KEY_DEFAULT_SNOOZE] ?: 10
        val sound = preferences[KEY_SOUND_ENABLED] ?: true
        val vibration = preferences[KEY_VIBRATION_ENABLED] ?: true

        val qhEnabled = preferences[KEY_QUIET_HOURS_ENABLED] ?: false
        val qhStart = preferences[KEY_QUIET_HOURS_START]?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        } ?: LocalTime.of(22, 0)

        val qhEnd = preferences[KEY_QUIET_HOURS_END]?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        } ?: LocalTime.of(7, 0)

        val qhPolicy = preferences[KEY_QUIET_HOURS_POLICY]?.let {
            runCatching { QuietHoursPolicy.valueOf(it) }.getOrNull()
        } ?: QuietHoursPolicy.DELAY

        val qhAllowCritical = preferences[KEY_QUIET_HOURS_ALLOW_CRITICAL] ?: true

        return AppSettings(
            themeMode = themeMode,
            use24HourFormat = use24Hour,
            dateFormat = dateFormat,
            firstDayOfWeek = firstDay,
            defaultEventPriority = priority,
            defaultEventDurationMinutes = duration,
            defaultSnoozeDurationMinutes = snooze,
            soundEnabled = sound,
            vibrationEnabled = vibration,
            quietHours = QuietHours(
                enabled = qhEnabled,
                startLocalTime = qhStart,
                endLocalTime = qhEnd,
                policy = qhPolicy,
                allowCritical = qhAllowCritical
            )
        )
    }
}
