package com.example.offlinereminder.presentation.notificationprofiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.usecase.AddCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.CreateNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.DeleteCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.DeleteNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.GetCustomSoundsUseCase
import com.example.offlinereminder.domain.usecase.GetNotificationProfilesUseCase
import com.example.offlinereminder.domain.usecase.SetDefaultNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.UpdateNotificationProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationProfilesViewModel(
    private val getNotificationProfilesUseCase: GetNotificationProfilesUseCase,
    private val createNotificationProfileUseCase: CreateNotificationProfileUseCase,
    private val updateNotificationProfileUseCase: UpdateNotificationProfileUseCase,
    private val deleteNotificationProfileUseCase: DeleteNotificationProfileUseCase,
    private val setDefaultNotificationProfileUseCase: SetDefaultNotificationProfileUseCase,
    private val getCustomSoundsUseCase: GetCustomSoundsUseCase,
    private val addCustomSoundUseCase: AddCustomSoundUseCase,
    private val deleteCustomSoundUseCase: DeleteCustomSoundUseCase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationProfilesUiState(isLoading = true))
    val uiState: StateFlow<NotificationProfilesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun onAction(action: NotificationProfilesAction) {
        when (action) {
            is NotificationProfilesAction.LoadData -> loadData()
            is NotificationProfilesAction.CreateProfile -> createProfile(
                action.name,
                action.deliveryType,
                action.sound,
                action.vibrationEnabled,
                action.defaultSnoozeMinutes,
                action.isDefault
            )
            is NotificationProfilesAction.UpdateProfile -> updateProfile(action.profile)
            is NotificationProfilesAction.DeleteProfile -> deleteProfile(action.profileId)
            is NotificationProfilesAction.SetDefaultProfile -> setDefaultProfile(action.profileId)
            is NotificationProfilesAction.RegisterCustomSound -> registerCustomSound(
                action.title,
                action.fileName,
                action.mimeType,
                action.fileSizeBytes
            )
            is NotificationProfilesAction.DeleteCustomSound -> deleteCustomSound(action.soundId)
            is NotificationProfilesAction.ClearMessage -> _uiState.update { it.copy(actionMessage = null, errorMessage = null) }
        }
    }

    private fun loadData() {
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                getNotificationProfilesUseCase(),
                getCustomSoundsUseCase()
            ) { profiles, sounds ->
                profiles to sounds
            }.collect { (profiles, sounds) ->
                _uiState.update {
                    it.copy(
                        profiles = profiles,
                        customSounds = sounds,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun createProfile(
        name: String,
        deliveryType: ReminderDeliveryType,
        sound: SoundReference,
        vibrationEnabled: Boolean,
        defaultSnoozeMinutes: Int,
        isDefault: Boolean
    ) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = createNotificationProfileUseCase(
                name = name,
                deliveryType = deliveryType,
                sound = sound,
                vibrationEnabled = vibrationEnabled,
                defaultSnoozeDurationMinutes = defaultSnoozeMinutes,
                isDefault = isDefault
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Created profile \"${result.data.name}\"") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to create profile") }
                }
            }
        }
    }

    private fun updateProfile(profile: NotificationProfile) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = updateNotificationProfileUseCase(profile)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Updated profile \"${profile.name}\"") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to update profile") }
                }
            }
        }
    }

    private fun deleteProfile(profileId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = deleteNotificationProfileUseCase(profileId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Profile deleted successfully") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to delete profile") }
                }
            }
        }
    }

    private fun setDefaultProfile(profileId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = setDefaultNotificationProfileUseCase(profileId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Default notification profile updated") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to set default profile") }
                }
            }
        }
    }

    private fun registerCustomSound(title: String, fileName: String, mimeType: String, fileSizeBytes: Long) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = addCustomSoundUseCase(title, fileName, mimeType, fileSizeBytes)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Registered sound: ${result.data.title}") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to register custom sound") }
                }
            }
        }
    }

    private fun deleteCustomSound(soundId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = deleteCustomSoundUseCase(soundId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Custom sound deleted") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to delete sound") }
                }
            }
        }
    }
}
