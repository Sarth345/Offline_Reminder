package com.example.offlinereminder.domain.model

data class NotificationProfile(
    val id: String,
    val name: String,
    val deliveryType: ReminderDeliveryType = ReminderDeliveryType.NOTIFICATION,
    val sound: SoundReference = SoundReference(SoundSourceType.DEFAULT),
    val vibrationEnabled: Boolean = true,
    val defaultSnoozeDurationMinutes: Int = 10,
    val isDefault: Boolean = false
)
