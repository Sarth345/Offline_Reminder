package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.offlinereminder.domain.model.NotificationTriggerType

@Entity(
    tableName = "notification_schedules",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_id"])
    ]
)
data class NotificationScheduleEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "offset_duration_millis")
    val offsetDurationMillis: Long,
    @ColumnInfo(name = "trigger_type")
    val triggerType: NotificationTriggerType,
    @ColumnInfo(name = "profile_id")
    val profileId: String? = null
)
