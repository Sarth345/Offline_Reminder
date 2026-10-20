package com.example.offlinereminder.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Named, persistent collection of events.
 * Groups reference events via foreign keys / membership IDs and never duplicate event records.
 */
data class EventGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val color: Int? = null,
    val icon: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
