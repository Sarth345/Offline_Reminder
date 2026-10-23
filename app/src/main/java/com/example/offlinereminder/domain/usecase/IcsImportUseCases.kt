package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.ics.IcsCalendarPreview
import com.example.offlinereminder.domain.ics.IcsOccurrenceException
import com.example.offlinereminder.domain.ics.IcsParser
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Parses an iCalendar (.ics) string and generates a validation preview
 * without mutating database records.
 */
class PreviewIcsImportUseCase(
    private val categoryRepository: CategoryRepository,
    private val eventTypeRepository: EventTypeRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(icsContent: String): Result<IcsCalendarPreview> = Result.runCatching {
        val defaultCategory = categoryRepository.getAllCategories().first().firstOrNull()?.id ?: "default"
        val defaultEventType = eventTypeRepository.getAllEventTypes().first().firstOrNull()?.id ?: "default"

        IcsParser.parse(
            icsContent = icsContent,
            defaultCategoryId = defaultCategory,
            defaultEventTypeId = defaultEventType,
            nowInstant = timeProvider.nowInstant()
        )
    }
}

/**
 * Persists the confirmed candidate events and occurrence exceptions from an ICS preview
 * and registers their notification instances.
 */
class ConfirmIcsImportUseCase(
    private val eventRepository: EventRepository,
    private val scheduleNotificationsUseCase: ScheduleNotificationsForEventUseCase,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository? = null
) {
    suspend operator fun invoke(
        events: List<Event>,
        exceptions: List<IcsOccurrenceException> = emptyList()
    ): Result<Int> = Result.runCatching {
        var count = 0
        for (event in events) {
            val saveResult = eventRepository.saveEvent(event)
            if (saveResult is Result.Success) {
                scheduleNotificationsUseCase(event)
                count++
            }
        }

        if (occurrenceExceptionRepository != null && exceptions.isNotEmpty()) {
            for (ex in exceptions) {
                val matchedEvent = events.firstOrNull { it.id == ex.masterUid }
                    ?: eventRepository.getEvent(ex.masterUid)
                if (matchedEvent != null && ex.newTemporal != null) {
                    val newLdt = when (val t = ex.newTemporal) {
                        is EventDateTime.DateTime -> t.dateTime
                        is EventDateTime.DateTimeRange -> t.startDateTime
                        is EventDateTime.DateOnly -> t.startDate.atTime(9, 0)
                        is EventDateTime.DateRange -> t.startDate.atTime(9, 0)
                        null -> null
                    } ?: continue
                    val originalStartLdt = when (val t = matchedEvent.temporal) {
                        is EventDateTime.DateTime -> ex.recurrenceOriginalDate.atTime(t.dateTime.toLocalTime())
                        is EventDateTime.DateTimeRange -> ex.recurrenceOriginalDate.atTime(t.startDateTime.toLocalTime())
                        else -> ex.recurrenceOriginalDate.atTime(9, 0)
                    }
                    val originalZone = when (val t = matchedEvent.temporal) {
                        is EventDateTime.DateTime -> t.zoneId
                        is EventDateTime.DateTimeRange -> t.zoneId
                        else -> ZoneId.systemDefault()
                    }
                    val exceptionEntity = OccurrenceException(
                        id = OccurrenceException.createId(matchedEvent.id, originalStartLdt, originalZone),
                        eventId = matchedEvent.id,
                        originalStartDateTime = originalStartLdt,
                        originalZoneId = originalZone,
                        status = OccurrenceStatus.RESCHEDULED,
                        rescheduledStartDateTime = newLdt,
                        titleOverride = ex.title
                    )
                    occurrenceExceptionRepository.saveException(exceptionEntity)
                }
            }
        }
        count
    }
}
