package com.example.offlinereminder.presentation.archive

sealed interface ArchiveAction {
    data object LoadArchive : ArchiveAction
    data class UnarchiveEvent(val eventId: String) : ArchiveAction
    data class MoveToTrash(val eventId: String) : ArchiveAction
    data class PermanentlyDelete(val eventId: String) : ArchiveAction
    data object ClearMessage : ArchiveAction
}
