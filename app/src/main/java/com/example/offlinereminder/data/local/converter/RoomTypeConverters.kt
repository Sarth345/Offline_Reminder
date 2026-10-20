package com.example.offlinereminder.data.local.converter

import androidx.room.TypeConverter
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RelationshipType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RoomTypeConverters {

    private val json = Json { ignoreUnknownKeys = true }

    // Instant
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // LocalDate
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    // LocalTime
    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    // LocalDateTime
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }

    // Priority
    @TypeConverter
    fun fromPriority(value: Priority?): String? = value?.name

    @TypeConverter
    fun toPriority(value: String?): Priority? = value?.let { Priority.valueOf(it) }

    // EventLifecycleStatus
    @TypeConverter
    fun fromLifecycleStatus(value: EventLifecycleStatus?): String? = value?.name

    @TypeConverter
    fun toLifecycleStatus(value: String?): EventLifecycleStatus? = value?.let { EventLifecycleStatus.valueOf(it) }

    // OccurrenceStatus
    @TypeConverter
    fun fromOccurrenceStatus(value: OccurrenceStatus?): String? = value?.name

    @TypeConverter
    fun toOccurrenceStatus(value: String?): OccurrenceStatus? = value?.let { OccurrenceStatus.valueOf(it) }

    // RecurrenceFrequency
    @TypeConverter
    fun fromFrequency(value: RecurrenceFrequency?): String? = value?.name

    @TypeConverter
    fun toFrequency(value: String?): RecurrenceFrequency? = value?.let { RecurrenceFrequency.valueOf(it) }

    // NotificationTriggerType
    @TypeConverter
    fun fromTriggerType(value: NotificationTriggerType?): String? = value?.name

    @TypeConverter
    fun toTriggerType(value: String?): NotificationTriggerType? = value?.let { NotificationTriggerType.valueOf(it) }

    // DependencyType
    @TypeConverter
    fun fromDependencyType(value: DependencyType?): String? = value?.name

    @TypeConverter
    fun toDependencyType(value: String?): DependencyType? = value?.let { DependencyType.valueOf(it) }

    // RelationshipType
    @TypeConverter
    fun fromRelationshipType(value: RelationshipType?): String? = value?.name

    @TypeConverter
    fun toRelationshipType(value: String?): RelationshipType? = value?.let { RelationshipType.valueOf(it) }

    // Set<DayOfWeek>
    @TypeConverter
    fun fromDaysOfWeek(value: Set<DayOfWeek>?): String? {
        if (value.isNullOrEmpty()) return null
        return value.joinToString(separator = ",") { it.name }
    }

    @TypeConverter
    fun toDaysOfWeek(value: String?): Set<DayOfWeek>? {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(",")
            .mapNotNull { name ->
                try {
                    DayOfWeek.valueOf(name.trim())
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            .toSet()
    }

    // Set<LocalDate>
    @TypeConverter
    fun fromLocalDateSet(value: Set<LocalDate>?): String? {
        if (value.isNullOrEmpty()) return null
        return value.joinToString(separator = ",") { it.toString() }
    }

    @TypeConverter
    fun toLocalDateSet(value: String?): Set<LocalDate>? {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(",")
            .mapNotNull { str ->
                try {
                    LocalDate.parse(str.trim())
                } catch (_: Exception) {
                    null
                }
            }
            .toSet()
    }

    // List<String> (e.g. aliases)
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Map<String, String> (custom metadata)
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        if (value == null) return null
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        if (value == null) return null
        return try {
            json.decodeFromString<Map<String, String>>(value)
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
