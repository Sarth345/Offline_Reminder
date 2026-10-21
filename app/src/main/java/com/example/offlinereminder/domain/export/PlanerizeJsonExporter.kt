package com.example.offlinereminder.domain.export

import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class StandaloneEventExportDto(
    val id: String,
    val title: String,
    val description: String = "",
    val categoryId: String,
    val eventTypeId: String,
    val priority: String,
    val lifecycleStatus: String,
    val isFavorite: Boolean,
    val temporalType: String,
    val startDate: String,
    val startTime: String? = null,
    val endDate: String? = null,
    val endTime: String? = null,
    val timezone: String? = null,
    val recurrenceSummary: String? = null,
    val location: String? = null,
    val checklistItemCount: Int = 0,
    val checklistCompletedCount: Int = 0,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class StandaloneExportPayload(
    val exportVersion: Int = 1,
    val appName: String = "Planerize",
    val appVersion: String = "1.1.0",
    val exportedAt: String,
    val totalEvents: Int,
    val events: List<StandaloneEventExportDto>
)

object PlanerizeJsonExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun export(events: List<Event>, exportedAt: Instant): String {
        val dtos = events.map { event ->
            val (tempType, sDate, sTime, eDate, eTime, zone) = when (val t = event.temporal) {
                is EventDateTime.DateOnly -> Tuple6("DATE_ONLY", t.startDate.toString(), null, null, null, null)
                is EventDateTime.DateTime -> Tuple6("DATE_TIME", t.dateTime.toLocalDate().toString(), t.dateTime.toLocalTime().toString(), null, null, t.zoneId.id)
                is EventDateTime.DateRange -> Tuple6("DATE_RANGE", t.startDate.toString(), null, t.endDate.toString(), null, null)
                is EventDateTime.DateTimeRange -> Tuple6("DATE_TIME_RANGE", t.startDateTime.toLocalDate().toString(), t.startDateTime.toLocalTime().toString(), t.endDateTime.toLocalDate().toString(), t.endDateTime.toLocalTime().toString(), t.zoneId.id)
            }

            val locStr = event.location?.let {
                listOfNotNull(it.placeName.ifBlank { null }, it.addressText.ifBlank { null }).joinToString(", ")
            }

            StandaloneEventExportDto(
                id = event.id,
                title = event.title,
                description = event.description,
                categoryId = event.categoryId,
                eventTypeId = event.eventTypeId,
                priority = event.priority.name,
                lifecycleStatus = event.lifecycleStatus.name,
                isFavorite = event.isFavorite,
                temporalType = tempType,
                startDate = sDate,
                startTime = sTime,
                endDate = eDate,
                endTime = eTime,
                timezone = zone,
                recurrenceSummary = event.recurrenceRule?.frequency?.name,
                location = locStr,
                checklistItemCount = event.checklist?.items?.size ?: 0,
                checklistCompletedCount = event.checklist?.items?.count { it.isCompleted } ?: 0,
                createdAt = event.createdAt.toString(),
                updatedAt = event.updatedAt.toString()
            )
        }

        val payload = StandaloneExportPayload(
            exportVersion = 1,
            appName = "Planerize",
            appVersion = "1.1.0",
            exportedAt = exportedAt.toString(),
            totalEvents = dtos.size,
            events = dtos
        )

        return json.encodeToString(StandaloneExportPayload.serializer(), payload)
    }

    private data class Tuple6(
        val a: String,
        val b: String,
        val c: String?,
        val d: String?,
        val e: String?,
        val f: String?
    )
}
