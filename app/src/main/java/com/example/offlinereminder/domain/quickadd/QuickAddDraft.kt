package com.example.offlinereminder.domain.quickadd

import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceRule
import java.time.LocalDate
import java.time.LocalTime

/**
 * Immutable domain representation of a deterministically parsed Quick Add input.
 * Contains preview fields, extracted tokens, and validation status.
 */
data class QuickAddDraft(
    val rawInput: String,
    val title: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val isAllDay: Boolean = true,
    val recurrenceRule: RecurrenceRule? = null,
    val priority: Priority = Priority.NORMAL,
    val categoryName: String? = null,
    val groupNames: List<String> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val reminderMinutesBefore: Int? = null,
    val validationErrors: List<String> = emptyList(),
    val ambiguities: List<String> = emptyList(),
    val unparsedTokens: List<String> = emptyList()
) {
    val isValid: Boolean
        get() = title.isNotBlank() && validationErrors.isEmpty()

    val hasDateTime: Boolean
        get() = date != null || time != null
}
