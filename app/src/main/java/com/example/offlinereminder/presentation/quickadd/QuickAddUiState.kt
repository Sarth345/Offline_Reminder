package com.example.offlinereminder.presentation.quickadd

import com.example.offlinereminder.domain.quickadd.QuickAddDraft

/**
 * Immutable UI State for the Quick Add bottom sheet / modal.
 */
data class QuickAddUiState(
    val input: String = "",
    val draft: QuickAddDraft? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val savedEventId: String? = null,
    val errorMessage: String? = null
) {
    val canSave: Boolean
        get() = !isSaving && draft != null && draft.isValid
}

/**
 * User actions and events for the Quick Add flow.
 */
sealed interface QuickAddAction {
    data class UpdateInput(val text: String) : QuickAddAction
    data object Save : QuickAddAction
    data object Reset : QuickAddAction
    data object ClearError : QuickAddAction
}
