package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.AttachmentEntity
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.ChecklistItemEntity
import com.example.offlinereminder.data.local.entity.EventDependencyEntity
import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.data.local.entity.EventRelationshipEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.local.entity.OccurrenceExceptionEntity
import com.example.offlinereminder.data.local.entity.TagEntity
import com.example.offlinereminder.domain.backup.AttachmentBackupDto
import com.example.offlinereminder.domain.backup.BackupConstants
import com.example.offlinereminder.domain.backup.CategoryBackupDto
import com.example.offlinereminder.domain.backup.ChecklistBackupDto
import com.example.offlinereminder.domain.backup.ChecklistItemBackupDto
import com.example.offlinereminder.domain.backup.EventBackupDto
import com.example.offlinereminder.domain.backup.EventDependencyBackupDto
import com.example.offlinereminder.domain.backup.EventRelationshipBackupDto
import com.example.offlinereminder.domain.backup.EventTypeBackupDto
import com.example.offlinereminder.domain.backup.GroupBackupDto
import com.example.offlinereminder.domain.backup.LocationBackupDto
import com.example.offlinereminder.domain.backup.NotificationScheduleBackupDto
import com.example.offlinereminder.domain.backup.OccurrenceExceptionBackupDto
import com.example.offlinereminder.domain.backup.QuietHoursBackupDto
import com.example.offlinereminder.domain.backup.RecurrenceRuleBackupDto
import com.example.offlinereminder.domain.backup.SettingsBackupDto
import com.example.offlinereminder.domain.backup.TagBackupDto
import com.example.offlinereminder.domain.backup.TemporalBackupDto
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.DateFormatPreference
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.RelationshipType
import com.example.offlinereminder.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// ----------------------------------------------------
// Category
// ----------------------------------------------------
fun CategoryEntity.toBackupDto(): CategoryBackupDto = CategoryBackupDto(
    id = id,
    name = name,
    color = color,
    icon = icon,
    isCustom = isCustom,
    sortOrder = sortOrder
)

fun CategoryBackupDto.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    isCustom = isCustom,
    sortOrder = sortOrder
)

// ----------------------------------------------------
// EventType
// ----------------------------------------------------
fun EventTypeEntity.toBackupDto(): EventTypeBackupDto = EventTypeBackupDto(
    id = id,
    name = name,
    key = key,
    categoryId = categoryId,
    defaultColor = defaultColor,
    defaultIcon = defaultIcon,
    aliases = aliases,
    isCustom = isCustom,
    isPinned = isPinned,
    createdAt = createdAt.toString()
)

fun EventTypeBackupDto.toEntity(): EventTypeEntity = EventTypeEntity(
    id = id,
    name = name,
    key = key,
    categoryId = categoryId,
    defaultColor = defaultColor,
    defaultIcon = defaultIcon,
    aliases = aliases,
    isCustom = isCustom,
    isPinned = isPinned,
    createdAt = Instant.parse(createdAt)
)

// ----------------------------------------------------
// Group
// ----------------------------------------------------
fun EventGroupEntity.toBackupDto(): GroupBackupDto = GroupBackupDto(
    id = id,
    name = name,
    description = description,
    color = color,
    icon = icon,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

fun GroupBackupDto.toEntity(): EventGroupEntity = EventGroupEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    icon = icon,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt)
)

// ----------------------------------------------------
// Tag
// ----------------------------------------------------
fun TagEntity.toBackupDto(): TagBackupDto = TagBackupDto(
    id = id,
    name = name,
    normalizedName = normalizedName,
    color = color
)

fun TagBackupDto.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    color = color
)

// ----------------------------------------------------
// Occurrence Exception
// ----------------------------------------------------
fun OccurrenceExceptionEntity.toBackupDto(): OccurrenceExceptionBackupDto = OccurrenceExceptionBackupDto(
    id = id,
    eventId = eventId,
    originalStartDateTime = originalStartDateTime.toString(),
    originalZoneId = originalZoneId,
    status = status.name,
    rescheduledStartDateTime = rescheduledStartDateTime?.toString(),
    rescheduledEndDateTime = rescheduledEndDateTime?.toString(),
    rescheduledZoneId = rescheduledZoneId,
    actualExecutionDateTime = actualExecutionDateTime?.toString(),
    completedAt = completedAt?.toString(),
    titleOverride = titleOverride,
    notesOverride = notesOverride
)

