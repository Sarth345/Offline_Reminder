package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundSourceType

@Entity(
    tableName = "notification_schedules",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomSoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["custom_sound_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["custom_sound_id"])
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
    val profileId: String? = null,
    @ColumnInfo(name = "delivery_type")
    val deliveryType: ReminderDeliveryType = ReminderDeliveryType.NOTIFICATION,
    @ColumnInfo(name = "sound_source")
    val soundSource: SoundSourceType = SoundSourceType.DEFAULT,
    @ColumnInfo(name = "custom_sound_id")
    val customSoundId: String? = null,
    @ColumnInfo(name = "system_sound_uri")
    val systemSoundUri: String? = null
)
