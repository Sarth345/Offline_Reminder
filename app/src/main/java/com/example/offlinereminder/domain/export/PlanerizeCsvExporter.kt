package com.example.offlinereminder.domain.export

import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime

object PlanerizeCsvExporter {

    private val HEADERS = listOf(
        "ID",
        "Title",
        "Description",
        "Category",
        "EventType",
        "Priority",
        "TemporalType",
        "StartDate",
        "StartTime",
        "EndDate",
        "EndTime",
        "Timezone",
        "Recurrence",
        "LifecycleStatus",
        "IsFavorite",
        "Location",
        "ChecklistProgress"
    )

    fun export(events: List<Event>): String {
        val sb = StringBuilder()
        sb.append(HEADERS.joinToString(",")).append("\r\n")

        for (event in events) {
            val (tempType, sDate, sTime, eDate, eTime, zone) = when (val t = event.temporal) {
                is EventDateTime.DateOnly -> Tuple6("DATE_ONLY", t.startDate.toString(), "", "", "", "")
                is EventDateTime.DateTime -> Tuple6("DATE_TIME", t.dateTime.toLocalDate().toString(), t.dateTime.toLocalTime().toString(), "", "", t.zoneId.id)
                is EventDateTime.DateRange -> Tuple6("DATE_RANGE", t.startDate.toString(), "", t.endDate.toString(), "", "")
                is EventDateTime.DateTimeRange -> Tuple6("DATE_TIME_RANGE", t.startDateTime.toLocalDate().toString(), t.startDateTime.toLocalTime().toString(), t.endDateTime.toLocalDate().toString(), t.endDateTime.toLocalTime().toString(), t.zoneId.id)
            }

            val locStr = event.location?.let {
                listOfNotNull(it.placeName.ifBlank { null }, it.addressText.ifBlank { null }).joinToString(" - ")
            }.orEmpty()

            val checklistProgress = event.checklist?.let {
                "${it.items.count { item -> item.isCompleted }}/${it.items.size}"
            }.orEmpty()

            val row = listOf(
                escapeCsv(event.id),
                escapeCsv(event.title),
                escapeCsv(event.description),
                escapeCsv(event.categoryId),
                escapeCsv(event.eventTypeId),
                escapeCsv(event.priority.name),
                escapeCsv(tempType),
                escapeCsv(sDate),
                escapeCsv(sTime),
                escapeCsv(eDate),
                escapeCsv(eTime),
                escapeCsv(zone),
                escapeCsv(event.recurrenceRule?.frequency?.name.orEmpty()),
                escapeCsv(event.lifecycleStatus.name),
                escapeCsv(event.isFavorite.toString()),
                escapeCsv(locStr),
                escapeCsv(checklistProgress)
            )

            sb.append(row.joinToString(",")).append("\r\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val containsSpecial = value.contains(',') ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')

        return if (containsSpecial) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private data class Tuple6(
        val a: String,
        val b: String,
        val c: String,
        val d: String,
        val e: String,
        val f: String
    )
}