fun OccurrenceExceptionBackupDto.toEntity(): OccurrenceExceptionEntity {
    val origLdt = LocalDateTime.parse(originalStartDateTime)
    val origZone = ZoneId.of(originalZoneId)
    val occId = "${eventId}_${origLdt}_${origZone.id}"
    return OccurrenceExceptionEntity(
        id = id,
        eventId = eventId,
        originalStartDateTime = origLdt,
        originalZoneId = origZone.id,
        occurrenceId = occId,
        status = OccurrenceStatus.valueOf(status),
        rescheduledStartDateTime = rescheduledStartDateTime?.let { LocalDateTime.parse(it) },
        rescheduledEndDateTime = rescheduledEndDateTime?.let { LocalDateTime.parse(it) },
        rescheduledZoneId = rescheduledZoneId,
        actualExecutionDateTime = actualExecutionDateTime?.let { LocalDateTime.parse(it) },
        completedAt = completedAt?.let { Instant.parse(it) },
        titleOverride = titleOverride,
        notesOverride = notesOverride
    )
}

// ----------------------------------------------------
// Dependencies & Relationships
// ----------------------------------------------------
fun EventDependencyEntity.toBackupDto(): EventDependencyBackupDto = EventDependencyBackupDto(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = type.name
)

fun EventDependencyBackupDto.toEntity(): EventDependencyEntity = EventDependencyEntity(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = DependencyType.valueOf(type)
)

fun EventRelationshipEntity.toBackupDto(): EventRelationshipBackupDto = EventRelationshipBackupDto(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = type.name
)

fun EventRelationshipBackupDto.toEntity(): EventRelationshipEntity = EventRelationshipEntity(
    sourceEventId = sourceEventId,
    targetEventId = targetEventId,
    type = RelationshipType.valueOf(type)
)

// ----------------------------------------------------
// Quiet Hours
// ----------------------------------------------------
fun QuietHours.toBackupDto(): QuietHoursBackupDto = QuietHoursBackupDto(
    enabled = enabled,
    startLocalTime = startLocalTime.toString(),
    endLocalTime = endLocalTime.toString(),
    policy = policy.name,
    allowCritical = allowCritical
)

fun QuietHoursBackupDto.toDomain(): QuietHours = QuietHours(
    enabled = enabled,
    startLocalTime = LocalTime.parse(startLocalTime),
    endLocalTime = LocalTime.parse(endLocalTime),
    policy = QuietHoursPolicy.valueOf(policy),
    allowCritical = allowCritical
)

// ----------------------------------------------------
// AppSettings <-> SettingsBackupDto
// ----------------------------------------------------
fun AppSettings.toBackupDto(): SettingsBackupDto = SettingsBackupDto(
    themeMode = themeMode.name,
    use24HourFormat = use24HourFormat,
    dateFormat = dateFormat.name,
    firstDayOfWeek = firstDayOfWeek.name,
    defaultEventPriority = defaultEventPriority.name,
    defaultEventDurationMinutes = defaultEventDurationMinutes,
    defaultSnoozeDurationMinutes = defaultSnoozeDurationMinutes,
    soundEnabled = soundEnabled,
    vibrationEnabled = vibrationEnabled,
    quietHours = quietHours.toBackupDto(),
    appLockEnabled = appLock.isEnabled,
    appLockTimeoutMinutes = appLock.timeoutMinutes,
    appLockOnBackground = appLock.lockOnBackground,
    notificationsEnabled = notificationsEnabled,
    alarmModeEnabled = alarmModeEnabled,
    notificationSoundType = notificationSound.type.name,
    notificationSoundCustomId = notificationSound.customSoundId,
    notificationSoundSysUri = notificationSound.systemUriString,
    notificationSoundBuiltInKey = notificationSound.builtInSoundKey,
    alarmSoundType = alarmSound.type.name,
    alarmSoundCustomId = alarmSound.customSoundId,
    alarmSoundSysUri = alarmSound.systemUriString,
    alarmSoundBuiltInKey = alarmSound.builtInSoundKey
)

