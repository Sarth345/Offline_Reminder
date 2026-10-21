package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundSourceType

@Entity(
    tableName = "notification_profiles",
    foreignKeys = [
        ForeignKey(
            entity = CustomSoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["custom_sound_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["custom_sound_id"])
    ]
)
data class NotificationProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "delivery_type")
    val deliveryType: ReminderDeliveryType = ReminderDeliveryType.NOTIFICATION,
    @ColumnInfo(name = "sound_source")
    val soundSource: SoundSourceType = SoundSourceType.DEFAULT,
    @ColumnInfo(name = "custom_sound_id")
    val customSoundId: String? = null,
    @ColumnInfo(name = "system_sound_uri")
    val systemSoundUri: String? = null,
    @ColumnInfo(name = "vibration_enabled")
    val vibrationEnabled: Boolean = true,
    @ColumnInfo(name = "default_snooze_duration_minutes")
    val defaultSnoozeDurationMinutes: Int = 10,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false
)
