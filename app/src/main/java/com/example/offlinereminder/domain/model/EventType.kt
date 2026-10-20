package com.example.offlinereminder.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Event classification template/catalog item (e.g. Birthday, Meeting, Bill, Exam).
 */
data class EventType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val key: String,
    val categoryId: String,
    val defaultColor: Int? = null,
    val defaultIcon: String? = null,
    val aliases: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Instant
)
