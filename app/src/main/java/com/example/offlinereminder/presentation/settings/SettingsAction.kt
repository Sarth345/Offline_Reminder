package com.example.offlinereminder.presentation.settings

import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.DateFormatPreference
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.ThemeMode
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek

/**
 * User actions supported on the Settings screen.
 */
sealed interface SettingsAction {
    // Appearance & Formatting
    data class UpdateThemeMode(val themeMode: ThemeMode) : SettingsAction
    data class UpdateTimeFormat(val use24Hour: Boolean) : SettingsAction
    data class UpdateDateFormat(val dateFormat: DateFormatPreference) : SettingsAction
    data class UpdateFirstDayOfWeek(val firstDay: DayOfWeek) : SettingsAction

    // Event Defaults
    data class UpdateDefaultPriority(val priority: Priority) : SettingsAction
    data class UpdateDefaultDuration(val minutes: Int) : SettingsAction
    data class UpdateDefaultSnooze(val minutes: Int) : SettingsAction

    // Notifications & Quiet Hours
    data class ToggleSound(val enabled: Boolean) : SettingsAction
    data class ToggleVibration(val enabled: Boolean) : SettingsAction
    data class UpdateQuietHours(val quietHours: QuietHours) : SettingsAction
    data class ToggleNotificationsEnabled(val enabled: Boolean) : SettingsAction
    data class ToggleAlarmModeEnabled(val enabled: Boolean) : SettingsAction
    data class UpdateNotificationSound(val sound: com.example.offlinereminder.domain.model.SoundReference) : SettingsAction
    data class UpdateAlarmSound(val sound: com.example.offlinereminder.domain.model.SoundReference) : SettingsAction
    data class RefreshCapabilities(val areNotificationsEnabled: Boolean) : SettingsAction
    data class AddCustomSound(
        val title: String,
        val fileName: String,
        val mimeType: String,
        val fileSizeBytes: Long,
        val inputStream: InputStream,
        val applyToNotification: Boolean = false,
        val applyToAlarm: Boolean = false
    ) : SettingsAction
    data class DeleteCustomSound(val soundId: String) : SettingsAction
    data class PreviewSound(val sound: com.example.offlinereminder.domain.model.SoundReference, val type: String) : SettingsAction
    data object StopPreviewSound : SettingsAction
    data class ScheduleTestNotification(val isAlarm: Boolean) : SettingsAction

    // Category Management
    data class CreateCategory(
        val name: String,
        val color: Long = 0xFF6200EE,
        val icon: String = "category",
        val sortOrder: Int = 0
    ) : SettingsAction
    data class UpdateCategory(val category: Category) : SettingsAction
    data class DeleteCategory(val categoryId: String) : SettingsAction

    // Event Type Management
    data class CreateEventType(
        val name: String,
        val categoryId: String,
        val key: String = "",
        val defaultIcon: String? = null,
        val defaultColor: Int? = null
    ) : SettingsAction
    data class UpdateEventType(val eventType: EventType) : SettingsAction
    data class DeleteEventType(val eventTypeId: String) : SettingsAction

    // Data Management & Reset
    data class ExportBackup(val outputStream: OutputStream) : SettingsAction
    data class ImportBackup(
        val inputStream: InputStream,
        val strategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL
    ) : SettingsAction
    data class SelectRestoreStrategy(val strategy: RestoreStrategy) : SettingsAction
    data object ResetSettings : SettingsAction
    data object DismissMessages : SettingsAction
}
