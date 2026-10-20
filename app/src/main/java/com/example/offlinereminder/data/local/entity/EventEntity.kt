package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = EventTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_type_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_event_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["event_type_id"]),
        Index(value = ["lifecycle_status"]),
        Index(value = ["parent_event_id"]),
        Index(value = ["start_date"]),
        Index(value = ["is_favorite"])
    ]
)
data class EventEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String = "",
    @ColumnInfo(name = "event_type_id")
    val eventTypeId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    val priority: Priority = Priority.NORMAL,

    // Temporal fields
    @ColumnInfo(name = "temporal_type")
    val temporalType: String,
    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,
    @ColumnInfo(name = "start_time")
    val startTime: LocalTime? = null,
    @ColumnInfo(name = "end_date")
    val endDate: LocalDate? = null,
    @ColumnInfo(name = "end_time")
    val endTime: LocalTime? = null,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,

    // Location fields
    @ColumnInfo(name = "location_place_name")
    val locationPlaceName: String? = null,
    @ColumnInfo(name = "location_address_text")
    val locationAddressText: String? = null,
    @ColumnInfo(name = "location_building")
    val locationBuilding: String? = null,
    @ColumnInfo(name = "location_room")
    val locationRoom: String? = null,
    @ColumnInfo(name = "location_latitude")
    val locationLatitude: Double? = null,
    @ColumnInfo(name = "location_longitude")
    val locationLongitude: Double? = null,

    // Recurrence fields
    @ColumnInfo(name = "has_recurrence")
    val hasRecurrence: Boolean = false,
    @ColumnInfo(name = "recurrence_frequency")
    val recurrenceFrequency: RecurrenceFrequency? = null,
    @ColumnInfo(name = "recurrence_interval")
    val recurrenceInterval: Int? = null,
    @ColumnInfo(name = "recurrence_days_of_week")
    val recurrenceDaysOfWeek: Set<DayOfWeek>? = null,
    @ColumnInfo(name = "recurrence_day_of_month")
    val recurrenceDayOfMonth: Int? = null,
    @ColumnInfo(name = "recurrence_set_position")
    val recurrenceSetPosition: Int? = null,
    @ColumnInfo(name = "recurrence_month_of_year")
    val recurrenceMonthOfYear: Int? = null,
    @ColumnInfo(name = "recurrence_end_type")
    val recurrenceEndType: String? = null,
    @ColumnInfo(name = "recurrence_until_date")
    val recurrenceUntilDate: LocalDate? = null,
    @ColumnInfo(name = "recurrence_max_occurrences")
    val recurrenceMaxOccurrences: Int? = null,
    @ColumnInfo(name = "recurrence_excluded_dates")
    val recurrenceExcludedDates: Set<LocalDate>? = null,

    // Lifecycle & Hierarchy
    @ColumnInfo(name = "lifecycle_status")
    val lifecycleStatus: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "parent_event_id")
    val parentEventId: String? = null,
    @ColumnInfo(name = "template_id")
    val templateId: String? = null,
    @ColumnInfo(name = "custom_metadata")
    val customMetadata: Map<String, String> = emptyMap(),
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
)
