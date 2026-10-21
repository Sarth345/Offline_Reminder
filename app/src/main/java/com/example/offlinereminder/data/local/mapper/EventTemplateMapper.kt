package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.EventTemplateEntity
import com.example.offlinereminder.domain.backup.ChecklistBackupDto
import com.example.offlinereminder.domain.backup.ChecklistItemBackupDto
import com.example.offlinereminder.domain.backup.NotificationScheduleBackupDto
import com.example.offlinereminder.domain.backup.RecurrenceRuleBackupDto
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true }

fun EventTemplateEntity.toDomain(): EventTemplate {
    val rule = recurrenceRuleJson?.let { str ->
        runCatching {
            val dto = json.decodeFromString<RecurrenceRuleBackupDto>(str)
            val endCond: RecurrenceEnd = when (dto.endConditionType) {
                "UNTIL_DATE" -> dto.untilDate?.let { RecurrenceEnd.UntilDate(LocalDate.parse(it)) } ?: RecurrenceEnd.Never
                "COUNT" -> dto.maxOccurrences?.let { RecurrenceEnd.Count(it) } ?: RecurrenceEnd.Never
                else -> RecurrenceEnd.Never
            }
            RecurrenceRule(
                frequency = RecurrenceFrequency.valueOf(dto.frequency),
                interval = dto.interval,
                daysOfWeek = dto.daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
                dayOfMonth = dto.dayOfMonth,
                monthOfYear = dto.monthOfYear,
                endCondition = endCond
            )
        }.getOrNull()
    }

    val schedules = notificationSchedulesJson?.let { str ->
        runCatching {
            json.decodeFromString<List<NotificationScheduleBackupDto>>(str).map { dto ->
                NotificationSchedule(
                    id = dto.id,
                    offsetDuration = Duration.ofSeconds(dto.offsetSeconds),
                    triggerType = NotificationTriggerType.valueOf(dto.triggerType),
                    profileId = dto.profileId
                )
            }
        }.getOrNull()
    } ?: emptyList()

    val checklistDomain = checklistJson?.let { str ->
        runCatching {
            val dto = json.decodeFromString<ChecklistBackupDto>(str)
            Checklist(
                id = dto.id,
                title = dto.title,
                items = dto.items.map { itemDto ->
                    ChecklistItem(
                        id = itemDto.id,
                        text = itemDto.text,
                        isCompleted = itemDto.isCompleted,
                        sortOrder = itemDto.sortOrder
                    )
                }
            )
        }.getOrNull()
    }

    val tags = tagIdsJson?.let { str ->
        runCatching {
            json.decodeFromString<List<String>>(str).toSet()
        }.getOrNull()
    } ?: emptySet()

    val loc = if (locationName != null || locationAddress != null) {
        LocationData(
            placeName = locationName ?: "",
            addressText = locationAddress ?: ""
        )
    } else null

    return EventTemplate(
        id = id,
        name = name,
        description = description,
        eventTypeId = eventTypeId,
        categoryId = categoryId,
        priority = priority,
        defaultDuration = Duration.ofMillis(defaultDurationMillis),
        isAllDay = isAllDay,
        location = loc,
        recurrenceRule = rule,
        notificationSchedules = schedules,
        checklist = checklistDomain,
        tagIds = tags,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun EventTemplate.toEntity(): EventTemplateEntity {
    val ruleJson = recurrenceRule?.let { r ->
        val until = (r.endCondition as? RecurrenceEnd.UntilDate)?.date?.toString()
        val count = (r.endCondition as? RecurrenceEnd.Count)?.maxOccurrences
        val endType = when (r.endCondition) {
            is RecurrenceEnd.UntilDate -> "UNTIL_DATE"
            is RecurrenceEnd.Count -> "COUNT"
            else -> "NEVER"
        }
        val dto = RecurrenceRuleBackupDto(
            frequency = r.frequency.name,
            interval = r.interval,
            daysOfWeek = r.daysOfWeek.map { it.name },
            dayOfMonth = r.dayOfMonth,
            monthOfYear = r.monthOfYear,
            endConditionType = endType,
            untilDate = until,
            maxOccurrences = count
        )
        json.encodeToString(dto)
    }

    val schedulesJson = if (notificationSchedules.isNotEmpty()) {
        val dtos = notificationSchedules.map { s ->
            NotificationScheduleBackupDto(
                id = s.id,
                offsetSeconds = s.offsetDuration.seconds,
                triggerType = s.triggerType.name,
                profileId = s.profileId
            )
        }
        json.encodeToString(dtos)
    } else null

    val checklistJsonStr = checklist?.let { c ->
        val dto = ChecklistBackupDto(
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
        json.encodeToString(dto)
    }

    val tagsJsonStr = if (tagIds.isNotEmpty()) {
        json.encodeToString(tagIds.toList())
    } else null

    return EventTemplateEntity(
        id = id,
        name = name,
        description = description,
        eventTypeId = eventTypeId,
        categoryId = categoryId,
        priority = priority,
        defaultDurationMillis = defaultDuration.toMillis(),
        isAllDay = isAllDay,
        locationName = location?.placeName,
        locationAddress = location?.addressText,
        hasRecurrence = recurrenceRule != null,
        recurrenceRuleJson = ruleJson,
        notificationSchedulesJson = schedulesJson,
        checklistJson = checklistJsonStr,
        tagIdsJson = tagsJsonStr,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