fun SettingsBackupDto.toDomain(): AppSettings = AppSettings(
    themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
    use24HourFormat = use24HourFormat,
    dateFormat = runCatching { DateFormatPreference.valueOf(dateFormat) }.getOrDefault(DateFormatPreference.SYSTEM_DEFAULT),
    firstDayOfWeek = runCatching { DayOfWeek.valueOf(firstDayOfWeek) }.getOrDefault(DayOfWeek.MONDAY),
    defaultEventPriority = runCatching { Priority.valueOf(defaultEventPriority) }.getOrDefault(Priority.NORMAL),
    defaultEventDurationMinutes = if (defaultEventDurationMinutes > 0) defaultEventDurationMinutes else 60,
    defaultSnoozeDurationMinutes = if (defaultSnoozeDurationMinutes > 0) defaultSnoozeDurationMinutes else 15,
    soundEnabled = soundEnabled,
    vibrationEnabled = vibrationEnabled,
    quietHours = quietHours.toDomain(),
    appLock = com.example.offlinereminder.domain.model.AppLockSettings(
        isEnabled = appLockEnabled,
        timeoutMinutes = appLockTimeoutMinutes,
        lockOnBackground = appLockOnBackground
    ),
    notificationsEnabled = notificationsEnabled,
    alarmModeEnabled = alarmModeEnabled,
    notificationSound = com.example.offlinereminder.domain.model.SoundReference(
        type = runCatching { com.example.offlinereminder.domain.model.SoundSourceType.valueOf(notificationSoundType) }.getOrDefault(com.example.offlinereminder.domain.model.SoundSourceType.DEFAULT),
        customSoundId = notificationSoundCustomId,
        systemUriString = notificationSoundSysUri,
        builtInSoundKey = notificationSoundBuiltInKey
    ),
    alarmSound = com.example.offlinereminder.domain.model.SoundReference(
        type = runCatching { com.example.offlinereminder.domain.model.SoundSourceType.valueOf(alarmSoundType) }.getOrDefault(com.example.offlinereminder.domain.model.SoundSourceType.DEFAULT),
        customSoundId = alarmSoundCustomId,
        systemUriString = alarmSoundSysUri,
        builtInSoundKey = alarmSoundBuiltInKey
    )
)

