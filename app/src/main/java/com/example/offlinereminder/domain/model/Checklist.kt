package com.example.offlinereminder.domain.model

import java.util.UUID

/**
 * A task or item within an event checklist.
 */
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * An embedded checklist within an event definition.
 */
data class Checklist(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val items: List<ChecklistItem> = emptyList()
) {
    val totalCount: Int get() = items.size
    val completedCount: Int get() = items.count { it.isCompleted }
    val progressPercentage: Float
        get() = if (totalCount == 0) 0f else (completedCount.toFloat() / totalCount)
    val isAllCompleted: Boolean
        get() = totalCount > 0 && completedCount == totalCount
}
