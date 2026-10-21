package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.offlinereminder.domain.model.Priority
import java.time.Instant

@Entity(
    tableName = "event_templates",
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
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["event_type_id"])
    ]
)
data class EventTemplateEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    @ColumnInfo(name = "event_type_id")
    val eventTypeId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    val priority: Priority = Priority.NORMAL,
    @ColumnInfo(name = "default_duration_millis")
    val defaultDurationMillis: Long,
    @ColumnInfo(name = "is_all_day")
    val isAllDay: Boolean = false,
    @ColumnInfo(name = "location_name")
    val locationName: String? = null,
    @ColumnInfo(name = "location_address")
    val locationAddress: String? = null,
    @ColumnInfo(name = "has_recurrence")
    val hasRecurrence: Boolean = false,
    @ColumnInfo(name = "recurrence_rule_json")
    val recurrenceRuleJson: String? = null,
    @ColumnInfo(name = "notification_schedules_json")
    val notificationSchedulesJson: String? = null,
    @ColumnInfo(name = "checklist_json")
    val checklistJson: String? = null,
    @ColumnInfo(name = "tag_ids_json")
    val tagIdsJson: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
)
