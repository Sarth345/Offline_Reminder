package com.example.offlinereminder.presentation.notificationprofiles

import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.model.NotificationProfile

data class NotificationProfilesUiState(
    val profiles: List<NotificationProfile> = emptyList(),
    val customSounds: List<CustomSoundMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)