// ----------------------------------------------------
// Event Domain <-> EventBackupDto
// ----------------------------------------------------
fun Event.toBackupDto(): EventBackupDto {
    val temporalDto = when (val t = temporal) {
        is EventDateTime.DateOnly -> TemporalBackupDto(
            type = "DATE_ONLY",
            startDate = t.startDate.toString()
        )
        is EventDateTime.DateTime -> TemporalBackupDto(
            type = "DATE_TIME",
            startDate = t.dateTime.toLocalDate().toString(),
            startTime = t.dateTime.toLocalTime().toString(),
            zoneId = t.zoneId.id
        )
        is EventDateTime.DateRange -> TemporalBackupDto(
            type = "DATE_RANGE",
            startDate = t.startDate.toString(),
            endDate = t.endDate.toString()
        )
        is EventDateTime.DateTimeRange -> TemporalBackupDto(
            type = "DATE_TIME_RANGE",
            startDate = t.startDateTime.toLocalDate().toString(),
            startTime = t.startDateTime.toLocalTime().toString(),
            endDate = t.endDateTime.toLocalDate().toString(),
            endTime = t.endDateTime.toLocalTime().toString(),
            zoneId = t.zoneId.id
        )
    }

    val locDto = location?.let {
        LocationBackupDto(
            name = it.placeName,
            address = it.addressText,
            latitude = it.latitude,
            longitude = it.longitude
        )
    }

    val recDto = recurrenceRule?.let { r ->
        val endType = when (r.endCondition) {
            is RecurrenceEnd.Never -> "NEVER"
            is RecurrenceEnd.UntilDate -> "UNTIL_DATE"
            is RecurrenceEnd.Count -> "COUNT"
        }
        val until = (r.endCondition as? RecurrenceEnd.UntilDate)?.date?.toString()
        val count = (r.endCondition as? RecurrenceEnd.Count)?.maxOccurrences

        RecurrenceRuleBackupDto(
            frequency = r.frequency.name,
            interval = r.interval,
            daysOfWeek = r.daysOfWeek.map { it.name },
            dayOfMonth = r.dayOfMonth,
            monthOfYear = r.monthOfYear,
            endConditionType = endType,
            untilDate = until,
            maxOccurrences = count
        )
    }

    val notifDtos = notificationSchedules.map { s ->
        NotificationScheduleBackupDto(
            id = s.id,
            offsetSeconds = s.offsetDuration.seconds,
            triggerType = s.triggerType.name,
            profileId = s.profileId
        )
    }

    val checkDto = checklist?.let { c ->
        ChecklistBackupDto(
            id = c.id,
            title = c.title,
            items = c.items.map { i ->
                ChecklistItemBackupDto(
                    id = i.id,
                    text = i.text,
                    isCompleted = i.isCompleted,
                    sortOrder = i.sortOrder
                )
            }
        )
    }

    val attDtos = attachments.map { a ->
        val ext = if (a.fileName.contains(".")) a.fileName.substringAfterLast(".") else ""
        val stableFileName = if (ext.isNotEmpty()) "${a.id}.$ext" else a.id
        AttachmentBackupDto(
            id = a.id,
            fileName = a.fileName,
            mimeType = a.mimeType,
            fileSizeBytes = a.fileSizeBytes,
            relativePath = "${BackupConstants.ATTACHMENTS_DIR}/$stableFileName",
            createdAt = a.createdAt.toString()
        )
    }

    return EventBackupDto(
        id = id,
        title = title,
        description = description,
        eventTypeId = eventTypeId,
        categoryId = categoryId,
        priority = priority.name,
        lifecycleStatus = lifecycleStatus.name,
        isFavorite = isFavorite,
        temporal = temporalDto,
        location = locDto,
        recurrenceRule = recDto,
        notificationSchedules = notifDtos,
        checklist = checkDto,
        groupIds = groupIds.toList(),
        tagIds = tagIds.toList(),
        parentEventId = parentEventId,
        templateId = templateId,
        customMetadata = customMetadata,
        attachments = attDtos,
        deletedAt = deletedAt?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}

fun EventBackupDto.toDomain(): Event {
    val temporalDomain = when (temporal.type) {
        "DATE_ONLY" -> EventDateTime.DateOnly(LocalDate.parse(temporal.startDate))
        "DATE_TIME" -> {
            val date = LocalDate.parse(temporal.startDate)
            val time = temporal.startTime?.let { LocalTime.parse(it) } ?: LocalTime.MIDNIGHT
            val zone = temporal.zoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
            EventDateTime.DateTime(date.atTime(time), zone)
        }
        "DATE_RANGE" -> {
            val start = LocalDate.parse(temporal.startDate)
            val end = temporal.endDate?.let { LocalDate.parse(it) } ?: start
            EventDateTime.DateRange(start, end)
        }
        "DATE_TIME_RANGE" -> {
            val sDate = LocalDate.parse(temporal.startDate)
            val sTime = temporal.startTime?.let { LocalTime.parse(it) } ?: LocalTime.MIDNIGHT
            val eDate = temporal.endDate?.let { LocalDate.parse(it) } ?: sDate
            val eTime = temporal.endTime?.let { LocalTime.parse(it) } ?: LocalTime.of(23, 59, 59)
            val zone = temporal.zoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
            EventDateTime.DateTimeRange(sDate.atTime(sTime), eDate.atTime(eTime), zone)
        }
        else -> EventDateTime.DateOnly(LocalDate.parse(temporal.startDate))
    }

    val locDomain = location?.let {
        LocationData(
            placeName = it.name.orEmpty(),
            addressText = it.address.orEmpty(),
            building = "",
            room = "",
            latitude = it.latitude,
            longitude = it.longitude
        )
    }

    val recDomain = recurrenceRule?.let { r ->
        val endCond = when (r.endConditionType) {
            "UNTIL_DATE" -> RecurrenceEnd.UntilDate(LocalDate.parse(r.untilDate))
            "COUNT" -> RecurrenceEnd.Count(r.maxOccurrences ?: 1)
            else -> RecurrenceEnd.Never
        }
        RecurrenceRule(
            frequency = RecurrenceFrequency.valueOf(r.frequency),
            interval = r.interval,
            daysOfWeek = r.daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
            dayOfMonth = r.dayOfMonth,
            monthOfYear = r.monthOfYear,
            endCondition = endCond
        )
    }

    val notifDomain = notificationSchedules.map { s ->
        NotificationSchedule(
            id = s.id,
            offsetDuration = Duration.ofSeconds(s.offsetSeconds),
            triggerType = NotificationTriggerType.valueOf(s.triggerType),
            profileId = s.profileId
        )
    }

    val checkDomain = checklist?.let { c ->
        Checklist(
            id = c.id,
            title = c.title,
            items = c.items.map { i ->
                ChecklistItem(
                    id = i.id,
                    text = i.text,
                    isCompleted = i.isCompleted,
                    sortOrder = i.sortOrder
                )
            }
        )
    }

    val attDomain = attachments.map { a ->
        val ext = if (a.fileName.contains(".")) a.fileName.substringAfterLast(".") else ""
        val stableFileName = if (ext.isNotEmpty()) "${a.id}.$ext" else a.id
        AttachmentMetadata(
            id = a.id,
            localUri = a.relativePath ?: "${BackupConstants.ATTACHMENTS_DIR}/$stableFileName",
            fileName = a.fileName,
            mimeType = a.mimeType,
            fileSizeBytes = a.fileSizeBytes,
            createdAt = Instant.parse(a.createdAt)
        )
    }

    return Event(
        id = id,
        title = title,
        description = description,
        eventTypeId = eventTypeId,
        categoryId = categoryId,
        priority = Priority.valueOf(priority),
        temporal = temporalDomain,
        location = locDomain,
        recurrenceRule = recDomain,
        notificationSchedules = notifDomain,
        checklist = checkDomain,
        lifecycleStatus = EventLifecycleStatus.valueOf(lifecycleStatus),
        isFavorite = isFavorite,
        groupIds = groupIds.toSet(),
        tagIds = tagIds.toSet(),
        parentEventId = parentEventId,
        templateId = templateId,
        customMetadata = customMetadata,
        attachments = attDomain,
        deletedAt = deletedAt?.let { Instant.parse(it) },
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )
}

fun NotificationScheduleBackupDto.toEntity(eventId: String): NotificationScheduleEntity = NotificationScheduleEntity(
    id = id,
    eventId = eventId,
    offsetDurationMillis = offsetSeconds * 1000L,
    triggerType = NotificationTriggerType.valueOf(triggerType),
    profileId = profileId
)

fun ChecklistBackupDto.toEntity(eventId: String): ChecklistEntity = ChecklistEntity(
    id = id,
    eventId = eventId,
    title = title
)

fun ChecklistItemBackupDto.toEntity(checklistId: String): ChecklistItemEntity = ChecklistItemEntity(
    id = id,
    checklistId = checklistId,
    text = text,
    isCompleted = isCompleted,
    sortOrder = sortOrder
)

fun AttachmentBackupDto.toEntity(eventId: String): AttachmentEntity = AttachmentEntity(
    id = id,
    eventId = eventId,
    localUri = relativePath ?: "attachments/${id}_${fileName}",
    fileName = fileName,
    mimeType = mimeType,
    fileSizeBytes = fileSizeBytes,
    createdAt = Instant.parse(createdAt)
)

fun com.example.offlinereminder.domain.model.SavedLocation.toBackupDto(): com.example.offlinereminder.domain.backup.SavedLocationBackupDto =
    com.example.offlinereminder.domain.backup.SavedLocationBackupDto(
        id = id,
        name = name,
        address = address,
        building = building,
        room = room,
        latitude = latitude,
        longitude = longitude,
        notes = notes,
        isFavorite = isFavorite,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

fun com.example.offlinereminder.domain.backup.SavedLocationBackupDto.toDomain(): com.example.offlinereminder.domain.model.SavedLocation =
    com.example.offlinereminder.domain.model.SavedLocation(
        id = id,
        name = name,
        address = address,
        building = building,
        room = room,
        latitude = latitude,
        longitude = longitude,
        notes = notes,
        isFavorite = isFavorite,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )
