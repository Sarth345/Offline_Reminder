package com.example.offlinereminder.domain.model

import java.time.Duration
import java.time.Instant

data class EventTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val eventTypeId: String,
    val categoryId: String,
    val priority: Priority = Priority.NORMAL,
    val defaultDuration: Duration = Duration.ofHours(1),
    val isAllDay: Boolean = false,
    val location: LocationData? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val notificationSchedules: List<NotificationSchedule> = emptyList(),
    val checklist: Checklist? = null,
    val tagIds: Set<String> = emptySet(),
    val createdAt: Instant,
    val updatedAt: Instant
)
