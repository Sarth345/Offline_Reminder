package com.example.offlinereminder.presentation.notificationprofiles

import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference

sealed interface NotificationProfilesAction {
    data object LoadData : NotificationProfilesAction
    data class CreateProfile(
        val name: String,
        val deliveryType: ReminderDeliveryType,
        val sound: SoundReference,
        val vibrationEnabled: Boolean,
        val defaultSnoozeMinutes: Int,
        val isDefault: Boolean
    ) : NotificationProfilesAction
    data class UpdateProfile(val profile: NotificationProfile) : NotificationProfilesAction
    data class DeleteProfile(val profileId: String) : NotificationProfilesAction
    data class SetDefaultProfile(val profileId: String) : NotificationProfilesAction
    data class RegisterCustomSound(
        val title: String,
        val fileName: String,
        val mimeType: String,
        val fileSizeBytes: Long
    ) : NotificationProfilesAction
    data class DeleteCustomSound(val soundId: String) : NotificationProfilesAction
    data object ClearMessage : NotificationProfilesAction
}
