package com.example.offlinereminder.presentation.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.SavedLocation
import com.example.offlinereminder.domain.usecase.DeleteSavedLocationUseCase
import com.example.offlinereminder.domain.usecase.GetSavedLocationsUseCase
import com.example.offlinereminder.domain.usecase.SaveSavedLocationUseCase
import com.example.offlinereminder.domain.usecase.ToggleSavedLocationFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedLocationsUiState(
    val locations: List<SavedLocation> = emptyList(),
    val searchQuery: String = "",
    val isEditorOpen: Boolean = false,
    val editingLocation: SavedLocation? = null,
    val errorMessage: String? = null
)

class SavedLocationsViewModel(
    private val getSavedLocationsUseCase: GetSavedLocationsUseCase,
    private val saveSavedLocationUseCase: SaveSavedLocationUseCase,
    private val deleteSavedLocationUseCase: DeleteSavedLocationUseCase,
    private val toggleSavedLocationFavoriteUseCase: ToggleSavedLocationFavoriteUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedLocationsUiState())
    val uiState: StateFlow<SavedLocationsUiState> = _uiState.asStateFlow()

    init {
        observeLocations()
    }

    private fun observeLocations() {
        viewModelScope.launch(dispatcherProvider.io) {
            getSavedLocationsUseCase().collect { list ->
                _uiState.update { it.copy(locations = list) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(isEditorOpen = true, editingLocation = null, errorMessage = null) }
    }

    fun openEditDialog(location: SavedLocation) {
        _uiState.update { it.copy(isEditorOpen = true, editingLocation = location, errorMessage = null) }
    }

    fun closeDialog() {
        _uiState.update { it.copy(isEditorOpen = false, editingLocation = null, errorMessage = null) }
    }

    fun saveLocation(
        name: String,
        address: String,
        building: String,
        room: String,
        latitude: Double?,
        longitude: Double?,
        notes: String,
        isFavorite: Boolean
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            val result = saveSavedLocationUseCase(
                id = _uiState.value.editingLocation?.id,
                name = name,
                address = address,
                building = building,
                room = room,
                latitude = latitude,
                longitude = longitude,
                notes = notes,
                isFavorite = isFavorite
            )
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isEditorOpen = false, editingLocation = null) }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to save location") }
                }
            }
        }
    }

    fun deleteLocation(id: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            deleteSavedLocationUseCase(id)
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            toggleSavedLocationFavoriteUseCase(id)
        }
    }
}
