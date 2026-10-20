package com.example.offlinereminder.domain.backup

import kotlinx.serialization.Serializable

/**
 * Root canonical, versioned public backup DTO.
 * Completely decoupled from internal Room database entities and schemas.
 */
@Serializable
data class BackupPayload(
    val backupVersion: Int = BackupConstants.CURRENT_BACKUP_VERSION,
    val appVersion: String = BackupConstants.DEFAULT_APP_VERSION,
    val exportedAt: String,
    val categories: List<CategoryBackupDto> = emptyList(),
    val eventTypes: List<EventTypeBackupDto> = emptyList(),
    val groups: List<GroupBackupDto> = emptyList(),
    val tags: List<TagBackupDto> = emptyList(),
    val events: List<EventBackupDto> = emptyList(),
    val occurrenceExceptions: List<OccurrenceExceptionBackupDto> = emptyList(),
    val dependencies: List<EventDependencyBackupDto> = emptyList(),
    val relationships: List<EventRelationshipBackupDto> = emptyList(),
    val settings: SettingsBackupDto = SettingsBackupDto()
)

@Serializable
data class CategoryBackupDto(
    val id: String,
    val name: String,
    val color: Int? = null,
    val icon: String? = null,
    val isCustom: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class EventTypeBackupDto(
    val id: String,
    val name: String,
    val key: String,
    val categoryId: String,
    val defaultColor: Int? = null,
    val defaultIcon: String? = null,
    val aliases: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: String
)

@Serializable
data class GroupBackupDto(
    val id: String,
    val name: String,
    val description: String = "",
    val color: Int? = null,
    val icon: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TagBackupDto(
    val id: String,
    val name: String,
    val normalizedName: String,
    val color: Int? = null
)

@Serializable
data class EventBackupDto(
    val id: String,
    val title: String,
    val description: String = "",
    val eventTypeId: String,
    val categoryId: String,
    val priority: String = "NORMAL",
    val lifecycleStatus: String = "ACTIVE",
    val isFavorite: Boolean = false,
    val temporal: TemporalBackupDto,
    val location: LocationBackupDto? = null,
    val recurrenceRule: RecurrenceRuleBackupDto? = null,
    val notificationSchedules: List<NotificationScheduleBackupDto> = emptyList(),
    val checklist: ChecklistBackupDto? = null,
    val groupIds: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val parentEventId: String? = null,
    val templateId: String? = null,
    val customMetadata: Map<String, String> = emptyMap(),
    val attachments: List<AttachmentBackupDto> = emptyList(),
    val deletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TemporalBackupDto(
    val type: String, // "DATE_ONLY", "DATE_TIME", "DATE_RANGE", "DATE_TIME_RANGE"
    val startDate: String, // ISO LocalDate: YYYY-MM-DD
    val startTime: String? = null, // ISO LocalTime: HH:mm:ss
    val endDate: String? = null, // ISO LocalDate: YYYY-MM-DD
    val endTime: String? = null, // ISO LocalTime: HH:mm:ss
    val zoneId: String? = null
)

@Serializable
data class LocationBackupDto(
    val name: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class RecurrenceRuleBackupDto(
    val frequency: String, // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    val interval: Int = 1,
    val daysOfWeek: List<String> = emptyList(),
    val dayOfMonth: Int? = null,
    val monthOfYear: Int? = null,
    val endConditionType: String = "NEVER", // "NEVER", "UNTIL_DATE", "COUNT"
    val untilDate: String? = null,
    val maxOccurrences: Int? = null
)

@Serializable
data class NotificationScheduleBackupDto(
    val id: String,
    val offsetSeconds: Long = 0L,
    val triggerType: String = "BEFORE_START",
    val profileId: String? = null
)

@Serializable
data class ChecklistBackupDto(
    val id: String,
    val title: String = "",
    val items: List<ChecklistItemBackupDto> = emptyList()
)

@Serializable
data class ChecklistItemBackupDto(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class AttachmentBackupDto(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long = 0L,
    val relativePath: String? = null, // Relative portable path inside archive e.g. "attachments/id.png"
    val createdAt: String
)

@Serializable
data class OccurrenceExceptionBackupDto(
    val id: String,
    val eventId: String,
    val originalStartDateTime: String,
    val originalZoneId: String,
    val status: String,
    val rescheduledStartDateTime: String? = null,
    val rescheduledEndDateTime: String? = null,
    val rescheduledZoneId: String? = null,
    val actualExecutionDateTime: String? = null,
    val completedAt: String? = null,
    val titleOverride: String? = null,
    val notesOverride: String? = null
)

@Serializable
data class EventDependencyBackupDto(
    val sourceEventId: String,
    val targetEventId: String,
    val type: String = "PREREQUISITE"
)

@Serializable
data class EventRelationshipBackupDto(
    val sourceEventId: String,
    val targetEventId: String,
    val type: String
)

@Serializable
data class SettingsBackupDto(
    val themeMode: String = "SYSTEM",
    val use24HourFormat: Boolean = false,
    val dateFormat: String = "SYSTEM_DEFAULT",
    val firstDayOfWeek: String = "MONDAY",
    val defaultEventPriority: String = "NORMAL",
    val defaultEventDurationMinutes: Int = 60,
    val defaultSnoozeDurationMinutes: Int = 10,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val quietHours: QuietHoursBackupDto = QuietHoursBackupDto()
)

@Serializable
data class QuietHoursBackupDto(
    val enabled: Boolean = false,
    val startLocalTime: String = "22:00",
    val endLocalTime: String = "07:00",
    val policy: String = "DELAY",
    val allowCritical: Boolean = true
)
