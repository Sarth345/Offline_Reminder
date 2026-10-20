package com.example.offlinereminder.domain.backup

import com.example.offlinereminder.core.result.Result
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Validates a [BackupPayload] before any database mutation takes place.
 * Ensures referential integrity, schema version compatibility, and data correctness.
 */
class BackupValidator {

    fun validate(payload: BackupPayload): Result<Unit> {
        val errors = mutableListOf<String>()

        // 1. Version check
        if (payload.backupVersion > BackupConstants.CURRENT_BACKUP_VERSION) {
            return Result.Failure(
                UnsupportedBackupVersionException(
                    "Backup version ${payload.backupVersion} is newer than supported version ${BackupConstants.CURRENT_BACKUP_VERSION}."
                )
            )
        }
        if (payload.backupVersion < BackupConstants.MIN_SUPPORTED_BACKUP_VERSION) {
            return Result.Failure(
                IllegalArgumentException("Backup version ${payload.backupVersion} is below minimum supported version.")
            )
        }

        // 2. Duplicate ID & Blank ID checks
        val categoryIds = mutableSetOf<String>()
        for (cat in payload.categories) {
            if (cat.id.isBlank()) errors.add("Category ID cannot be blank: ${cat.name}")
            if (!categoryIds.add(cat.id)) errors.add("Duplicate category ID: ${cat.id}")
        }

        val eventTypeIds = mutableSetOf<String>()
        for (type in payload.eventTypes) {
            if (type.id.isBlank()) errors.add("EventType ID cannot be blank: ${type.name}")
            if (!eventTypeIds.add(type.id)) errors.add("Duplicate eventType ID: ${type.id}")
        }

        val groupIds = mutableSetOf<String>()
        for (group in payload.groups) {
            if (group.id.isBlank()) errors.add("Group ID cannot be blank: ${group.name}")
            if (!groupIds.add(group.id)) errors.add("Duplicate group ID: ${group.id}")
        }

        val tagIds = mutableSetOf<String>()
        for (tag in payload.tags) {
            if (tag.id.isBlank()) errors.add("Tag ID cannot be blank: ${tag.name}")
            if (!tagIds.add(tag.id)) errors.add("Duplicate tag ID: ${tag.id}")
        }

        val eventIds = mutableSetOf<String>()
        for (event in payload.events) {
            if (event.id.isBlank()) errors.add("Event ID cannot be blank: ${event.title}")
            if (!eventIds.add(event.id)) errors.add("Duplicate event ID: ${event.id}")
        }

        val exceptionIds = mutableSetOf<String>()
        for (exc in payload.occurrenceExceptions) {
            if (exc.id.isBlank()) errors.add("OccurrenceException ID cannot be blank")
            if (!exceptionIds.add(exc.id)) errors.add("Duplicate exception ID: ${exc.id}")
        }

        // 3. Referential integrity
        for (event in payload.events) {
            // Lifecycle & Priority integrity
            if (event.lifecycleStatus !in listOf("ACTIVE", "ARCHIVED", "TRASHED")) {
                errors.add("Event '${event.title}' has invalid lifecycleStatus: ${event.lifecycleStatus}")
            }
            if (event.priority !in listOf("LOW", "NORMAL", "HIGH", "CRITICAL")) {
                errors.add("Event '${event.title}' has invalid priority: ${event.priority}")
            }

            // Category check (allow standard default IDs or categories in payload)
            if (payload.categories.isNotEmpty() && !categoryIds.contains(event.categoryId)) {
                errors.add("Event '${event.title}' references non-existent category ID: ${event.categoryId}")
            }
            // EventType check
            if (payload.eventTypes.isNotEmpty() && !eventTypeIds.contains(event.eventTypeId)) {
                errors.add("Event '${event.title}' references non-existent eventType ID: ${event.eventTypeId}")
            }
            // Group memberships
            for (gId in event.groupIds) {
                if (!groupIds.contains(gId)) {
                    errors.add("Event '${event.title}' references non-existent group ID: $gId")
                }
            }
            // Tag memberships
            for (tId in event.tagIds) {
                if (!tagIds.contains(tId)) {
                    errors.add("Event '${event.title}' references non-existent tag ID: $tId")
                }
            }
            // Temporal integrity
            try {
                val start = LocalDate.parse(event.temporal.startDate)
                event.temporal.startTime?.let { LocalTime.parse(it) }
                if (event.temporal.endDate != null) {
                    val end = LocalDate.parse(event.temporal.endDate)
                    if (end.isBefore(start)) {
                        errors.add("Event '${event.title}' has end date before start date: $end < $start")
                    }
                }
                event.temporal.endTime?.let { LocalTime.parse(it) }
            } catch (e: DateTimeParseException) {
                errors.add("Event '${event.title}' has malformed date/time: ${e.message}")
            }

            // Recurrence integrity
            event.recurrenceRule?.let { rule ->
                if (rule.interval < 1) {
                    errors.add("Event '${event.title}' has invalid recurrence interval: ${rule.interval}")
                }
                if (rule.frequency !in listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) {
                    errors.add("Event '${event.title}' has invalid recurrence frequency: ${rule.frequency}")
                }
            }

            // Notification schedules
            for (sched in event.notificationSchedules) {
                if (sched.offsetSeconds < 0) {
                    errors.add("Event '${event.title}' has negative notification offset: ${sched.offsetSeconds}")
                }
                if (sched.triggerType !in listOf("BEFORE_START", "AT_START", "AFTER_START", "BEFORE_END", "AT_END")) {
                    errors.add("Event '${event.title}' has invalid notification triggerType: ${sched.triggerType}")
                }
            }
        }

        // 4. Dependencies
        for (dep in payload.dependencies) {
            if (dep.sourceEventId == dep.targetEventId) {
                errors.add("Dependency cannot have identical source and target: ${dep.sourceEventId}")
            }
            if (!eventIds.contains(dep.sourceEventId)) {
                errors.add("Dependency references missing source event: ${dep.sourceEventId}")
            }
            if (!eventIds.contains(dep.targetEventId)) {
                errors.add("Dependency references missing target event: ${dep.targetEventId}")
            }
            if (dep.type !in listOf("BLOCKS", "BLOCKED_BY", "PREREQUISITE")) {
                errors.add("Dependency has invalid type: ${dep.type}")
            }
        }

        // 5. Relationships
        for (rel in payload.relationships) {
            if (rel.sourceEventId == rel.targetEventId) {
                errors.add("Relationship cannot have identical source and target: ${rel.sourceEventId}")
            }
            if (!eventIds.contains(rel.sourceEventId)) {
                errors.add("Relationship references missing source event: ${rel.sourceEventId}")
            }
            if (!eventIds.contains(rel.targetEventId)) {
                errors.add("Relationship references missing target event: ${rel.targetEventId}")
            }
            if (rel.type !in listOf("RELATED_TO", "FOLLOW_UP_TO", "PREPARATION_FOR", "REPLACES")) {
                errors.add("Relationship has invalid type: ${rel.type}")
            }
        }

        // 6. Occurrence exceptions
        for (exc in payload.occurrenceExceptions) {
            if (!eventIds.contains(exc.eventId)) {
                errors.add("OccurrenceException references missing event ID: ${exc.eventId}")
            }
            try {
                LocalDateTime.parse(exc.originalStartDateTime)
                exc.rescheduledStartDateTime?.let { LocalDateTime.parse(it) }
                exc.rescheduledEndDateTime?.let { LocalDateTime.parse(it) }
            } catch (e: DateTimeParseException) {
                errors.add("OccurrenceException has malformed date/time: ${e.message}")
            }
        }

        // 7. Settings validation
        val settings = payload.settings
        if (settings.themeMode !in listOf("SYSTEM", "LIGHT", "DARK")) {
            errors.add("Invalid themeMode: ${settings.themeMode}")
        }
        if (settings.dateFormat !in listOf("SYSTEM_DEFAULT", "ISO", "DMY", "MDY")) {
            errors.add("Invalid dateFormat: ${settings.dateFormat}")
        }
        if (settings.firstDayOfWeek !in listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            errors.add("Invalid firstDayOfWeek: ${settings.firstDayOfWeek}")
        }
        if (settings.defaultEventPriority !in listOf("LOW", "NORMAL", "HIGH", "CRITICAL")) {
            errors.add("Invalid defaultEventPriority: ${settings.defaultEventPriority}")
        }
        if (settings.defaultEventDurationMinutes <= 0) {
            errors.add("defaultEventDurationMinutes must be positive: ${settings.defaultEventDurationMinutes}")
        }
        if (settings.defaultSnoozeDurationMinutes <= 0) {
            errors.add("defaultSnoozeDurationMinutes must be positive: ${settings.defaultSnoozeDurationMinutes}")
        }
        try {
            LocalTime.parse(settings.quietHours.startLocalTime)
            LocalTime.parse(settings.quietHours.endLocalTime)
        } catch (e: DateTimeParseException) {
            errors.add("QuietHours has malformed time: ${e.message}")
        }
        if (settings.quietHours.policy !in listOf("DELAY", "SILENT", "ALLOW_CRITICAL")) {
            errors.add("Invalid quietHours policy: ${settings.quietHours.policy}")
        }

        return if (errors.isEmpty()) {
            Result.Success(Unit)
        } else {
            Result.Failure(BackupValidationException("Validation failed with ${errors.size} error(s):\n" + errors.joinToString("\n")))
        }
    }
}

class UnsupportedBackupVersionException(message: String) : Exception(message)

class BackupValidationException(message: String) : Exception(message)
