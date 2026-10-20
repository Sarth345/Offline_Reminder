package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.entity.EventTagCrossRef
import com.example.offlinereminder.data.local.relation.EventWithDetails
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceRule
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

fun Event.toEntity(): EventEntity {
    val temporalType: String
    val startDate: java.time.LocalDate
    val startTime: LocalTime?
    val endDate: java.time.LocalDate?
    val endTime: LocalTime?
    val zoneIdStr: String

    when (val t = temporal) {
        is EventDateTime.DateOnly -> {
            temporalType = "DATE_ONLY"
            startDate = t.startDate
            startTime = null
            endDate = null
            endTime = null
            zoneIdStr = ZoneId.systemDefault().id
        }
        is EventDateTime.DateTime -> {
            temporalType = "DATE_TIME"
            startDate = t.dateTime.toLocalDate()
            startTime = t.dateTime.toLocalTime()
            endDate = null
            endTime = null
            zoneIdStr = t.zoneId.id
        }
        is EventDateTime.DateRange -> {
            temporalType = "DATE_RANGE"
            startDate = t.startDate
            startTime = null
            endDate = t.endDate
            endTime = null
            zoneIdStr = ZoneId.systemDefault().id
        }
        is EventDateTime.DateTimeRange -> {
            temporalType = "DATE_TIME_RANGE"
            startDate = t.startDateTime.toLocalDate()
            startTime = t.startDateTime.toLocalTime()
            endDate = t.endDateTime.toLocalDate()
            endTime = t.endDateTime.toLocalTime()
            zoneIdStr = t.zoneId.id
        }
    }

    val loc = location
    val r = recurrenceRule

    return EventEntity(
        id = id,
        title = title,
        description = description,
        eventTypeId = eventTypeId,
        categoryId = categoryId,
        priority = priority,
        temporalType = temporalType,
        startDate = startDate,
        startTime = startTime,
        endDate = endDate,
        endTime = endTime,
        zoneId = zoneIdStr,
        locationPlaceName = loc?.placeName?.takeIf { it.isNotBlank() },
        locationAddressText = loc?.addressText?.takeIf { it.isNotBlank() },
        locationBuilding = loc?.building?.takeIf { it.isNotBlank() },
        locationRoom = loc?.room?.takeIf { it.isNotBlank() },
        locationLatitude = loc?.latitude,
        locationLongitude = loc?.longitude,
        hasRecurrence = r != null,
        recurrenceFrequency = r?.frequency,
        recurrenceInterval = r?.interval,
        recurrenceDaysOfWeek = r?.daysOfWeek,
        recurrenceDayOfMonth = r?.dayOfMonth,
        recurrenceSetPosition = r?.setPosition,
        recurrenceMonthOfYear = r?.monthOfYear,
        recurrenceEndType = when (r?.endCondition) {
            is RecurrenceEnd.UntilDate -> "UNTIL_DATE"
            is RecurrenceEnd.Count -> "COUNT"
            RecurrenceEnd.Never -> "NEVER"
            null -> null
        },
        recurrenceUntilDate = (r?.endCondition as? RecurrenceEnd.UntilDate)?.date,
        recurrenceMaxOccurrences = (r?.endCondition as? RecurrenceEnd.Count)?.maxOccurrences,
        recurrenceExcludedDates = r?.excludedDates,
        lifecycleStatus = lifecycleStatus,
        isFavorite = isFavorite,
        parentEventId = parentEventId,
        templateId = templateId,
        customMetadata = customMetadata,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Event.toGroupCrossRefs(): List<EventGroupCrossRef> =
    groupIds.map { EventGroupCrossRef(eventId = id, groupId = it) }

fun Event.toTagCrossRefs(): List<EventTagCrossRef> =
    tagIds.map { EventTagCrossRef(eventId = id, tagId = it) }

fun EventWithDetails.toDomain(): Event {
    val e = event

    val temporal: EventDateTime = when (e.temporalType) {
        "DATE_ONLY" -> EventDateTime.DateOnly(e.startDate)
        "DATE_TIME" -> EventDateTime.DateTime(
            dateTime = LocalDateTime.of(e.startDate, e.startTime ?: LocalTime.MIDNIGHT),
            zoneId = ZoneId.of(e.zoneId)
        )
        "DATE_RANGE" -> EventDateTime.DateRange(
            startDate = e.startDate,
            endDate = e.endDate ?: e.startDate
        )
        "DATE_TIME_RANGE" -> EventDateTime.DateTimeRange(
            startDateTime = LocalDateTime.of(e.startDate, e.startTime ?: LocalTime.MIDNIGHT),
            endDateTime = LocalDateTime.of(e.endDate ?: e.startDate, e.endTime ?: LocalTime.MIDNIGHT),
            zoneId = ZoneId.of(e.zoneId)
        )
        else -> EventDateTime.DateOnly(e.startDate)
    }

    val location: LocationData? = if (
        !e.locationPlaceName.isNullOrBlank() ||
        !e.locationAddressText.isNullOrBlank() ||
        !e.locationBuilding.isNullOrBlank() ||
        !e.locationRoom.isNullOrBlank() ||
        (e.locationLatitude != null && e.locationLongitude != null)
    ) {
        LocationData(
            placeName = e.locationPlaceName ?: "",
            addressText = e.locationAddressText ?: "",
            building = e.locationBuilding ?: "",
            room = e.locationRoom ?: "",
            latitude = e.locationLatitude,
            longitude = e.locationLongitude
        )
    } else {
        null
    }

    val recurrenceRule: RecurrenceRule? = if (e.hasRecurrence && e.recurrenceFrequency != null) {
        val endCondition: RecurrenceEnd = when (e.recurrenceEndType) {
            "UNTIL_DATE" -> e.recurrenceUntilDate?.let { RecurrenceEnd.UntilDate(it) } ?: RecurrenceEnd.Never
            "COUNT" -> e.recurrenceMaxOccurrences?.let { RecurrenceEnd.Count(it) } ?: RecurrenceEnd.Never
            else -> RecurrenceEnd.Never
        }
        RecurrenceRule(
            frequency = e.recurrenceFrequency,
            interval = e.recurrenceInterval ?: 1,
            daysOfWeek = e.recurrenceDaysOfWeek ?: emptySet(),
            dayOfMonth = e.recurrenceDayOfMonth,
            setPosition = e.recurrenceSetPosition,
            monthOfYear = e.recurrenceMonthOfYear,
            endCondition = endCondition,
            excludedDates = e.recurrenceExcludedDates ?: emptySet()
        )
    } else {
        null
    }

    return Event(
        id = e.id,
        title = e.title,
        description = e.description,
        eventTypeId = e.eventTypeId,
        categoryId = e.categoryId,
        priority = e.priority,
        temporal = temporal,
        location = location,
        recurrenceRule = recurrenceRule,
        notificationSchedules = notificationSchedules.map { it.toDomain() },
        checklist = checklistWithItems?.toDomain(),
        lifecycleStatus = e.lifecycleStatus,
        isFavorite = e.isFavorite,
        groupIds = groups.map { it.id }.toSet(),
        tagIds = tags.map { it.id }.toSet(),
        parentEventId = e.parentEventId,
        templateId = e.templateId,
        customMetadata = e.customMetadata,
        attachments = attachments.map { it.toDomain() },
        deletedAt = e.deletedAt,
        createdAt = e.createdAt,
        updatedAt = e.updatedAt
    )
}
