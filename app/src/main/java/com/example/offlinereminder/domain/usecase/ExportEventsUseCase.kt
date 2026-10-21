package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.export.PlanerizeCsvExporter
import com.example.offlinereminder.domain.export.PlanerizeIcsExporter
import com.example.offlinereminder.domain.export.PlanerizeJsonExporter
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.first

enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    ICS("ics", "text/calendar")
}

/**
 * Domain use case for exporting user schedule data into JSON, CSV, or ICS format.
 * Zero network, local file export only.
 */
class ExportEventsUseCase(
    private val eventRepository: EventRepository,
    private val timeProvider: TimeProvider,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository? = null
) {
    suspend operator fun invoke(
        format: ExportFormat,
        includeArchived: Boolean = false
    ): Result<String> = Result.runCatching {
        val allEvents = eventRepository.getAllEvents().first()
        val filtered = allEvents.filter {
            if (includeArchived) {
                it.lifecycleStatus != EventLifecycleStatus.TRASHED
            } else {
                it.lifecycleStatus == EventLifecycleStatus.ACTIVE
            }
        }

        when (format) {
            ExportFormat.JSON -> PlanerizeJsonExporter.export(filtered, timeProvider.nowInstant())
            ExportFormat.CSV -> PlanerizeCsvExporter.export(filtered)
            ExportFormat.ICS -> {
                val exceptions = occurrenceExceptionRepository?.getAllExceptions() ?: emptyList()
                PlanerizeIcsExporter.export(filtered, exceptions, timeProvider.nowInstant())
            }
        }
    }
}
