package com.example.offlinereminder.domain.model

import java.time.DayOfWeek

/**
 * User interface theme selection.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Date display format preference.
 */
enum class DateFormatPreference {
    SYSTEM_DEFAULT,
    ISO,        // YYYY-MM-DD
    DMY,        // DD/MM/YYYY
    MDY         // MM/DD/YYYY
}

/**
 * Pure Kotlin immutable model of user preferences and application configuration.
 * Fully decoupled from Android framework, DataStore, and Room persistence.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val use24HourFormat: Boolean = false,
    val dateFormat: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val defaultEventPriority: Priority = Priority.NORMAL,
    val defaultEventDurationMinutes: Int = 60,
    val defaultSnoozeDurationMinutes: Int = 10,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val quietHours: QuietHours = QuietHours(),
    val appLock: AppLockSettings = AppLockSettings()
)
