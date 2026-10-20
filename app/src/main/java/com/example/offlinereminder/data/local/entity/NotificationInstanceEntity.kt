package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDateTime

@Entity(
    tableName = "notification_instances",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NotificationScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["schedule_id"]),
        Index(value = ["trigger_epoch_millis"]),
        Index(value = ["status"])
    ]
)
data class NotificationInstanceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "occurrence_original_start_date_time")
    val occurrenceOriginalStartDateTime: LocalDateTime,
    @ColumnInfo(name = "occurrence_original_zone_id")
    val occurrenceOriginalZoneId: String,
    @ColumnInfo(name = "schedule_id")
    val scheduleId: String? = null,
    @ColumnInfo(name = "trigger_epoch_millis")
    val triggerEpochMillis: Long,
    val status: String = "SCHEDULED",
    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int = 0,
    @ColumnInfo(name = "snooze_until")
    val snoozeUntil: Instant? = null,
    @ColumnInfo(name = "request_code")
    val requestCode: Int
)
