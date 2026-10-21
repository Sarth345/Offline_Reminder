package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Creates an independent clone of an existing [Event].
 *
 * Product Cloning Semantics:
 * - Identity: Fresh [Event.id] (UUID).
 * - Title: Uses [newTitle] if provided, otherwise defaults to "${source.title} (Copy)".
 * - Lifecycle: Always starts as [EventLifecycleStatus.ACTIVE] with [Event.deletedAt] = null.
 * - Favorites: Reset to false ([Event.isFavorite] = false); duplicates start unpinned.
 * - Temporal Bounds: Preserved from source, or shifted to [newStartDateTime] while preserving duration.
 * - Schedules: Fresh schedule UUIDs generated; notification policies copied; runtime NotificationInstance state is OMITTED.
 * - Checklist: Fresh checklist and checklist item UUIDs generated; all items reset to isCompleted = false.
 * - Recurrence: Preserved from source ([Event.recurrenceRule]).
 * - Tags & Groups: Preserved from source ([Event.tagIds], [Event.groupIds]).
 * - Custom Metadata: Preserved from source ([Event.customMetadata]).
 * - Attachments: Preserved from source ([Event.attachments]).
 * - Dependencies & Relationships: Intentionally OMITTED. A duplicate represents a distinct, unconstrained
 *   task/event that must not implicitly block or be blocked by the source event's dependency graph.
 * - Runtime State: Occurrence history, overrides, and completion records are OMITTED.
 */
class DuplicateEventUseCase(
    private val eventRepository: EventRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        sourceEventId: String,
        newTitle: String? = null,
        newStartDateTime: LocalDateTime? = null
    ): Result<Event> {
        if (sourceEventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Source event id cannot be blank"))
        }

        val source = eventRepository.getEvent(sourceEventId)
            ?: return Result.Failure(IllegalArgumentException("Source event with id $sourceEventId not found"))

        val now = timeProvider.nowInstant()
        val title = (newTitle?.takeIf { it.isNotBlank() } ?: "${source.title} (Copy)").trim()

        val temporal = if (newStartDateTime != null) {
            when (val original = source.temporal) {
                is EventDateTime.DateOnly -> {
                    EventDateTime.DateOnly(startDate = newStartDateTime.toLocalDate())
                }
                is EventDateTime.DateRange -> {
                    val days = ChronoUnit.DAYS.between(original.startDate, original.endDate)
                    EventDateTime.DateRange(
                        startDate = newStartDateTime.toLocalDate(),
                        endDate = newStartDateTime.toLocalDate().plusDays(days)
                    )
                }
                is EventDateTime.DateTime -> {
                    EventDateTime.DateTime(
                        dateTime = newStartDateTime,
                        zoneId = original.zoneId
                    )
                }
                is EventDateTime.DateTimeRange -> {
                    val duration = Duration.between(original.startDateTime, original.endDateTime)
                    EventDateTime.DateTimeRange(
                        startDateTime = newStartDateTime,
                        endDateTime = newStartDateTime.plus(duration),
                        zoneId = original.zoneId
                    )
                }
            }
        } else {
            source.temporal
        }

        val freshSchedules = source.notificationSchedules.map { schedule ->
            schedule.copy(id = UUID.randomUUID().toString())
        }

        val freshChecklist = source.checklist?.let { cl ->
            Checklist(
                id = UUID.randomUUID().toString(),
                title = cl.title,
                items = cl.items.map { item ->
                    item.copy(id = UUID.randomUUID().toString(), isCompleted = false)
                }
            )
        }

        val duplicated = source.copy(
            id = UUID.randomUUID().toString(),
            title = title,
            temporal = temporal,
            notificationSchedules = freshSchedules,
            checklist = freshChecklist,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            isFavorite = false,
            deletedAt = null,
            createdAt = now,
            updatedAt = now
        )

        return when (val saveResult = eventRepository.saveEvent(duplicated)) {
            is Result.Success -> Result.Success(duplicated)
            is Result.Failure -> saveResult
        }
    }
}
