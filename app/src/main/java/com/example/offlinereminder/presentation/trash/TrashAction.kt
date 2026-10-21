package com.example.offlinereminder.presentation.trash

sealed interface TrashAction {
    data object LoadTrash : TrashAction
    data class RestoreEvent(val eventId: String) : TrashAction
    data class PermanentlyDelete(val eventId: String) : TrashAction
    data object EmptyTrash : TrashAction
    data object ClearMessage : TrashAction
}
